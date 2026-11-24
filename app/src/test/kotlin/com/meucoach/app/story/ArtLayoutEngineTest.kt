package com.meucoach.app.story

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtFont
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtLayout
import com.meucoach.core.domain.story.ArtOffset
import com.meucoach.core.domain.story.ArtTemplate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtLayoutEngineTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val engine = ArtLayoutEngine(ArtFont.INTER.typeface(context))

    @Test
    fun `top template preserves the legacy 4x5 frame`() {
        val layout = layout(format = ArtFormat.PORTRAIT_4_5, includeExtras = false)

        assertThat(layout.distance.label.baseline).isEqualTo(307f)
        assertThat(layout.distance.number.baseline).isEqualTo(460f)
        assertThat(layout.paceTime.paceLabel.baseline).isEqualTo(307f)
        assertThat(layout.paceTime.paceValue.baseline).isEqualTo(353f)
        assertThat(layout.paceTime.timeLabel.baseline).isEqualTo(405f)
        assertThat(layout.paceTime.timeValue.baseline).isEqualTo(451f)
        assertThat(layout.route.path).isEqualTo(ArtRect(244f, 569f, 836f, 1161f))
        assertThat(layout.route.square.width).isCloseTo(layout.route.square.height, within(0.001f))
    }

    @Test
    fun `top template preserves the legacy square frame`() {
        val layout = layout(format = ArtFormat.SQUARE, includeExtras = false)

        assertThat(layout.distance.label.baseline).isEqualTo(265f)
        assertThat(layout.distance.number.baseline).isEqualTo(400f)
        assertThat(layout.paceTime.timeValue.baseline).isEqualTo(409f)
        assertThat(layout.route.path).isEqualTo(ArtRect(338.5f, 503f, 741.5f, 906f))
        assertThat(layout.route.square.width).isCloseTo(layout.route.square.height, within(0.001f))
    }

    @Test
    fun `top template preserves the legacy 9x16 route frame`() {
        val layout = layout(format = ArtFormat.PORTRAIT_9_16, includeExtras = false)

        assertThat(layout.route.path).isEqualTo(ArtRect(244f, 854f, 836f, 1446f))
        assertThat(layout.route.square.width).isCloseTo(layout.route.square.height, within(0.001f))
    }

    @Test
    fun `photo 9x16 respects the native story safe area`() {
        val layout = layout(
            format = ArtFormat.PORTRAIT_9_16,
            background = ArtBackgroundMode.PHOTO,
        )

        assertThat(layout.safeArea).isEqualTo(ArtRect(88f, 250f, 992f, 1580f))
        assertThat(layout.brand.bounds.top).isEqualTo(250f)
        with(layout) {
            assertInside(distance.bounds, safeArea)
            assertInside(paceTime.bounds, safeArea)
            assertInside(checkNotNull(extras).bounds, safeArea)
            assertInside(route.square, safeArea)
            assertInside(route.path, safeArea)
        }
        assertThat(layout.route.square.width).isCloseTo(layout.route.square.height, within(0.001f))
    }

    @Test
    fun `transparent and photo margins keep 88 sides with a larger bottom`() {
        listOf(ArtFormat.PORTRAIT_4_5, ArtFormat.SQUARE).forEach { format ->
            val layout = layout(format = format, background = ArtBackgroundMode.PHOTO)
            assertThat(layout.safeArea.top).isEqualTo(88f)
            assertThat(layout.safeArea.bottom).isEqualTo(format.height - 150f)
            assertThat(layout.brand.bounds.top).isEqualTo(84f)
        }
        val transparent = layout(format = ArtFormat.PORTRAIT_4_5)
        assertThat(transparent.safeArea.bottom).isEqualTo(ArtFormat.PORTRAIT_4_5.height - 150f)
    }

    @Test
    fun `the route square never exceeds the cap on tall cards`() {
        val fourFive = layout(format = ArtFormat.PORTRAIT_4_5)
        val nineSixteen = layout(format = ArtFormat.PORTRAIT_9_16)
        val square = layout(format = ArtFormat.SQUARE)

        assertThat(nineSixteen.route.square.width).isCloseTo(640f, within(0.01f))
        assertThat(fourFive.route.square.width).isLessThanOrEqualTo(640f)
        assertThat(fourFive.route.square.width).isGreaterThan(0f)
        assertThat(square.route.square.width).isLessThan(640f)
    }

    @Test
    fun `templates never overlap or clip across formats and scales`() {
        ArtFormat.entries.forEach { format ->
            ArtTemplate.entries.forEach { template ->
                listOf(80, 100, 130).forEach { scale ->
                    val layout = layout(
                        format = format,
                        template = template,
                        textScalePercent = scale,
                    )
                    assertInside(layout.distance.bounds, layout.safeArea)
                    assertInside(layout.paceTime.bounds, layout.safeArea)
                    assertInside(checkNotNull(layout.extras).bounds, layout.safeArea)
                    assertInside(layout.route.square, layout.safeArea)
                    assertInside(layout.route.path, layout.safeArea)
                    assertInside(layout.brand.bounds, ArtRect(0f, 0f, 1080f, format.height.toFloat()))
                    assertNoOverlap(layout)
                    assertThat(layout.route.square.width)
                        .describedAs("%s %s %d", format, template, scale)
                        .isCloseTo(layout.route.square.height, within(0.001f))
                    assertThat(layout.route.square.width).isGreaterThan(0f)
                }
            }
        }
    }

    @Test
    fun `offsets move only the selected block without text scaling`() {
        val base = layout(format = ArtFormat.PORTRAIT_4_5)
        val moved = engine.layout(
            format = ArtFormat.PORTRAIT_4_5,
            background = ArtBackgroundMode.TRANSPARENT,
            layout = ArtLayout(distanceOffset = ArtOffset(0.02f, 0.02f)),
            text = text(),
            canvasHeight = 1350f,
            logoAspectRatio = LOGO_ASPECT,
        )

        assertThat(moved.distance.bounds.left).isCloseTo(base.distance.bounds.left + 21.6f, within(0.01f))
        assertThat(moved.distance.bounds.top).isCloseTo(base.distance.bounds.top + 27f, within(0.01f))
        assertThat(moved.paceTime.bounds).isEqualTo(base.paceTime.bounds)
        assertThat(moved.extras?.bounds).isEqualTo(base.extras?.bounds)
        assertThat(moved.route).isEqualTo(base.route)
    }

    @Test
    fun `offsets are not multiplied by the text scale`() {
        val small = layout(format = ArtFormat.PORTRAIT_4_5, textScalePercent = 80)
        val large = layout(format = ArtFormat.PORTRAIT_4_5, textScalePercent = 130)
        val smallMoved = engine.layout(
            format = ArtFormat.PORTRAIT_4_5,
            background = ArtBackgroundMode.TRANSPARENT,
            layout = ArtLayout(distanceOffset = ArtOffset(0.02f, 0.02f), textScalePercent = 80),
            text = text(),
            canvasHeight = 1350f,
            logoAspectRatio = LOGO_ASPECT,
        )
        val largeMoved = engine.layout(
            format = ArtFormat.PORTRAIT_4_5,
            background = ArtBackgroundMode.TRANSPARENT,
            layout = ArtLayout(distanceOffset = ArtOffset(0.02f, 0.02f), textScalePercent = 130),
            text = text(),
            canvasHeight = 1350f,
            logoAspectRatio = LOGO_ASPECT,
        )

        assertThat(smallMoved.distance.bounds.left - small.distance.bounds.left)
            .isCloseTo(largeMoved.distance.bounds.left - large.distance.bounds.left, within(0.01f))
        assertThat(smallMoved.distance.bounds.top - small.distance.bounds.top)
            .isCloseTo(largeMoved.distance.bounds.top - large.distance.bounds.top, within(0.01f))
    }

    @Test
    fun `offsets are clamped by the block box and the free region`() {
        val base = layout(format = ArtFormat.PORTRAIT_4_5)
        val clamped = engine.layout(
            format = ArtFormat.PORTRAIT_4_5,
            background = ArtBackgroundMode.TRANSPARENT,
            layout = ArtLayout(
                distanceOffset = ArtOffset(0.5f, 0.5f),
                routeOffset = ArtOffset(0.5f, 0.5f),
            ),
            text = text(),
            canvasHeight = 1350f,
            logoAspectRatio = LOGO_ASPECT,
        )
        val free = ArtRect(32f, 32f, 1048f, 1318f)

        // The text block now travels past the old safe area (right 992):
        // the free region only stops it at the canvas edge.
        assertThat(clamped.distance.bounds.right).isGreaterThan(clamped.safeArea.right)
        assertThat(clamped.distance.bounds.top).isGreaterThan(base.distance.bounds.top)
        assertInside(clamped.distance.bounds, free)
        assertThat(clamped.route.square.right).isCloseTo(free.right, within(0.01f))
        assertThat(clamped.route.square.bottom).isCloseTo(free.bottom, within(0.01f))
        assertInside(clamped.route.square, free)
        assertThat(clamped.route.square.width).isCloseTo(clamped.route.square.height, within(0.001f))
        assertThat(clamped.route.square.width).isLessThanOrEqualTo(640f)
    }

    @Test
    fun `text scale multiplies text but not the brand`() {
        val small = layout(format = ArtFormat.PORTRAIT_4_5, textScalePercent = 80)
        val normal = layout(format = ArtFormat.PORTRAIT_4_5, textScalePercent = 100)
        val large = layout(format = ArtFormat.PORTRAIT_4_5, textScalePercent = 130)

        assertThat(small.distance.number.size).isCloseTo(108f, within(0.01f))
        assertThat(normal.distance.number.size).isCloseTo(135f, within(0.01f))
        assertThat(large.distance.number.size).isCloseTo(175.5f, within(0.01f))
        assertThat(small.brand.bounds).isEqualTo(normal.brand.bounds)
        assertThat(large.brand.bounds).isEqualTo(normal.brand.bounds)
    }

    @Test
    fun `scrims exist only in photo mode and wrap every text block`() {
        val transparent = layout(format = ArtFormat.PORTRAIT_4_5)
        val photo = layout(format = ArtFormat.PORTRAIT_4_5, background = ArtBackgroundMode.PHOTO)

        assertThat(transparent.scrims).isEmpty()
        assertThat(photo.scrims).hasSize(4)
        assertThat(photo.scrims[0]).isEqualTo(photo.brand.bounds.expanded(ArtScrim.PADDING))
        assertThat(photo.scrims[1]).isEqualTo(photo.distance.bounds.expanded(ArtScrim.PADDING))
        assertThat(photo.scrims[2]).isEqualTo(photo.paceTime.bounds.expanded(ArtScrim.PADDING))
        assertThat(photo.scrims[3]).isEqualTo(checkNotNull(photo.extras).bounds.expanded(ArtScrim.PADDING))
        assertThat(ArtScrim.ALPHA).isEqualTo(148)
        assertThat(ArtScrim.PADDING).isEqualTo(36f)
        assertThat(ArtScrim.RADIUS).isEqualTo(48f)
        assertThat(ArtScrim.argb ushr 24).isEqualTo(148)
    }

    @Test
    fun `a textual brand keeps the fixed wordmark slot`() {
        val layout = engine.layout(
            format = ArtFormat.PORTRAIT_4_5,
            background = ArtBackgroundMode.TRANSPARENT,
            layout = ArtLayout(),
            text = text(),
            canvasHeight = 1350f,
        )

        assertThat(layout.brand.logoBounds).isNull()
        assertThat(layout.brand.text).isNotNull()
        assertThat(layout.brand.bounds.height).isEqualTo(44f)
        assertThat(layout.brand.bounds.width).isGreaterThan(0f)
    }

    @Test
    fun `a long distance is measured against the pace column`() {
        val layout = engine.layout(
            format = ArtFormat.PORTRAIT_4_5,
            background = ArtBackgroundMode.TRANSPARENT,
            layout = ArtLayout(),
            text = text(distance = "12345,67 km"),
            canvasHeight = 1350f,
            logoAspectRatio = LOGO_ASPECT,
        )

        assertThat(layout.distance.bounds.right).isLessThan(layout.paceTime.bounds.left)
        assertThat(layout.distance.number.size).isLessThan(135f)
        assertInside(layout.distance.bounds, layout.safeArea)
    }

    private fun layout(
        format: ArtFormat,
        background: ArtBackgroundMode = ArtBackgroundMode.TRANSPARENT,
        template: ArtTemplate = ArtTemplate.TOP,
        textScalePercent: Int = 100,
        includeExtras: Boolean = true,
    ): ArtCardLayout = engine.layout(
        format = format,
        background = background,
        layout = ArtLayout(template = template, textScalePercent = textScalePercent),
        text = text(includeExtras = includeExtras),
        canvasHeight = format.height.toFloat(),
        logoAspectRatio = LOGO_ASPECT,
    )

    private fun text(distance: String = "5,12 km", includeExtras: Boolean = true): ArtLayoutText = ArtLayoutText(
        distanceLabel = "DISTÂNCIA",
        distanceValue = distance,
        paceLabel = "RITMO MÉDIO",
        paceValue = "6:00 /km",
        timeLabel = "TEMPO TOTAL",
        timeValue = "30:00",
        extras = if (includeExtras) "24 out 2026 · 150 bpm" else null,
    )

    private fun assertInside(rect: ArtRect, container: ArtRect) {
        assertThat(container.contains(rect))
            .describedAs("%s must fit inside %s", rect, container)
            .isTrue()
    }

    private fun assertNoOverlap(layout: ArtCardLayout) {
        val blocks = buildList {
            add("brand" to layout.brand.bounds)
            add("distance" to layout.distance.bounds)
            add("paceTime" to layout.paceTime.bounds)
            layout.extras?.let { add("extras" to it.bounds) }
            add("route" to layout.route.square)
        }
        blocks.forEachIndexed { index, (firstName, first) ->
            blocks.drop(index + 1).forEach { (secondName, second) ->
                assertThat(overlaps(first, second))
                    .describedAs("%s overlaps %s: %s vs %s", firstName, secondName, first, second)
                    .isFalse()
            }
        }
    }

    /**
     * Font metrics include ascent/descent beyond the glyphs; the frozen ADR
     * 0060 gap leaves less than one design pixel of line-box overlap at 100%.
     * One pixel is the tolerance between a line box and the next block.
     */
    private fun overlaps(first: ArtRect, second: ArtRect): Boolean {
        val tolerance = 1f
        return first.left + tolerance < second.right &&
            second.left + tolerance < first.right &&
            first.top + tolerance < second.bottom &&
            second.top + tolerance < first.bottom
    }

    private companion object {
        const val LOGO_ASPECT = 3.8f
    }

    @Test
    fun `effective offset clamps a far nudge into the free region`() {
        val text = ArtLayoutText(
            distanceLabel = "DISTÂNCIA",
            distanceValue = "5,00",
            paceLabel = "RITMO MÉDIO",
            paceValue = "6:00 /km",
            timeLabel = "TEMPO TOTAL",
            timeValue = "30:00",
            extras = null,
        )
        // The pace/time column already hugs the right margin: pushing it half
        // a canvas right cannot survive the box-aware clamp.
        val desired = ArtOffset(dx = 0.5f, dy = 0f)
        val effective = engine.effectiveOffset(
            format = ArtFormat.PORTRAIT_4_5,
            background = ArtBackgroundMode.TRANSPARENT,
            layout = ArtLayout(),
            block = com.meucoach.core.domain.story.ArtBlock.PACE_TIME,
            desired = desired,
            text = text,
            canvasHeight = ArtFormat.PORTRAIT_4_5.height.toFloat(),
        )
        assertThat(effective.dx).isLessThan(0.5f)
        assertThat(effective.dx).isGreaterThanOrEqualTo(-0.5f)
        // The effective value round-trips: clamping it again is a no-op.
        val second = engine.effectiveOffset(
            format = ArtFormat.PORTRAIT_4_5,
            background = ArtBackgroundMode.TRANSPARENT,
            layout = ArtLayout(paceTimeOffset = effective),
            block = com.meucoach.core.domain.story.ArtBlock.PACE_TIME,
            desired = effective,
            text = text,
            canvasHeight = ArtFormat.PORTRAIT_4_5.height.toFloat(),
        )
        assertThat(second.dx).isCloseTo(effective.dx, org.assertj.core.api.Assertions.within(0.001f))
    }

    @Test
    fun `effective offset keeps a small nudge untouched`() {
        val text = ArtLayoutText(
            distanceLabel = "DISTÂNCIA",
            distanceValue = "5,00",
            paceLabel = "RITMO MÉDIO",
            paceValue = "6:00 /km",
            timeLabel = "TEMPO TOTAL",
            timeValue = "30:00",
            extras = null,
        )
        val desired = ArtOffset(dx = 0.02f, dy = -0.02f)
        val effective = engine.effectiveOffset(
            format = ArtFormat.PORTRAIT_4_5,
            background = ArtBackgroundMode.TRANSPARENT,
            layout = ArtLayout(),
            block = com.meucoach.core.domain.story.ArtBlock.DISTANCE,
            desired = desired,
            text = text,
            canvasHeight = ArtFormat.PORTRAIT_4_5.height.toFloat(),
        )
        assertThat(effective.dx).isCloseTo(0.02f, org.assertj.core.api.Assertions.within(0.001f))
        assertThat(effective.dy).isCloseTo(-0.02f, org.assertj.core.api.Assertions.within(0.001f))
    }
}
