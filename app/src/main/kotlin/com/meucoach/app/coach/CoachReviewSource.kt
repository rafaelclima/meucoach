package com.meucoach.app.coach

import com.meucoach.app.application.PersistedTrainingSignals
import com.meucoach.app.application.WeeklyReviewAssembler
import com.meucoach.app.time.TimeProvider
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

interface CoachReviewSource {
    /** The last closed Monday–Sunday week, or null while there is no profile. */
    fun observeLastClosedWeek(): Flow<WeeklyReview?>
}

/**
 * Reconstructs the same closed week the Tendências tab shows, from persisted
 * data only (FR-011, PRD IA Coach 3.2). The Coach tab never re-decides training:
 * it consumes the shared [WeeklyReviewAssembler] output.
 *
 * The closed week itself, the current week (completion window), the shared
 * signals and the race events are observed: an import, a completion, a
 * check-in or a plan revision in the closed week re-emits the review.
 */
class RoomCoachReviewSource @Inject constructor(
    private val profiles: ProfileRepository,
    private val activities: ActivityRepository,
    private val plans: TrainingPlanRepository,
    private val raceEvents: RaceEventRepository,
    private val assembler: WeeklyReviewAssembler,
    private val coachSignals: CoachSignalsSource,
    private val time: TimeProvider,
) : CoachReviewSource {
    private val completionCalculator = PlanCompletionCalculator()
    private val intensityCalculator = IntensityDistributionCalculator()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeLastClosedWeek(): Flow<WeeklyReview?> = time.today
        .map { today -> today to lastClosedWeekStart(today) }
        .distinctUntilChanged { previous, current -> previous.second == current.second }
        .flatMapLatest { (today, weekStart) ->
            val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            combine(
                profiles.observeProfile(),
                plans.observeWeek(weekStart),
                plans.observeWeek(currentMonday),
                raceEvents.observeAll(),
                coachSignals.observeSignals(),
            ) { profile, closedPlan, currentPlan, races, persisted ->
                if (profile == null) {
                    null
                } else {
                    load(weekStart, today, profile, closedPlan, currentPlan, races, persisted)
                }
            }
        }

    private suspend fun load(
        weekStart: LocalDate,
        today: LocalDate,
        profile: AthleteProfile,
        closedPlan: WeeklyPlan?,
        currentPlan: WeeklyPlan?,
        races: List<RaceEvent>,
        persisted: PersistedTrainingSignals,
    ): WeeklyReview {
        val zone = runCatching { ZoneId.of(profile.timezone) }.getOrDefault(ZoneId.systemDefault())
        val summaries = persisted.activities
        val currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val excluded = races
            .filter { it.targetDate in weekStart..currentMonday.plusDays(6) }
            .map { it.targetDate }
            .toSet()
        val completionWeek = completionCalculator.calculate(
            plans = listOfNotNull(closedPlan, currentPlan),
            today = today,
            weeks = 2,
            excludedDates = excluded,
        ).weeks.first { it.weekStart == weekStart }
        val sessions = intensitySessions(summaries, today, zone)
        val intensity = profile.lactateThresholdHeartRate?.let { lthr ->
            intensityCalculator.calculate(
                sessions = sessions,
                today = today,
                lthrBpm = lthr,
                windowDays = INTENSITY_WINDOW_DAYS,
            )
        }
        return assembler.assemble(
            weekStart = weekStart,
            today = today,
            summaries = summaries,
            zone = zone,
            completionWeek = completionWeek,
            intensity = intensity,
            intensitySessions = sessions,
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

/** The review always covers the last closed week; a partial week is never judged. */
internal fun lastClosedWeekStart(today: LocalDate): LocalDate =
    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
