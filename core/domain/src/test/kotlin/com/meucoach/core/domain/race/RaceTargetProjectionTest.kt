package com.meucoach.core.domain.race

import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.RaceDistance
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class RaceTargetProjectionTest {
    @Test
    fun `five k in twenty-five minutes is five minutes per kilometer`() {
        val projection = RaceTargetProjector.project(RaceDistance.FIVE_K, 1_500)!!

        assertThat(projection.targetPaceSecPerKm).isEqualTo(300)
        assertThat(projection.paceRangeSecPerKm).isEqualTo(291..309)
        assertThat(projection.finishSec).isEqualTo(1_500)
        assertThat(projection.splits).hasSize(5)
        assertThat(projection.splits.first()).isEqualTo(
            RaceSplitProjection(distanceMeters = 1_000, cumulativeSec = 300, isFinish = false),
        )
        assertThat(projection.splits.last()).isEqualTo(
            RaceSplitProjection(distanceMeters = 5_000, cumulativeSec = 1_500, isFinish = true),
        )
        assertThat(projection.confidence).isEqualTo(DataConfidence.HIGH)
        assertThat(projection.ruleIds).containsExactly("R-RACE-02")
    }

    @Test
    fun `ten k in fifty minutes keeps exact kilometer splits`() {
        val projection = RaceTargetProjector.project(RaceDistance.TEN_K, 3_000)!!

        assertThat(projection.targetPaceSecPerKm).isEqualTo(300)
        assertThat(projection.splits).hasSize(10)
        assertThat(projection.splits[4].cumulativeSec).isEqualTo(1_500)
        assertThat(projection.splits.last().cumulativeSec).isEqualTo(3_000)
        assertThat(projection.splits.last().isFinish).isTrue()
    }

    @Test
    fun `half marathon rounds the pace and ends at the exact target`() {
        val projection = RaceTargetProjector.project(RaceDistance.HALF_MARATHON, 6_300)!!

        // 6300 / 21,0975 = 298,61 -> 299 s/km (4:59/km)
        assertThat(projection.targetPaceSecPerKm).isEqualTo(299)
        assertThat(projection.paceRangeSecPerKm).isEqualTo(290..308)
        assertThat(projection.splits).hasSize(22)
        assertThat(projection.splits[20]).isEqualTo(
            RaceSplitProjection(distanceMeters = 21_000, cumulativeSec = 6_271, isFinish = false),
        )
        assertThat(projection.splits.last()).isEqualTo(
            RaceSplitProjection(distanceMeters = 21_098, cumulativeSec = 6_300, isFinish = true),
        )
    }

    @Test
    fun `marathon rounds the pace and keeps the last fraction as the finish`() {
        val projection = RaceTargetProjector.project(RaceDistance.MARATHON, 12_600)!!

        assertThat(projection.targetPaceSecPerKm).isEqualTo(299)
        assertThat(projection.splits).hasSize(43)
        assertThat(projection.splits[41].cumulativeSec).isEqualTo(12_542)
        assertThat(projection.splits.last()).isEqualTo(
            RaceSplitProjection(distanceMeters = 42_195, cumulativeSec = 12_600, isFinish = true),
        )
    }

    @Test
    fun `range is rounded to the second and includes the target pace`() {
        val projection = RaceTargetProjector.project(RaceDistance.FIVE_K, 1_200)!!

        // 240 s/km, 3% = 7,2 -> 7
        assertThat(projection.targetPaceSecPerKm).isEqualTo(240)
        assertThat(projection.paceRangeSecPerKm).isEqualTo(233..247)
        assertThat(projection.paceRangeSecPerKm.first).isLessThan(projection.targetPaceSecPerKm)
        assertThat(projection.paceRangeSecPerKm.last).isGreaterThan(projection.targetPaceSecPerKm)
    }

    @Test
    fun `without a target time nothing is invented`() {
        assertThat(RaceTargetProjector.project(RaceDistance.TEN_K, null)).isNull()
        assertThat(RaceTargetProjector.project(RaceDistance.TEN_K, 0)).isNull()
        assertThat(RaceTargetProjector.project(RaceDistance.TEN_K, -1)).isNull()
    }

    @Test
    fun `a zero range percent returns a single-second range`() {
        val projection = RaceTargetProjector.project(RaceDistance.TEN_K, 3_000, rangePercent = 0)!!

        assertThat(projection.paceRangeSecPerKm).isEqualTo(300..300)
    }

    @Test
    fun `all splits are strictly increasing`() {
        for (distance in RaceDistance.entries) {
            val projection = RaceTargetProjector.project(distance, 10_000)!!

            assertThat(projection.splits).allMatch { it.cumulativeSec > 0 }
            assertThat(projection.splits.zipWithNext()).allMatch { (first, second) ->
                second.distanceMeters > first.distanceMeters && second.cumulativeSec > first.cumulativeSec
            }
        }
    }
}
