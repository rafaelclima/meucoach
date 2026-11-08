package com.meucoach.app.application

import com.meucoach.core.domain.load.ActivitySourceStatus
import com.meucoach.core.domain.load.LoadActivity
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.load.TrainingLoadEngine
import com.meucoach.core.domain.readiness.DailyMetricsReadinessBuilder
import com.meucoach.core.domain.readiness.ReadinessCalculator
import com.meucoach.core.domain.readiness.toSubjectiveCheckIn
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DailyMetrics
import java.time.LocalDate

/**
 * Builds the training load snapshot used by the plan, the prescription and the
 * replanner. It lives in the application layer so use cases never depend on a
 * Compose screen to read the same signals.
 */
internal fun buildLoadSnapshot(
    activities: List<ActivitySummary>,
    profile: AthleteProfile,
    today: LocalDate,
    sourceStatus: ActivitySourceStatus,
): LoadSnapshot {
    val zone = runCatching { java.time.ZoneId.of(profile.timezone) }.getOrNull()
        ?: java.time.ZoneId.systemDefault()
    return TrainingLoadEngine().calculate(
        activities = activities.map { summary ->
            LoadActivity(
                date = summary.startTime.atZone(zone).toLocalDate(),
                durationSec = summary.durationSec,
                averageHeartRate = summary.averageHeartRate,
                averagePace = summary.averagePace,
                startTime = summary.startTime,
            )
        },
        profile = profile,
        today = today,
        sourceStatus = sourceStatus,
    )
}

/** Readiness derived from the persisted check-in plus the available objective signals. */
internal fun readinessResult(
    checkIn: DailyCheckIn?,
    metrics: List<DailyMetrics>,
    today: LocalDate,
): com.meucoach.core.domain.readiness.ReadinessResult {
    val inputs = DailyMetricsReadinessBuilder.build(metrics, today)
    val signals = inputs.signals.copy(
        illnessOrInjury = inputs.signals.illnessOrInjury || checkIn?.illnessOrInjury == true,
    )
    return ReadinessCalculator.calculate(
        checkIn = checkIn?.toSubjectiveCheckIn(),
        sleep = inputs.sleep,
        hrv = inputs.hrv,
        restingHeartRate = inputs.restingHeartRate,
        signals = signals,
    )
}
