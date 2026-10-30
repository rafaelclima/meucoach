package com.meucoach.app.ui.activitydetail

import com.meucoach.core.data.repository.StoredActivity
import com.meucoach.core.domain.insights.ActivityInsightsCalculator
import com.meucoach.core.domain.zones.HrZoneDistributionCalculator
import com.meucoach.core.model.ActivityLap
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import java.time.Instant
import java.time.LocalDate

internal object ActivityDetailTestData {
    fun state(): ActivityDetailUiState {
        val activity = activity()
        return ActivityDetailUiState(
            isLoading = false,
            activity = activity,
            insights = ActivityInsightsCalculator().calculate(activity.imported.laps),
            hrZones = HrZoneDistributionCalculator.calculate(activity.imported.samples, LTHR),
            lthr = LTHR,
        )
    }

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
