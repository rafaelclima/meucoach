package com.meucoach.app.ui.activitydetail

import com.meucoach.core.data.repository.StoredActivity
import com.meucoach.core.domain.feedback.ComparisonPlan
import com.meucoach.core.domain.feedback.SessionComparison
import com.meucoach.core.domain.feedback.SessionComparisonCalculator
import com.meucoach.core.domain.insights.ActivityInsightsCalculator
import com.meucoach.core.domain.prescription.HeartRateTarget
import com.meucoach.core.domain.prescription.PaceTarget
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.zones.HrZoneDistributionCalculator
import com.meucoach.core.model.ActivityLap
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.Distance
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import java.time.Instant
import java.time.LocalDate

internal object ActivityDetailTestData {
    fun state(comparison: SessionComparison? = null): ActivityDetailUiState {
        val activity = activity()
        return ActivityDetailUiState(
            isLoading = false,
            activity = activity,
            insights = ActivityInsightsCalculator().calculate(activity.imported.laps),
            hrZones = HrZoneDistributionCalculator.calculate(activity.imported.samples, LTHR),
            lthr = LTHR,
            comparison = comparison,
        )
    }

    fun linkedComparison(): SessionComparison = SessionComparisonCalculator.compare(
        linkedPlan = ComparisonPlan(
            title = "Fácil 30 min",
            type = WorkoutType.EASY,
            durationMin = 30,
            targets = PrescriptionTargets(
                pace = PaceTarget(fast = Pace.fromSecondsPerKm(350), slow = Pace.fromSecondsPerKm(390)),
                heartRate = HeartRateTarget(140, 155),
                zone = "Z2",
                distanceKm = 5.5,
            ),
        ),
        activity = summary(),
        rpe = 5,
    )

    fun freeRunComparison(): SessionComparison = SessionComparisonCalculator.compare(
        linkedPlan = null,
        activity = summary(),
        rpe = null,
    )

    private fun summary() = ActivitySummary(
        id = "activity-1",
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-10-24T08:17:10Z"),
        durationSec = 1_800,
        distance = Distance(5_000.0),
        averageHeartRate = 150,
        maxHeartRate = 170,
        averagePace = Pace.fromSecondsPerKm(360),
    )

    fun activity(): StoredActivity {
        val start = Instant.parse("2026-10-24T08:17:10Z")
        val imported = ImportedActivity(
            source = ActivitySource.FIT,
            sport = Sport.RUNNING,
            startTime = start,
            durationSec = 1_800,
            distanceM = 5_000.0,
            averageHeartRate = 150,
            maxHeartRate = 170,
            averagePace = Pace.fromSecondsPerKm(360),
            averageCadenceSpm = 170,
            averagePowerW = 250,
            elevationGainM = 40.0,
            laps = listOf(
                ActivityLap(index = 0, durationSec = 900, distanceM = 2_500.0, averageHeartRate = 148),
                ActivityLap(index = 1, durationSec = 900, distanceM = 2_500.0, averageHeartRate = 152),
            ),
            samples = listOf(
                ActivitySample(offsetSec = 0, speedMps = 2.7, heartRate = 140, latitude = -5.79, longitude = -35.21),
                ActivitySample(offsetSec = 900, speedMps = 2.8, heartRate = 150, latitude = -5.78, longitude = -35.20),
                ActivitySample(
                    offsetSec = 1_800,
                    speedMps = 2.9,
                    heartRate = 160,
                    latitude = -5.77,
                    longitude = -35.19,
                ),
            ),
            parserVersion = "1.0.0",
        )
        return StoredActivity(
            id = "activity-1",
            imported = imported,
            importedAt = Instant.parse("2026-10-30T12:00:00Z"),
            rawFilePath = null,
            sourceFileSha256 = null,
            zoneId = "America/Sao_Paulo",
            localDate = LocalDate.parse("2026-10-24"),
        )
    }

    private const val LTHR = 180
}
