package com.meucoach.core.domain.story

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class ArtLayoutTest {
    @Test
    fun `default layout is top with zero offsets and 100 percent text`() {
        val layout = ArtLayout()

        assertThat(layout.template).isEqualTo(ArtTemplate.TOP)
        assertThat(layout.distanceOffset).isEqualTo(ArtOffset())
        assertThat(layout.paceTimeOffset).isEqualTo(ArtOffset())
        assertThat(layout.extrasOffset).isEqualTo(ArtOffset())
        assertThat(layout.routeOffset).isEqualTo(ArtOffset())
        assertThat(layout.textScalePercent).isEqualTo(100)
    }

    @Test
    fun `offset maps each block to its own offset`() {
        val layout = ArtLayout(
            distanceOffset = ArtOffset(0.02f, -0.04f),
            paceTimeOffset = ArtOffset(0.06f, 0.08f),
            extrasOffset = ArtOffset(-0.1f, 0.12f),
            routeOffset = ArtOffset(0.14f, -0.16f),
        )

        assertThat(layout.offset(ArtBlock.DISTANCE)).isEqualTo(ArtOffset(0.02f, -0.04f))
        assertThat(layout.offset(ArtBlock.PACE_TIME)).isEqualTo(ArtOffset(0.06f, 0.08f))
        assertThat(layout.offset(ArtBlock.EXTRAS)).isEqualTo(ArtOffset(-0.1f, 0.12f))
        assertThat(layout.offset(ArtBlock.ROUTE)).isEqualTo(ArtOffset(0.14f, -0.16f))
    }

    @Test
    fun `withOffset changes only the selected block`() {
        val original = ArtLayout(
            template = ArtTemplate.CENTER,
            distanceOffset = ArtOffset(0.02f, 0.02f),
            textScalePercent = 115,
        )

        val distance = original.withOffset(ArtBlock.DISTANCE, ArtOffset(0.04f, 0.04f))
        val paceTime = original.withOffset(ArtBlock.PACE_TIME, ArtOffset(0.06f, 0.06f))
        val extras = original.withOffset(ArtBlock.EXTRAS, ArtOffset(0.08f, 0.08f))
        val route = original.withOffset(ArtBlock.ROUTE, ArtOffset(-0.1f, 0.2f))

        assertThat(distance.distanceOffset).isEqualTo(ArtOffset(0.04f, 0.04f))
        assertThat(distance.paceTimeOffset).isEqualTo(ArtOffset())
        assertThat(paceTime.paceTimeOffset).isEqualTo(ArtOffset(0.06f, 0.06f))
        assertThat(paceTime.distanceOffset).isEqualTo(ArtOffset(0.02f, 0.02f))
        assertThat(extras.extrasOffset).isEqualTo(ArtOffset(0.08f, 0.08f))
        assertThat(extras.routeOffset).isEqualTo(ArtOffset())
        assertThat(route.routeOffset).isEqualTo(ArtOffset(-0.1f, 0.2f))
        assertThat(route.distanceOffset).isEqualTo(ArtOffset(0.02f, 0.02f))
        assertThat(route.template).isEqualTo(ArtTemplate.CENTER)
        assertThat(route.textScalePercent).isEqualTo(115)
    }

    @Test
    fun `text scale accepts the full 80 to 130 percent range`() {
        assertThat(ArtLayout(textScalePercent = 80).textScalePercent).isEqualTo(80)
        assertThat(ArtLayout(textScalePercent = 130).textScalePercent).isEqualTo(130)
    }

    @Test
    fun `text scale below 80 or above 130 is rejected`() {
        assertThatThrownBy { ArtLayout(textScalePercent = 79) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ArtLayout(textScalePercent = 131) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `offset accepts values up to half the canvas`() {
        assertThat(ArtOffset(dx = 0.5f, dy = -0.5f).dx).isEqualTo(0.5f)
        assertThat(ArtOffset(dx = -0.5f, dy = 0.5f).dy).isEqualTo(0.5f)
    }

    @Test
    fun `offset rejects non finite values and values beyond half the canvas`() {
        assertThatThrownBy { ArtOffset(dx = Float.NaN) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ArtOffset(dy = Float.POSITIVE_INFINITY) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ArtOffset(dx = 0.5001f) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ArtOffset(dy = -0.5001f) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `withOffset rejects an invalid offset`() {
        assertThatThrownBy {
            ArtLayout().withOffset(ArtBlock.DISTANCE, ArtOffset(dx = 0.6f))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `blocks and templates expose the documented order`() {
        assertThat(ArtBlock.entries).containsExactly(
            ArtBlock.DISTANCE,
            ArtBlock.PACE_TIME,
            ArtBlock.EXTRAS,
            ArtBlock.ROUTE,
        )
        assertThat(ArtTemplate.entries).containsExactly(
            ArtTemplate.TOP,
            ArtTemplate.CENTER,
            ArtTemplate.BOTTOM,
        )
    }
}
