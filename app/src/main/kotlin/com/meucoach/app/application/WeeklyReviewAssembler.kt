package com.meucoach.app.application

import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.PlanCompletionWeek
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.review.QualityWeekSummary
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewCalculator
import com.meucoach.core.domain.trends.IntensityDistribution
import com.meucoach.core.domain.trends.IntensitySession
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.Sport
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Single application-layer assembly of one closed week (FR-011), shared by
 * Tendências and the Coach tab so both surfaces never disagree about the same
 * week. It only aggregates persisted facts: the calculator owns the rules and
 * this class never recalculates a target or consults an AI.
 */
class WeeklyReviewAssembler @Inject constructor(
    private val planRepository: TrainingPlanRepository,
    private val prescriptionRepository: DailyPrescriptionRepository,
    private val signalSource: TrainingSignalSource,
) {
    private val weeklyReviewCalculator = WeeklyReviewCalculator()

    suspend fun assemble(
        weekStart: LocalDate,
        today: LocalDate,
        summaries: List<ActivitySummary>,
        zone: ZoneId,
        completionWeek: PlanCompletionWeek?,
        intensity: IntensityDistribution?,
        intensitySessions: List<IntensitySession>,
    ): WeeklyReview {
        val weekEnd = weekStart.plusDays(6)
        val plan = planRepository.getWeek(weekStart)
        val checkIns = signalSource.allCheckIns()
        val metrics = signalSource.allMetrics()
        val readiness = signalSource.readinessHistory(today, REVIEW_HEALTH_WINDOW_DAYS)
        val weekSummaries = summaries
            .filter { it.sport == Sport.RUNNING || it.sport == Sport.TREADMILL }
            .filter { summary ->
                val date = summary.startTime.atZone(zone).toLocalDate()
                !date.isBefore(weekStart) && !date.isAfter(weekEnd)
            }
        val executedMin = weekSummaries
            .sumOf { it.durationSec }
            .let { seconds -> if (seconds <= 0) null else (seconds / 60.0).roundToInt().takeIf { it > 0 } }
        // Extra training (ADR 0050 §2.1): runs without a planned session behind
        // them count for volume, never for adherence. Same linkage the volume
        // projection uses: a summary counts as planned only when the plan links it.
        val linkedIds = plan?.sessions.orEmpty().mapNotNull { it.activityId }.toSet()
        val extraMin = weekSummaries
            .filter { it.id !in linkedIds }
            .sumOf { it.durationSec }
            .let { seconds -> if (seconds <= 0) null else (seconds / 60.0).roundToInt().takeIf { it > 0 } }
        val plannedMin = plan
            ?.let { week ->
                week.sessions
                    .filter { it.type != WorkoutType.REST && !it.date.isBefore(week.createdOn) }
                    .mapNotNull { it.durationMin }
                    .sum()
                    .takeIf { it > 0 }
            }
        val hasPlan = plan?.sessions.orEmpty().any { it.type != WorkoutType.REST }
        val intensityWeek = intensity?.weeks?.firstOrNull { it.weekStart == weekStart }
        val weekIntensitySessions = if (intensityWeek == null) {
            0
        } else {
            intensitySessions.count { it.date in weekStart..weekEnd }.coerceAtLeast(1)
        }

        return weeklyReviewCalculator.calculate(
            weekStart = weekStart,
            today = today,
            hasPlan = hasPlan,
            completion = completionWeek,
            plannedDurationMin = if (hasPlan) plannedMin else null,
            executedDurationMin = executedMin,
            extraDurationMin = extraMin,
            quality = qualitySummary(plan),
            intensity = intensityWeek,
            intensitySessions = weekIntensitySessions,
            readinessDays = readiness,
            metrics = metrics,
            checkIns = checkIns,
        )
    }

    /**
     * Day decisions for the planned quality opportunities (FR-401/FR-408).
     * The latest stored prescription of each quality date decides; without a
     * stored decision the week reports none instead of inventing one.
     */
    private suspend fun qualitySummary(plan: WeeklyPlan?) = plan
        ?.sessions.orEmpty()
        .filter { it.type == WorkoutType.QUALITY }
        .map { it.date }
        .sorted()
        .takeIf { it.isNotEmpty() }
        ?.let { qualityDates ->
            val latestByDate = qualityDates
                .mapNotNull { date ->
                    prescriptionRepository.getLatest(date)?.let { date to it }
                }
                .toMap()
            QualityWeekSummary.summarize(qualityDates, latestByDate).takeIf { it.hasDecision }
        }

    private companion object {
        /** Covers the last closed week (7–13 days back) with readiness/sleep. */
        const val REVIEW_HEALTH_WINDOW_DAYS = 14
    }
}
