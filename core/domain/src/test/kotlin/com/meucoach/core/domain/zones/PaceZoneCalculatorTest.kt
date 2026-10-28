package com.meucoach.core.domain.zones

import com.meucoach.core.model.Pace
import com.meucoach.core.model.PaceZone
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class PaceZoneCalculatorTest {
    private val threshold = Pace.fromSecondsPerKm(364)

    @Test
    fun `builds target ranges for user zero threshold pace`() {
        val ranges = PaceZoneCalculator.ranges(threshold).associateBy { it.zone }

        assertThat(ranges.getValue(PaceZone.E).minSecPerKm).isEqualTo(455)
        assertThat(ranges.getValue(PaceZone.E).maxSecPerKm).isEqualTo(510)
        assertThat(ranges.getValue(PaceZone.M).minSecPerKm).isEqualTo(400)
        assertThat(ranges.getValue(PaceZone.M).maxSecPerKm).isEqualTo(419)
        assertThat(ranges.getValue(PaceZone.T).minSecPerKm).isEqualTo(364)
        assertThat(ranges.getValue(PaceZone.T).maxSecPerKm).isEqualTo(375)
        assertThat(ranges.getValue(PaceZone.I).minSecPerKm).isEqualTo(335)
        assertThat(ranges.getValue(PaceZone.I).maxSecPerKm).isEqualTo(346)
        assertThat(ranges.getValue(PaceZone.R).minSecPerKm).isEqualTo(309)
        assertThat(ranges.getValue(PaceZone.R).maxSecPerKm).isEqualTo(328)
    }

    @Test
    fun `classifies paces by nearest target zone`() {
        assertThat(PaceZoneCalculator.zoneFor(Pace.fromSecondsPerKm(460), threshold))
            .isEqualTo(PaceZone.E)
        assertThat(PaceZoneCalculator.zoneFor(Pace.fromSecondsPerKm(410), threshold))
            .isEqualTo(PaceZone.M)
        assertThat(PaceZoneCalculator.zoneFor(Pace.fromSecondsPerKm(364), threshold))
            .isEqualTo(PaceZone.T)
        assertThat(PaceZoneCalculator.zoneFor(Pace.fromSecondsPerKm(340), threshold))
            .isEqualTo(PaceZone.I)
        assertThat(PaceZoneCalculator.zoneFor(Pace.fromSecondsPerKm(320), threshold))
            .isEqualTo(PaceZone.R)
    }

    @Test
    fun `clamps extreme paces to first or last zone`() {
        assertThat(PaceZoneCalculator.zoneFor(Pace.fromSecondsPerKm(280), threshold))
            .isEqualTo(PaceZone.R)
        assertThat(PaceZoneCalculator.zoneFor(Pace.fromSecondsPerKm(700), threshold))
            .isEqualTo(PaceZone.E)
    }

    @Test
    fun `range contains includes both ends`() {
        val easy = PaceZoneCalculator.ranges(threshold).first { it.zone == PaceZone.E }

        assertThat(easy.contains(455)).isTrue()
        assertThat(easy.contains(510)).isTrue()
        assertThat(easy.contains(454)).isFalse()
        assertThat(easy.contains(511)).isFalse()
    }

    @Test
    fun `rejects unsupported threshold pace and invalid ranges`() {
        assertThatThrownBy { PaceZoneCalculator.ranges(Pace.fromSecondsPerKm(149)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { PaceZoneCalculator.ranges(Pace.fromSecondsPerKm(901)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { PaceZoneCalculator.zoneFor(Pace.fromSecondsPerKm(400), Pace.fromSecondsPerKm(901)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { PaceZoneRange(PaceZone.E, minSecPerKm = 0, maxSecPerKm = 100) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { PaceZoneRange(PaceZone.E, minSecPerKm = 200, maxSecPerKm = 100) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
