package com.meucoach.core.domain.load

import com.meucoach.core.model.Sex
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.Test

class TrainingLoadCalculatorTest {
    @Test
    fun `calculates trimp for male athlete`() {
        // Banister's formula uses minutes, so 30 min stays in the same 0-100 scale as rTSS.
        val result =
            TrainingLoadCalculator.trimp(
                durationSec = 1_800,
                averageHeartRateBpm = 150,
                restingHeartRateBpm = 58,
                maxHeartRateBpm = 187,
                sex = Sex.MALE,
            )

        assertThat(result).isCloseTo(53.849_266_228_749_8, within(1e-9))
    }

    @Test
    fun `calculates trimp for race effort`() {
        val result =
            TrainingLoadCalculator.trimp(
                durationSec = 1_860,
                averageHeartRateBpm = 181,
                restingHeartRateBpm = 58,
                maxHeartRateBpm = 187,
                sex = Sex.MALE,
            )

        assertThat(result).isCloseTo(118.010_042_560_127_66, within(1e-9))
    }

    @Test
    fun `uses sex specific coefficients`() {
        val female = TrainingLoadCalculator.trimp(1_800, 150, 58, 187, Sex.FEMALE)
        val neutral = TrainingLoadCalculator.trimp(1_800, 150, 58, 187, Sex.UNSPECIFIED)

        assertThat(female).isCloseTo(60.543_276_087_197_74, within(1e-9))
        assertThat(neutral).isCloseTo(57.928_669_190_657_42, within(1e-9))
    }

    @Test
    fun `rejects invalid trimp inputs`() {
        assertThatThrownBy { TrainingLoadCalculator.trimp(0, 150, 58, 187, Sex.MALE) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { TrainingLoadCalculator.trimp(1_800, 150, 187, 187, Sex.MALE) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { TrainingLoadCalculator.trimp(1_800, 50, 58, 187, Sex.MALE) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { TrainingLoadCalculator.trimp(1_800, 200, 58, 187, Sex.MALE) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `calculates rtss at and below threshold`() {
        assertThat(TrainingLoadCalculator.rTss(3_600, 364, 364)).isEqualTo(100.0)
        assertThat(TrainingLoadCalculator.rTss(1_800, 364, 364)).isEqualTo(50.0)
        assertThat(TrainingLoadCalculator.rTss(3_600, 400, 364)).isCloseTo(82.81, within(1e-9))
    }

    @Test
    fun `rejects invalid rtss inputs`() {
        assertThatThrownBy { TrainingLoadCalculator.rTss(0, 364, 364) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { TrainingLoadCalculator.rTss(3_600, 0, 364) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { TrainingLoadCalculator.rTss(3_600, 364, 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `calculates srpe`() {
        assertThat(TrainingLoadCalculator.srpe(7, 45)).isEqualTo(315.0)
        assertThatThrownBy { TrainingLoadCalculator.srpe(0, 45) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { TrainingLoadCalculator.srpe(11, 45) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { TrainingLoadCalculator.srpe(5, 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `ewma starts at zero and converges to constant load`() {
        assertThat(TrainingLoadCalculator.ewma(emptyList(), 7)).isEmpty()
        assertThat(TrainingLoadCalculator.ewma(listOf(100.0), 7).single())
            .isCloseTo(100.0 / 7.0, within(1e-12))

        val converged = TrainingLoadCalculator.ewma(List(1_000) { 100.0 }, 7).last()
        assertThat(converged).isCloseTo(100.0, within(1e-9))
    }

    @Test
    fun `ewma rejects invalid inputs`() {
        assertThatThrownBy { TrainingLoadCalculator.ewma(listOf(10.0), 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { TrainingLoadCalculator.ewma(listOf(-1.0), 7) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { TrainingLoadCalculator.ewma(listOf(Double.NaN), 7) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `calculates ctl atl and tsb`() {
        val loads = List(10) { 100.0 }
        val ctl = TrainingLoadCalculator.ctl(loads).last()
        val atl = TrainingLoadCalculator.atl(loads).last()

        assertThat(ctl).isLessThan(atl)
        assertThat(TrainingLoadCalculator.tsb(ctl, atl)).isEqualTo(ctl - atl)
    }

    @Test
    fun `calculates acwr and handles zero chronic load`() {
        assertThat(TrainingLoadCalculator.acwr(40.0, 50.0)).isEqualTo(0.8)
        assertThat(TrainingLoadCalculator.acwr(30.0, 0.0)).isNull()
        assertThatThrownBy { TrainingLoadCalculator.acwr(-1.0, 50.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { TrainingLoadCalculator.acwr(30.0, Double.NaN) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `calculates monotony and handles insufficient or uniform data`() {
        assertThat(TrainingLoadCalculator.monotony(emptyList())).isNull()
        assertThat(TrainingLoadCalculator.monotony(listOf(100.0))).isNull()
        assertThat(TrainingLoadCalculator.monotony(listOf(10.0, 10.0, 10.0))).isNull()
        assertThat(TrainingLoadCalculator.monotony(listOf(0.0, 0.0, 10.0, 10.0)))
            .isCloseTo(1.0, within(1e-12))
    }

    @Test
    fun `monotony rejects invalid loads`() {
        assertThatThrownBy { TrainingLoadCalculator.monotony(listOf(-1.0, 10.0)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { TrainingLoadCalculator.monotony(listOf(Double.NaN, 10.0)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
