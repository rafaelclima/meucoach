package com.meucoach.app.ui.activitydetail

import com.meucoach.core.data.repository.StoredActivity
import com.meucoach.core.data.repository.StoredAlternate
import com.meucoach.core.model.ActivitySource
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
