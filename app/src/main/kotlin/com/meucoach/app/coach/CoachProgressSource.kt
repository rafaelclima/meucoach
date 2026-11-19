package com.meucoach.app.coach

import com.meucoach.app.application.PersistedTrainingSignals
import com.meucoach.app.application.WeeklyReviewAssembler
import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.PlanCompletionCalculator
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.trends.IntensityDistributionCalculator
import com.meucoach.core.domain.trends.IntensitySession
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.Sport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/**
 * Last four closed weeks for the Coach tab (PRD evolução, F4).
 *
 * Read-only aggregation through the shared [WeeklyReviewAssembler], so the
 * progress card can never disagree with Tendências about the same week.
 * Intensity is computed once over the last 28 days (like Tendências) instead
 * of per week; per-week intensity stays out of the rollup. Activities,
 * check-ins, executions, plans and races are observed, so a new import,
 * completion or replan re-emits the four weeks instead of keeping stale data.
 */
data class CoachProgressContext(
    /** Oldest first; weeks without data/plan surface as `EMPTY_NO_DATA`. */
    val weeks: List<WeeklyReview>,
    /** Share of easy time over the last 28 days, or null without LTHR/samples. */
    val easyPercent28d: Int?,
    val intensitySessions28d: Int,
    val profile: AthleteProfile?,
    val today: LocalDate,
)

interface CoachProgressSource {
    fun observeProgress(): Flow<CoachProgressContext>
}

class RoomCoachProgressSource @Inject constructor(
    private val profiles: ProfileRepository,
    private val plans: TrainingPlanRepository,
    private val activities: ActivityRepository,
    private val raceEvents: RaceEventRepository,
    private val assembler: WeeklyReviewAssembler,
    private val coachSignals: CoachSignalsSource,
    private val time: TimeProvider,
) : CoachProgressSource {
    private val completionCalculator = PlanCompletionCalculator()
    private val intensityCalculator = IntensityDistributionCalculator()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeProgress(): Flow<CoachProgressContext> = time.today
        .map { today -> today to closedStarts(today) }
        .distinctUntilChanged()
        .flatMapLatest { (today, starts) ->
            val weekStarts = starts + today.weekStart()
            combine(
                profiles.observeProfile(),
                coachSignals.observeSignals(),
                raceEvents.observeAll(),
                observeWeeks(weekStarts),
            ) { profile, persisted, races, weeklyPlans ->
                load(today, profile, races, starts, weeklyPlans, persisted)
            }
        }

    private fun observeWeeks(weekStarts: List<LocalDate>): Flow<List<WeeklyPlan?>> =
        combine(weekStarts.map(plans::observeWeek)) { plans -> plans.toList() }

    private suspend fun load(
        today: LocalDate,
        profile: AthleteProfile?,
        races: List<RaceEvent>,
        starts: List<LocalDate>,
        weeklyPlans: List<WeeklyPlan?>,
        persisted: PersistedTrainingSignals,
    ): CoachProgressContext {
        if (profile == null) return CoachProgressContext(emptyList(), null, 0, null, today)
        val zone = runCatching { ZoneId.of(profile.timezone) }.getOrDefault(ZoneId.systemDefault())
        val summaries = persisted.activities
        val excluded = races
            .filter { it.targetDate in starts.first()..starts.last().plusDays(6) }
            .map { it.targetDate }
            .toSet()
        val completion = completionCalculator.calculate(
            plans = weeklyPlans.filterNotNull(),
            today = today,
            weeks = starts.size + 1,
            excludedDates = excluded,
        )
        val weeks = starts.map { weekStart ->
            assembler.assemble(
                weekStart = weekStart,
                today = today,
                summaries = summaries,
                zone = zone,
                completionWeek = completion.weeks.firstOrNull { it.weekStart == weekStart },
                intensity = null,
                intensitySessions = emptyList(),
            )
        }
        val sessions = intensitySessions(summaries, today, zone)
        val intensity = profile.lactateThresholdHeartRate?.let { lthr ->
            intensityCalculator.calculate(
                sessions = sessions,
                today = today,
                lthrBpm = lthr,
                windowDays = INTENSITY_WINDOW_DAYS,
            )
        }
        return CoachProgressContext(
            weeks = weeks,
            easyPercent28d = intensity?.let { Math.round(it.easyPercent()).toInt() },
            intensitySessions28d = sessions.size,
            profile = profile,
            today = today,
        )
    }

    private suspend fun intensitySessions(
        summaries: List<ActivitySummary>,
        today: LocalDate,
        zone: ZoneId,
    ): List<IntensitySession> {
        val start = today.minusDays(INTENSITY_WINDOW_DAYS - 1L)
        return summaries
            .filter { it.sport == Sport.RUNNING || it.sport == Sport.TREADMILL }
            .mapNotNull { summary ->
                val date = summary.startTime.atZone(zone).toLocalDate()
                if (date.isBefore(start) || date.isAfter(today)) return@mapNotNull null
                val samples = activities.openActivity(summary.id)?.imported?.samples.orEmpty()
                if (samples.isEmpty()) null else IntensitySession(date = date, samples = samples)
            }
    }

    private companion object {
        const val INTENSITY_WINDOW_DAYS = 28
    }
}

/** The progress rollup never judges a partial week: only closed Monday–Sunday weeks. */
internal fun closedStarts(today: LocalDate): List<LocalDate> {
    val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (1..4).map { currentMonday.minusWeeks(it.toLong()) }.reversed()
}
