package com.meucoach.core.domain.zones

import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.HeartRateZone
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class HrZoneDistributionCalculatorTest {
    @Test
    fun `allocates time to each heart rate zone`() {
        val distribution = HrZoneDistributionCalculator.calculate(
            samples = listOf(
                sample(offset = 0, heartRate = 140),
                sample(offset = 60, heartRate = 160),
                sample(offset = 120, heartRate = 175),
                sample(offset = 180, heartRate = 181),
                sample(offset = 240, heartRate = 181),
            ),
            lthrBpm = 180,
        )!!

        assertThat(distribution.totalSec).isEqualTo(240)
        assertThat(distribution.durationOf(HeartRateZone.Z1)).isEqualTo(60)
        assertThat(distribution.durationOf(HeartRateZone.Z2)).isEqualTo(60)
        assertThat(distribution.durationOf(HeartRateZone.Z3)).isZero()
        assertThat(distribution.durationOf(HeartRateZone.Z4)).isEqualTo(60)
        assertThat(distribution.durationOf(HeartRateZone.Z5)).isEqualTo(60)
        assertThat(distribution.percentOf(HeartRateZone.Z2)).isEqualTo(25.0)
    }

    @Test
    fun `sorts samples and skips intervals without heart rate`() {
        val distribution = HrZoneDistributionCalculator.calculate(
            samples = listOf(
                sample(offset = 120, heartRate = 150),
                sample(offset = 0, heartRate = 150),
                sample(offset = 60, heartRate = null),
                sample(offset = 180, heartRate = 150),
            ),
            lthrBpm = 180,
        )!!

        assertThat(distribution.totalSec).isEqualTo(120)
        assertThat(distribution.durationOf(HeartRateZone.Z2)).isEqualTo(120)
        assertThat(distribution.durationOf(HeartRateZone.Z1)).isZero()
    }

    @Test
    fun `returns null without intervals or heart rate data`() {
        assertThat(HrZoneDistributionCalculator.calculate(emptyList(), 180)).isNull()
        assertThat(
            HrZoneDistributionCalculator.calculate(listOf(sample(offset = 0, heartRate = 150)), 180),
        ).isNull()
        assertThat(
            HrZoneDistributionCalculator.calculate(
                listOf(sample(offset = 0, heartRate = null), sample(offset = 60, heartRate = null)),
                180,
            ),
        ).isNull()
    }

    @Test
    fun `rejects an invalid LTHR`() {
        assertThatThrownBy {
            HrZoneDistributionCalculator.calculate(
                listOf(sample(offset = 0, heartRate = 150), sample(offset = 60, heartRate = 150)),
                lthrBpm = 90,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun sample(offset: Int, heartRate: Int?) = ActivitySample(
        offsetSec = offset,
        heartRate = heartRate,
    )
}
