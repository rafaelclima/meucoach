package com.meucoach.core.domain.readiness

import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ReadinessLevel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import kotlin.math.ln

class ReadinessCalculatorTest {
    private val goodCheckIn =
        SubjectiveCheckIn(
            sleepQuality = 5,
            fatigue = 1,
            soreness = 1,
            stress = 1,
        )
    private val goodSleep = SleepMetrics(durationMin = 480)
    private val goodHrv =
        HrvMetrics(
            rmssd7dMs = 60.0,
            baseline60dMs = 50.0,
            meanLog7d = ln(60.0),
            meanLog60d = ln(50.0),
            sdLnMs = 0.2,
            validNights = 6,
        )
    private val goodRestingHr =
        RestingHeartRateMetrics(
            average7dBpm = 55.0,
            baseline30dBpm = 58.0,
        )

    @Test
    fun `returns green with high confidence when all inputs are good`() {
        val result = ReadinessCalculator.calculate(goodCheckIn, goodSleep, goodHrv, goodRestingHr)

        assertThat(result.score).isEqualTo(90)
        assertThat(result.level).isEqualTo(ReadinessLevel.GREEN)
        assertThat(result.confidence).isEqualTo(DataConfidence.HIGH)
        assertThat(result.components).containsEntry(ReadinessComponent.SUBJECTIVE, 100)
        assertThat(result.components).containsEntry(ReadinessComponent.SLEEP, 100)
        assertThat(result.components).containsEntry(ReadinessComponent.HRV, 73)
        assertThat(result.components).containsEntry(ReadinessComponent.RESTING_HEART_RATE, 80)
        assertThat(result.missing).isEmpty()
        assertThat(result.flags).isEmpty()
    }

    @Test
    fun `renormalizes weights when objective inputs are missing`() {
        val result = ReadinessCalculator.calculate(goodCheckIn, goodSleep, hrv = null, restingHeartRate = null)

        assertThat(result.score).isEqualTo(100)
        assertThat(result.level).isEqualTo(ReadinessLevel.GREEN)
        assertThat(result.confidence).isEqualTo(DataConfidence.MEDIUM)
        assertThat(result.missing).containsExactlyInAnyOrder(
            ReadinessComponent.HRV,
            ReadinessComponent.RESTING_HEART_RATE,
        )
    }

    @Test
    fun `subjective only is green but low confidence`() {
        val result = ReadinessCalculator.calculate(goodCheckIn, sleep = null, hrv = null, restingHeartRate = null)

        assertThat(result.score).isEqualTo(100)
        assertThat(result.level).isEqualTo(ReadinessLevel.GREEN)
        assertThat(result.confidence).isEqualTo(DataConfidence.LOW)
        assertThat(result.missing).hasSize(3)
    }

    @Test
    fun `two objective inputs without check-in yield medium confidence`() {
        val result = ReadinessCalculator.calculate(null, goodSleep, goodHrv, restingHeartRate = null)

        assertThat(result.score).isEqualTo(87)
        assertThat(result.level).isEqualTo(ReadinessLevel.GREEN)
        assertThat(result.confidence).isEqualTo(DataConfidence.MEDIUM)
    }

    @Test
    fun `three objective inputs without check-in remain medium confidence`() {
        val result = ReadinessCalculator.calculate(null, goodSleep, goodHrv, goodRestingHr)

        assertThat(result.level).isEqualTo(ReadinessLevel.GREEN)
        assertThat(result.confidence).isEqualTo(DataConfidence.MEDIUM)
    }

    @Test
    fun `one objective input only yields low confidence`() {
        val result = ReadinessCalculator.calculate(null, goodSleep, hrv = null, restingHeartRate = null)

        assertThat(result.score).isEqualTo(100)
        assertThat(result.confidence).isEqualTo(DataConfidence.LOW)
    }

    @Test
    fun `short sleep caps green result at amber`() {
        val result =
            ReadinessCalculator.calculate(
                checkIn = goodCheckIn,
                sleep = SleepMetrics(durationMin = 359),
                hrv = null,
                restingHeartRate = null,
            )

        assertThat(result.score).isEqualTo(69)
        assertThat(result.level).isEqualTo(ReadinessLevel.AMBER)
        assertThat(result.flags).contains(ReadinessFlag.SHORT_SLEEP)
        assertThat(result.flags).contains(ReadinessFlag.NO_QUALITY)
    }

    @Test
    fun `short sleep keeps an already amber score`() {
        val result =
            ReadinessCalculator.calculate(
                checkIn = goodCheckIn,
                sleep = SleepMetrics(durationMin = 330),
                hrv = null,
                restingHeartRate = null,
            )

        assertThat(result.score).isEqualTo(67)
        assertThat(result.level).isEqualTo(ReadinessLevel.AMBER)
        assertThat(result.flags).contains(ReadinessFlag.SHORT_SLEEP)
    }

    @Test
    fun `illness or injury overrides readiness to red`() {
        val result =
            ReadinessCalculator.calculate(
                checkIn = goodCheckIn,
                sleep = goodSleep,
                hrv = goodHrv,
                restingHeartRate = goodRestingHr,
                signals = ReadinessSignals(illnessOrInjury = true),
            )

        assertThat(result.score).isEqualTo(29)
        assertThat(result.level).isEqualTo(ReadinessLevel.RED)
        assertThat(result.flags).contains(ReadinessFlag.ILLNESS_OR_INJURY)
        assertThat(result.flags).contains(ReadinessFlag.NO_QUALITY)
    }

    @Test
    fun `elevated resting heart rate caps green at amber`() {
        val result =
            ReadinessCalculator.calculate(
                checkIn = goodCheckIn,
                sleep = goodSleep,
                hrv = goodHrv,
                restingHeartRate = goodRestingHr,
                signals = ReadinessSignals(restingHeartRateElevatedTwoDays = true),
            )

        assertThat(result.score).isEqualTo(69)
        assertThat(result.level).isEqualTo(ReadinessLevel.AMBER)
        assertThat(result.flags).contains(ReadinessFlag.RESTING_HEART_RATE_ELEVATED)
    }

    @Test
    fun `no inputs returns neutral amber with low confidence`() {
        val result = ReadinessCalculator.calculate(null, null, null, null)

        assertThat(result.score).isEqualTo(50)
        assertThat(result.level).isEqualTo(ReadinessLevel.AMBER)
        assertThat(result.confidence).isEqualTo(DataConfidence.LOW)
        assertThat(result.components).isEmpty()
        assertThat(result.missing).hasSize(4)
        assertThat(result.flags).containsExactly(ReadinessFlag.INSUFFICIENT_DATA)
    }

    @Test
    fun `insufficient hrv nights are treated as missing`() {
        val result =
            ReadinessCalculator.calculate(
                checkIn = goodCheckIn,
                sleep = goodSleep,
                hrv = goodHrv.copy(validNights = 3),
                restingHeartRate = null,
            )

        assertThat(result.components).doesNotContainKey(ReadinessComponent.HRV)
        assertThat(result.missing).contains(ReadinessComponent.HRV)
    }

    @Test
    fun `poor inputs return red with high confidence`() {
        val result =
            ReadinessCalculator.calculate(
                checkIn = SubjectiveCheckIn(1, 5, 5, 5),
                sleep = SleepMetrics(300),
                hrv = hrv(10.0, 50.0, 0.2, 6),
                restingHeartRate = RestingHeartRateMetrics(70.0, 58.0),
            )

        assertThat(result.score).isEqualTo(0)
        assertThat(result.level).isEqualTo(ReadinessLevel.RED)
        assertThat(result.confidence).isEqualTo(DataConfidence.HIGH)
        assertThat(result.flags).contains(ReadinessFlag.NO_QUALITY)
    }

    @Test
    fun `score thresholds map to amber and red`() {
        val amberHigh = ReadinessCalculator.calculate(SubjectiveCheckIn(1, 1, 1, 2), null, null, null)
        val amberLow = ReadinessCalculator.calculate(SubjectiveCheckIn(1, 3, 3, 2), null, null, null)
        val red = ReadinessCalculator.calculate(SubjectiveCheckIn(1, 3, 3, 3), null, null, null)

        assertThat(amberHigh.score).isEqualTo(69)
        assertThat(amberHigh.level).isEqualTo(ReadinessLevel.AMBER)
        assertThat(amberLow.score).isEqualTo(44)
        assertThat(amberLow.level).isEqualTo(ReadinessLevel.AMBER)
        assertThat(red.score).isEqualTo(38)
        assertThat(red.level).isEqualTo(ReadinessLevel.RED)
    }

    @Test
    fun `sleep score covers all duration bands`() {
        fun scoreFor(minutes: Int): Int = ReadinessCalculator
            .calculate(null, SleepMetrics(minutes), null, null)
            .components
            .getValue(ReadinessComponent.SLEEP)

        assertThat(scoreFor(300)).isEqualTo(0)
        assertThat(scoreFor(330)).isEqualTo(20)
        assertThat(scoreFor(360)).isEqualTo(40)
        assertThat(scoreFor(390)).isEqualTo(70)
        assertThat(scoreFor(420)).isEqualTo(100)
        assertThat(scoreFor(540)).isEqualTo(100)
        assertThat(scoreFor(600)).isEqualTo(90)
    }

    @Test
    fun `hrv and resting heart rate scores clamp to zero and one hundred`() {
        val highHrv =
            ReadinessCalculator.calculate(
                null,
                null,
                hrv(200.0, 50.0, 0.2, 6),
                null,
            )
        val lowHrv =
            ReadinessCalculator.calculate(
                null,
                null,
                hrv(5.0, 50.0, 0.2, 6),
                null,
            )
        val lowRhr =
            ReadinessCalculator.calculate(
                null,
                null,
                null,
                RestingHeartRateMetrics(40.0, 58.0),
            )
        val highRhr =
            ReadinessCalculator.calculate(
                null,
                null,
                null,
                RestingHeartRateMetrics(70.0, 58.0),
            )

        assertThat(highHrv.components.getValue(ReadinessComponent.HRV)).isEqualTo(100)
        assertThat(lowHrv.components.getValue(ReadinessComponent.HRV)).isEqualTo(0)
        assertThat(lowRhr.components.getValue(ReadinessComponent.RESTING_HEART_RATE)).isEqualTo(100)
        assertThat(highRhr.components.getValue(ReadinessComponent.RESTING_HEART_RATE)).isEqualTo(0)
    }

    @Test
    fun `uses the log-space z-score instead of the raw ratio`() {
        val result =
            ReadinessCalculator.calculate(
                checkIn = null,
                sleep = null,
                hrv = HrvMetrics(
                    rmssd7dMs = 60.0,
                    baseline60dMs = 60.0,
                    meanLog7d = ln(60.0) + 0.5,
                    meanLog60d = ln(60.0),
                    sdLnMs = 0.5,
                    validNights = 7,
                ),
                restingHeartRate = null,
            )

        // z = 0.5 / 0.5 = 1.0. The old raw ratio (60/60) would score 50.
        assertThat(result.components.getValue(ReadinessComponent.HRV)).isEqualTo(75)
    }

    @Test
    fun `validates check-in and metric inputs`() {
        assertThatThrownBy { SubjectiveCheckIn(0, 1, 1, 1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { SubjectiveCheckIn(5, 6, 1, 1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { SleepMetrics(-1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { SleepMetrics(1_441) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { hrv(0.0, 50.0, 0.2, 6) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { hrv(60.0, 0.0, 0.2, 6) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { hrv(60.0, 50.0, 0.0, 6) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { hrv(60.0, 50.0, 0.2, -1) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { RestingHeartRateMetrics(0.0, 58.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { RestingHeartRateMetrics(55.0, 0.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun hrv(rmssd7dMs: Double, baseline60dMs: Double, sdLnMs: Double, validNights: Int) = HrvMetrics(
        rmssd7dMs = rmssd7dMs,
        baseline60dMs = baseline60dMs,
        meanLog7d = ln(rmssd7dMs),
        meanLog60d = ln(baseline60dMs),
        sdLnMs = sdLnMs,
        validNights = validNights,
    )
}
