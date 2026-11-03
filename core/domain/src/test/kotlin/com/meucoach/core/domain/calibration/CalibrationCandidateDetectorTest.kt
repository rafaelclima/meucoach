package com.meucoach.core.domain.calibration

import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CalibrationCandidateDetectorTest {
    private val date = LocalDate.of(2026, 11, 1)
    private val today = date.plusDays(1)

    @Test
    fun `accepts a steady thirty minute run with heart rate`() {
        val candidate = CalibrationCandidateDetector.detect(activity(), date, today)

        assertThat(candidate).isNotNull
        assertThat(candidate?.date).isEqualTo(date)
        assertThat(candidate?.distanceM).isEqualTo(7_000.0)
        assertThat(candidate?.durationSec).isEqualTo(30 * 60)
        assertThat(candidate?.averageHeartRate).isEqualTo(176)
        assertThat(candidate?.heartRateCoverage).isEqualTo(1.0)
    }

    @Test
    fun `rejects runs outside the twenty five to thirty five minute window`() {
        assertThat(CalibrationCandidateDetector.detect(activity(durationSec = 24 * 60), date, today)).isNull()
        assertThat(CalibrationCandidateDetector.detect(activity(durationSec = 36 * 60), date, today)).isNull()
    }

    @Test
    fun `rejects runs without average heart rate`() {
        assertThat(CalibrationCandidateDetector.detect(activity(averageHeartRate = null), date, today)).isNull()
    }

    @Test
    fun `rejects walks and other sports`() {
        assertThat(CalibrationCandidateDetector.detect(activity(sport = Sport.WALKING), date, today)).isNull()
        assertThat(CalibrationCandidateDetector.detect(activity(sport = Sport.OTHER), date, today)).isNull()
    }

    @Test
    fun `rejects runs outside the eight week calibration window`() {
        assertThat(
            CalibrationCandidateDetector.detect(activity(), date, today = date.plusDays(57)),
        ).isNull()
        assertThat(
            CalibrationCandidateDetector.detect(activity(), date, today = date.minusDays(1)),
        ).isNull()
    }

    @Test
    fun `accepts treadmill efforts`() {
        assertThat(CalibrationCandidateDetector.detect(activity(sport = Sport.TREADMILL), date, today)).isNotNull()
    }

    @Test
    fun `rejects files with too few heart rate samples`() {
        assertThat(CalibrationCandidateDetector.detect(activity(heartRateSamples = 5), date, today)).isNull()
    }

    @Test
    fun `rejects files where most samples have no heart rate`() {
        assertThat(CalibrationCandidateDetector.detect(activity(missingHeartRateSamples = 30), date, today)).isNull()
    }

    @Test
    fun `rejects interval sessions with a wide heart rate band`() {
        assertThat(CalibrationCandidateDetector.detect(activity(variableHeartRate = true), date, today)).isNull()
    }

    @Test
    fun `rejects efforts with a long pause`() {
        assertThat(CalibrationCandidateDetector.detect(activity(pauseAtSample = 30), date, today)).isNull()
    }

    @Test
    fun `rejects short distances even inside the duration window`() {
        assertThat(CalibrationCandidateDetector.detect(activity(distanceM = 700.0), date, today)).isNull()
    }

    private fun activity(
        sport: Sport = Sport.RUNNING,
        durationSec: Int = 30 * 60,
        distanceM: Double = 7_000.0,
        averageHeartRate: Int? = 176,
        heartRateSamples: Int = 61,
        missingHeartRateSamples: Int = 0,
        variableHeartRate: Boolean = false,
        pauseAtSample: Int? = null,
    ): ImportedActivity {
        var offset = 0
        val samples = buildList {
            repeat(61) { index ->
                if (pauseAtSample == index) offset += 180
                val hasHeartRate = index < heartRateSamples && index >= missingHeartRateSamples
                val heartRate = when {
                    !hasHeartRate -> null
                    variableHeartRate -> if (index % 2 == 0) 150 else 190
                    else -> 176 + (if (index % 3 == 0) 1 else 0)
                }
                add(ActivitySample(offsetSec = offset, heartRate = heartRate))
                offset += 30
            }
        }
        return ImportedActivity(
            source = ActivitySource.TCX,
            sport = sport,
            startTime = Instant.parse("2026-11-01T10:00:00Z"),
            durationSec = durationSec,
            distanceM = distanceM,
            averageHeartRate = averageHeartRate,
            averagePace = Pace.fromSecondsPerKm(257),
            samples = samples,
        )
    }
}
