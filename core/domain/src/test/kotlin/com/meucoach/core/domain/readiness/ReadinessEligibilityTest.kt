package com.meucoach.core.domain.readiness

import com.meucoach.core.model.ReadinessLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import kotlin.math.ln

/**
 * C4-28 — readiness eligibility for quality (ADR 0049 §2.1, FR-406).
 *
 * A missing check-in must not become a green light: the score can still be
 * shown with partial data, but quality stays blocked. The inverse incentive
 * (not answering scores better) disappears.
 */
class ReadinessEligibilityTest {
    private val goodSleep = SleepMetrics(durationMin = 450)
    private val goodHrv =
        HrvMetrics(
            rmssd7dMs = 60.0,
            baseline60dMs = 55.0,
            meanLog7d = ln(60.0),
            meanLog60d = ln(55.0),
            sdLnMs = 0.4,
            validNights = 7,
        )
    private val goodRestingHr = RestingHeartRateMetrics(average7dBpm = 56.0, baseline30dBpm = 58.0)

    @Test
    fun `without a check-in the objective score is green but quality is not eligible`() {
        val result = ReadinessCalculator.calculate(null, goodSleep, goodHrv, goodRestingHr)

        assertThat(result.score).isEqualTo(76)
        assertThat(result.level).isEqualTo(ReadinessLevel.GREEN)
        assertThat(result.flags).contains(ReadinessFlag.SUBJECTIVE_MISSING)
        assertThat(result.flags).doesNotContain(ReadinessFlag.NO_QUALITY)
        assertThat(result.qualityEligibleByReadiness).isFalse()
    }

    @Test
    fun `no data at all is amber missing subjective and not quality eligible`() {
        val result = ReadinessCalculator.calculate(null, null, null, null)

        assertThat(result.level).isEqualTo(ReadinessLevel.AMBER)
        assertThat(result.flags).contains(
            ReadinessFlag.INSUFFICIENT_DATA,
            ReadinessFlag.SUBJECTIVE_MISSING,
        )
        assertThat(result.qualityEligibleByReadiness).isFalse()
    }

    @Test
    fun `neutral check-in 3 3 3 3 is amber and blocks quality`() {
        val result = ReadinessCalculator.calculate(SubjectiveCheckIn(3, 3, 3, 3), null, null, null)

        assertThat(result.score).isEqualTo(50)
        assertThat(result.level).isEqualTo(ReadinessLevel.AMBER)
        assertThat(result.qualityEligibleByReadiness).isFalse()
    }

    @Test
    fun `check-in 4 2 2 2 is green and keeps quality eligible`() {
        val result = ReadinessCalculator.calculate(SubjectiveCheckIn(4, 2, 2, 2), null, null, null)

        assertThat(result.score).isEqualTo(75)
        assertThat(result.level).isEqualTo(ReadinessLevel.GREEN)
        assertThat(result.flags).doesNotContain(
            ReadinessFlag.SUBJECTIVE_MISSING,
            ReadinessFlag.NO_QUALITY,
        )
        assertThat(result.qualityEligibleByReadiness).isTrue()
    }

    @Test
    fun `check-in 5 1 1 1 is green and keeps quality eligible`() {
        val result = ReadinessCalculator.calculate(SubjectiveCheckIn(5, 1, 1, 1), null, null, null)

        assertThat(result.score).isEqualTo(100)
        assertThat(result.level).isEqualTo(ReadinessLevel.GREEN)
        assertThat(result.qualityEligibleByReadiness).isTrue()
    }

    @Test
    fun `valid short sleep blocks quality even with a excellent check-in`() {
        val result =
            ReadinessCalculator.calculate(
                checkIn = SubjectiveCheckIn(5, 1, 1, 1),
                sleep = SleepMetrics(durationMin = 359),
                hrv = null,
                restingHeartRate = null,
            )

        assertThat(result.level).isEqualTo(ReadinessLevel.AMBER)
        assertThat(result.flags).contains(ReadinessFlag.SHORT_SLEEP)
        assertThat(result.qualityEligibleByReadiness).isFalse()
    }

    @Test
    fun `illness blocks quality even when the check-in is excellent`() {
        val result =
            ReadinessCalculator.calculate(
                checkIn = SubjectiveCheckIn(5, 1, 1, 1),
                sleep = goodSleep,
                hrv = null,
                restingHeartRate = null,
                signals = ReadinessSignals(illnessOrInjury = true),
            )

        assertThat(result.level).isEqualTo(ReadinessLevel.RED)
        assertThat(result.flags).contains(ReadinessFlag.ILLNESS_OR_INJURY)
        assertThat(result.qualityEligibleByReadiness).isFalse()
    }

    @Test
    fun `elevated resting heart rate caps green and blocks quality`() {
        val result =
            ReadinessCalculator.calculate(
                checkIn = SubjectiveCheckIn(5, 1, 1, 1),
                sleep = goodSleep,
                hrv = null,
                restingHeartRate = goodRestingHr,
                signals = ReadinessSignals(restingHeartRateElevatedTwoDays = true),
            )

        assertThat(result.level).isEqualTo(ReadinessLevel.AMBER)
        assertThat(result.flags).contains(ReadinessFlag.RESTING_HEART_RATE_ELEVATED)
        assertThat(result.qualityEligibleByReadiness).isFalse()
    }
}
