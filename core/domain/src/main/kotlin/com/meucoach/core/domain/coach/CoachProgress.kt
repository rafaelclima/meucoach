package com.meucoach.core.domain.coach

import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewStatus
import kotlin.math.roundToInt

/**
 * Four-week rollup for the Coach tab (PRD evolução, F4).
 *
 * Pure aggregation of closed [WeeklyReview]s assembled by the shared
 * assembler. Extra training counts for volume, never for adherence
 * (ADR 0050); weeks without data stay out of every average. It never
 * estimates, projects or promises: without an available week every share
 * is null.
 */
data class CoachProgressSummary(
    val availableWeeks: Int,
    val plannedSessions: Int,
    val doneSessions: Int,
    val adherencePercent: Int?,
    val plannedMinutes: Int,
    val executedMinutes: Int,
    val extraMinutes: Int,
    val volumePercent: Int?,
    val avgReadiness: Int?,
    val redDays: Int,
    val avgSleepMinutes: Int?,
    val shortNights: Int,
    val easyPercent28d: Int?,
)

object CoachProgress {
    fun summarize(weeks: List<WeeklyReview>, easyPercent28d: Int?): CoachProgressSummary {
        val available = weeks.filter { it.status == WeeklyReviewStatus.AVAILABLE }
        val plannedSessions = available.sumOf { it.adherence?.planned ?: 0 }
        val doneSessions = available.sumOf { it.adherence?.done ?: 0 }
        val plannedMinutes = available.sumOf { it.volume?.plannedMin ?: 0 }
        val executedMinutes = available.sumOf { it.volume?.executedMin ?: 0 }
        return CoachProgressSummary(
            availableWeeks = available.size,
            plannedSessions = plannedSessions,
            doneSessions = doneSessions,
            adherencePercent = if (plannedSessions > 0) {
                (doneSessions * 100.0 / plannedSessions).roundToInt()
            } else {
                null
            },
            plannedMinutes = plannedMinutes,
            executedMinutes = executedMinutes,
            extraMinutes = available.sumOf { it.volume?.extraMin ?: 0 },
            volumePercent = if (plannedMinutes > 0) {
                (executedMinutes * 100.0 / plannedMinutes).roundToInt()
            } else {
                null
            },
            avgReadiness = available.mapNotNull { it.readiness?.averageScore }.averageOrNull(),
            redDays = available.sumOf { it.readiness?.redDays ?: 0 },
            avgSleepMinutes = available.mapNotNull { it.sleep?.averageMinutes }.averageOrNull(),
            shortNights = available.sumOf { it.sleep?.shortNights ?: 0 },
            easyPercent28d = easyPercent28d,
        )
    }

    private fun List<Int>.averageOrNull(): Int? = takeIf { it.isNotEmpty() }?.average()?.roundToInt()
}
