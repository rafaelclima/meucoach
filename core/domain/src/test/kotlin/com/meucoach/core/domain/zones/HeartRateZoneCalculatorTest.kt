package com.meucoach.core.domain.zones

import com.meucoach.core.model.HeartRateZone
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class HeartRateZoneCalculatorTest {
    private val lthr = 180

    @Test
    fun `classifies exact zone boundaries from lthr`() {
        assertThat(HeartRateZoneCalculator.zoneFor(145, lthr)).isEqualTo(HeartRateZone.Z1)
        assertThat(HeartRateZoneCalculator.zoneFor(146, lthr)).isEqualTo(HeartRateZone.Z2)
        assertThat(HeartRateZoneCalculator.zoneFor(161, lthr)).isEqualTo(HeartRateZone.Z2)
        assertThat(HeartRateZoneCalculator.zoneFor(162, lthr)).isEqualTo(HeartRateZone.Z3)
        assertThat(HeartRateZoneCalculator.zoneFor(169, lthr)).isEqualTo(HeartRateZone.Z3)
        assertThat(HeartRateZoneCalculator.zoneFor(170, lthr)).isEqualTo(HeartRateZone.Z4)
        assertThat(HeartRateZoneCalculator.zoneFor(179, lthr)).isEqualTo(HeartRateZone.Z4)
        assertThat(HeartRateZoneCalculator.zoneFor(180, lthr)).isEqualTo(HeartRateZone.Z5)
        assertThat(HeartRateZoneCalculator.zoneFor(187, lthr)).isEqualTo(HeartRateZone.Z5)
    }

    @Test
    fun `builds contiguous ranges without gaps for lthr 180`() {
        val ranges = HeartRateZoneCalculator.ranges(lthr)

        assertThat(ranges.map { it.zone }).containsExactly(
            HeartRateZone.Z1,
            HeartRateZone.Z2,
            HeartRateZone.Z3,
            HeartRateZone.Z4,
            HeartRateZone.Z5,
        )
        assertThat(ranges[0].minBpm).isEqualTo(0)
        assertThat(ranges[0].maxBpm).isEqualTo(145)
        assertThat(ranges[1].minBpm).isEqualTo(146)
        assertThat(ranges[1].maxBpm).isEqualTo(161)
        assertThat(ranges[2].minBpm).isEqualTo(162)
        assertThat(ranges[2].maxBpm).isEqualTo(169)
        assertThat(ranges[3].minBpm).isEqualTo(170)
        assertThat(ranges[3].maxBpm).isEqualTo(179)
        assertThat(ranges[4].minBpm).isEqualTo(180)
        assertThat(ranges[4].maxBpm).isNull()

        ranges.zipWithNext().forEach { (current, next) ->
            assertThat(current.maxBpm?.plus(1)).isEqualTo(next.minBpm)
        }
    }

    @Test
    fun `range contains respects open final zone`() {
        val ranges = HeartRateZoneCalculator.ranges(lthr)

        assertThat(ranges[0].contains(145)).isTrue()
        assertThat(ranges[0].contains(146)).isFalse()
        assertThat(ranges[4].contains(180)).isTrue()
        assertThat(ranges[4].contains(250)).isTrue()
        assertThat(ranges[4].contains(179)).isFalse()
    }

    @Test
    fun `rejects invalid heart rate and lthr`() {
        assertThatThrownBy { HeartRateZoneCalculator.zoneFor(150, 99) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { HeartRateZoneCalculator.zoneFor(150, 231) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { HeartRateZoneCalculator.zoneFor(29, lthr) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { HeartRateZoneCalculator.zoneFor(251, lthr) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { HeartRateZoneCalculator.ranges(99) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `validates zone range values`() {
        assertThatThrownBy { HeartRateZoneRange(HeartRateZone.Z1, minBpm = -1, maxBpm = 100) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { HeartRateZoneRange(HeartRateZone.Z2, minBpm = 100, maxBpm = 90) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
