package com.meucoach.app.ui.today

import com.meucoach.app.application.buildLoadSnapshot
import com.meucoach.app.time.weekStart
import com.meucoach.app.ui.activities.formatDateTime
import com.meucoach.app.ui.activities.formatDistance
import com.meucoach.app.ui.activities.formatDuration
import com.meucoach.app.ui.activities.paceLabel
import com.meucoach.app.ui.activities.sportLabel
import com.meucoach.core.domain.load.ActivitySourceStatus
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.SessionExecution
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class ContextInputs(
    val checkIn: DailyCheckIn?,
    val metrics: List<DailyMetrics>,
    val profile: AthleteProfile?,
    val activities: List<ActivitySummary>,
    val lastImportedAt: Instant?,
)

internal data class TodayContext(
    val checkIn: DailyCheckIn?,
    val metrics: List<DailyMetrics>,
    val profile: AthleteProfile?,
    val load: LoadSnapshot?,
    val today: LocalDate,
    val weeklyLoadKm: Double,
    val lastActivityImportAt: Instant?,
    val kmByDay: Map<LocalDate, Double>,
    val recentActivity: RecentActivityUi?,
    val activities: List<ActivitySummary>,
    val linkedActivityIds: Set<String>,
)

/** Builds the immutable day context from one reactive emission of the repositories. */
internal fun buildTodayContext(
    inputs: ContextInputs,
    linkedActivityIds: Set<String>,
    executions: List<SessionExecution>,
    today: LocalDate,
    checkedAt: Instant,
    zone: ZoneId,
): TodayContext {
    val checkIn = inputs.checkIn
    val metrics = inputs.metrics
    val profile = inputs.profile
    val activities = inputs.activities
    val lastImportedAt = inputs.lastImportedAt
    val load = profile?.let {
        buildLoadSnapshot(
            activities = activities,
            profile = it,
            today = today,
            sourceStatus = ActivitySourceStatus(
                lastImportedAt = lastImportedAt,
                checkedAt = checkedAt,
                firstActivityAt = activities.minOfOrNull { summary -> summary.startTime },
            ),
            executions = executions,
        )
    }
    val weekStart = today.weekStart()
    val weekKm = activities
        .filter {
            val date = it.startTime.atZone(zone).toLocalDate()
            !date.isBefore(weekStart) && !date.isAfter(weekStart.plusDays(6))
        }
        .sumOf { it.distance.kilometers }
    val kmByDay = activities.groupBy { it.startTime.atZone(zone).toLocalDate() }
        .mapValues { (_, rows) -> rows.sumOf { it.distance.kilometers } }
    val recent = activities.maxByOrNull { it.startTime }?.let { summary ->
        RecentActivityUi(
            id = summary.id,
            title = sportLabel(summary.sport),
            subtitle = formatDateTime(summary.startTime, zone),
            distance = "${formatDistance(summary.distance)} km",
            duration = formatDuration(summary.durationSec),
            pace = summary.averagePace?.let { "${paceLabel(it)} /km" } ?: "—",
            zone = "—",
        )
    }
    return TodayContext(
        checkIn = checkIn,
        metrics = metrics,
        profile = profile,
        load = load,
        today = today,
        weeklyLoadKm = weekKm,
        lastActivityImportAt = lastImportedAt,
        kmByDay = kmByDay,
        recentActivity = recent,
        activities = activities,
        linkedActivityIds = linkedActivityIds,
    )
}
