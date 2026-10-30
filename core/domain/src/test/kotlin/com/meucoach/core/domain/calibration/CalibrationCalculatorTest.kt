package com.meucoach.core.domain.calibration

import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Sex
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test
import java.time.LocalDate

class CalibrationCalculatorTest {
    private val today = LocalDate.of(2026, 10, 30)

    @Test
    fun `computes vdot from a 5k performance`() {
        val vdot = CalibrationCalculator.vdot(distanceM = 5_000.0, durationSec = 1_859)

        assertThat(vdot).isNotNull
        assertThat(vdot!!).isCloseTo(29.6477, within(0.001))
    }

    @Test
    fun `derives threshold pace from vdot`() {
        assertThat(CalibrationCalculator.thresholdPaceForVdot(29.6477).secondsPerKm).isEqualTo(388)
    }

    @Test
    fun `calibrates a recent race as tier A`() {
        val result = CalibrationCalculator.calibrate(
            input(
                performance = CalibrationPerformance(
                    distanceM = 5_000.0,
                    durationSec = 1_859,
                    date = LocalDate.of(2026, 10, 24),
                    averageHeartRate = 181,
                ),
            ),
        )

        assertThat(result.tier).isEqualTo(CalibrationTier.A)
        assertThat(result.vdot).isCloseTo(29.6477, within(0.001))
        assertThat(result.thresholdPace?.secondsPerKm).isEqualTo(388)
        assertThat(result.lthr).isEqualTo(181)
        assertThat(result.confidence).isEqualTo(DataConfidence.HIGH)
        assertThat(result.ruleId).isEqualTo("R-CAL-01")
        assertThat(result.evidenceIds).containsExactly("EV-DANIELS-2014", "EV-FRIEL-2018")
        assertThat(result.warnings).contains(CalibrationWarning.MAX_HEART_RATE_INCONSISTENT)
    }

    @Test
    fun `does not flag max heart rate when it is consistent`() {
        val result = CalibrationCalculator.calibrate(
            input(
                profile = profile(maxHeartRateObserved = 200),
                performance = race(),
            ),
        )

        assertThat(result.warnings).doesNotContain(CalibrationWarning.MAX_HEART_RATE_INCONSISTENT)
    }

    @Test
    fun `skips lthr without effort heart rate`() {
        val result = CalibrationCalculator.calibrate(
            input(performance = race().copy(averageHeartRate = null)),
        )

        assertThat(result.lthr).isNull()
        assertThat(result.warnings).contains(CalibrationWarning.NO_EFFORT_HEART_RATE)
    }

    @Test
    fun `skips lthr for efforts outside the 25 to 35 minute window`() {
        val result = CalibrationCalculator.calibrate(
            input(
                performance = CalibrationPerformance(
                    distanceM = 5_000.0,
                    durationSec = 1_200,
                    date = LocalDate.of(2026, 10, 24),
                    averageHeartRate = 181,
                ),
            ),
        )

        assertThat(result.tier).isEqualTo(CalibrationTier.A)
        assertThat(result.lthr).isNull()
        assertThat(result.warnings).contains(CalibrationWarning.NO_EFFORT_HEART_RATE)
    }

    @Test
    fun `falls back to tier B with three recent activities and no performance`() {
        val result = CalibrationCalculator.calibrate(
            input(recentActivitiesWithHeartRateAndPace = 3),
        )

        assertThat(result.tier).isEqualTo(CalibrationTier.B)
        assertThat(result.vdot).isNull()
        assertThat(result.thresholdPace).isNull()
        assertThat(result.confidence).isEqualTo(DataConfidence.MEDIUM)
        assertThat(result.ruleId).isEqualTo("R-CAL-02")
        assertThat(result.warnings).contains(CalibrationWarning.NO_RECENT_PERFORMANCE)
    }

    @Test
    fun `falls back to tier C with fewer than three activities`() {
        val result = CalibrationCalculator.calibrate(
            input(recentActivitiesWithHeartRateAndPace = 2),
        )

        assertThat(result.tier).isEqualTo(CalibrationTier.C)
        assertThat(result.confidence).isEqualTo(DataConfidence.LOW)
    }

    @Test
    fun `ignores a stale performance but keeps the activity tier`() {
        val result = CalibrationCalculator.calibrate(
            input(
                performance = race().copy(date = LocalDate.of(2026, 8, 1)),
                recentActivitiesWithHeartRateAndPace = 4,
            ),
        )

        assertThat(result.tier).isEqualTo(CalibrationTier.B)
        assertThat(result.vdot).isNull()
        assertThat(result.warnings).contains(CalibrationWarning.NO_RECENT_PERFORMANCE)
    }

    @Test
    fun `rejects an implausible performance without inventing vdot`() {
        val result = CalibrationCalculator.calibrate(
            input(
                performance = race().copy(distanceM = 500.0),
            ),
        )

        assertThat(result.vdot).isNull()
        assertThat(result.tier).isEqualTo(CalibrationTier.C)
        assertThat(result.warnings).contains(CalibrationWarning.PERFORMANCE_DURATION_OUT_OF_RANGE)
        assertThat(result.warnings).doesNotContain(CalibrationWarning.NO_RECENT_PERFORMANCE)
    }

    @Test
    fun `treats a future performance as stale`() {
        val result = CalibrationCalculator.calibrate(
            input(performance = race().copy(date = today.plusDays(1))),
        )

        assertThat(result.vdot).isNull()
        assertThat(result.warnings).contains(CalibrationWarning.NO_RECENT_PERFORMANCE)
    }

    private fun input(
        profile: AthleteProfile = profile(),
        performance: CalibrationPerformance? = null,
        recentActivitiesWithHeartRateAndPace: Int = 0,
    ) = CalibrationInput(
        profile = profile,
        today = today,
        performance = performance,
        recentActivitiesWithHeartRateAndPace = recentActivitiesWithHeartRateAndPace,
    )

    private fun race() = CalibrationPerformance(
        distanceM = 5_000.0,
        durationSec = 1_859,
        date = LocalDate.of(2026, 10, 24),
        averageHeartRate = 181,
    )

    private fun profile(maxHeartRateObserved: Int = 187) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = maxHeartRateObserved,
        timezone = "America/Sao_Paulo",
    )
}
