package com.meucoach.app.ui.activitydetail

import com.meucoach.core.data.repository.StoredActivity
import com.meucoach.core.data.repository.StoredAlternate
import com.meucoach.core.model.ActivityLap
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ActivityDetailFormattersTest {
    @Test
    fun `lists canonical source first and removes duplicate formats`() {
        val activity = storedActivity(
            source = ActivitySource.FIT,
            alternates = listOf(ActivitySource.TCX, ActivitySource.GPX, ActivitySource.TCX),
        )

        assertThat(activitySources(activity)).containsExactly(
            ActivitySource.FIT,
            ActivitySource.TCX,
            ActivitySource.GPX,
        )
    }

    @Test
    fun `maps available metrics and omits missing ones`() {
        val metrics = detailMetrics(importedActivity())

        assertThat(metrics).containsExactly(
            ActivityMetricUi("FC", "150/170", "bpm"),
            ActivityMetricUi("Cadência", "170", "spm"),
            ActivityMetricUi("Potência", "250", "W"),
            ActivityMetricUi("Elevação", "40", "m"),
            ActivityMetricUi("Calorias", "320", "kcal"),
            ActivityMetricUi("Movimento", "29:10"),
        )
    }

    @Test
    fun `returns no metrics when the file has only distance and time`() {
        val metrics = detailMetrics(
            importedActivity().copy(
                averageHeartRate = null,
                maxHeartRate = null,
                averageCadenceSpm = null,
                averagePowerW = null,
                elevationGainM = null,
                calories = null,
                movingTimeSec = null,
            ),
        )

        assertThat(metrics).isEmpty()
    }

    @Test
    fun `formats distance with brazilian separator`() {
        assertThat(formatDistanceMeters(5_106.0)).isEqualTo("5,11")
    }

    @Test
    fun `uses the lap pace from the file when present`() {
        val lap = lap(durationSec = 300, distanceM = 1_000.0)
            .copy(averagePace = Pace.fromSecondsPerKm(310))

        assertThat(lapPace(lap)).isEqualTo(Pace.fromSecondsPerKm(310))
    }

    @Test
    fun `derives lap pace from lap time and distance`() {
        val lap = lap(durationSec = 330, distanceM = 1_000.0)

        assertThat(lapPace(lap)).isEqualTo(Pace.fromSecondsPerKm(330))
    }

    @Test
    fun `returns null lap pace without valid distance or duration`() {
        assertThat(lapPace(lap(durationSec = 0, distanceM = 1_000.0))).isNull()
        assertThat(lapPace(lap(durationSec = 300, distanceM = 0.0))).isNull()
    }

    @Test
    fun `labels lap distance`() {
        assertThat(lapDistanceLabel(lap(durationSec = 300, distanceM = 1_000.0))).isEqualTo("1,00 km")
        assertThat(lapDistanceLabel(lap(durationSec = 300, distanceM = 600.0))).isEqualTo("0,60 km")
    }

    @Test
    fun `labels chart ranges with units`() {
        assertThat(paceRangeLabel(300..360)).isEqualTo("5:00–6:00/km")
        assertThat(heartRateRangeLabel(140..170)).isEqualTo("140–170 bpm")
    }

    @Test
    fun `labels confidence levels`() {
        assertThat(confidenceLabel(DataConfidence.HIGH)).isEqualTo("confiança alta")
        assertThat(confidenceLabel(DataConfidence.MEDIUM)).isEqualTo("confiança média")
        assertThat(confidenceLabel(DataConfidence.LOW)).isEqualTo("confiança baixa")
    }

    private fun lap(durationSec: Int, distanceM: Double) = ActivityLap(
        index = 0,
        durationSec = durationSec,
        distanceM = distanceM,
    )

    private fun storedActivity(source: ActivitySource, alternates: List<ActivitySource>) = StoredActivity(
        id = "activity-1",
        imported = importedActivity().copy(source = source),
        importedAt = Instant.parse("2026-10-30T12:00:00Z"),
        rawFilePath = null,
        sourceFileSha256 = null,
        zoneId = "America/Sao_Paulo",
        localDate = LocalDate.parse("2026-10-24"),
        alternates = alternates.map { alternate ->
            StoredAlternate(
                source = alternate,
                sourceFileSha256 = null,
                rawFilePath = null,
                parserVersion = "1.0.0",
                importedAt = Instant.parse("2026-10-30T12:00:00Z"),
                sampleCount = 10,
            )
        },
    )

    private fun importedActivity() = ImportedActivity(
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-10-24T08:17:10Z"),
        durationSec = 1_800,
        distanceM = 5_000.0,
        averageHeartRate = 150,
        maxHeartRate = 170,
        averagePace = Pace.fromSecondsPerKm(360),
        averageCadenceSpm = 170,
        averagePowerW = 250,
        elevationGainM = 40.0,
        calories = 320,
        movingTimeSec = 1_750,
        parserVersion = "1.0.0",
    )
}
