package com.meucoach.app.story

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.domain.story.ArtColor
import com.meucoach.core.domain.story.ArtFont
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.RouteData
import com.meucoach.core.domain.story.RoutePoint
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryPlanCalculator
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoryArtRendererTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val renderer = StoryArtRenderer(
        recipe = ArtRecipe(),
        typeface = ArtFont.INTER.typeface(context),
        logoBitmap = StoryBranding.logo(context),
    )

    @Test
    fun `renders the 4x5 card at 1080x1350`() {
        val bitmap = checkNotNull(renderer.render(plan(), plan().localDate, 1080, 1350))

        assertThat(bitmap.width).isEqualTo(1080)
        assertThat(bitmap.height).isEqualTo(1350)
        assertThat(bitmap.config).isEqualTo(android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.recycle()
    }

    @Test
    fun `renders the 1x1 card at 1080x1080`() {
        val square = StoryArtRenderer(
            recipe = ArtRecipe(format = ArtFormat.SQUARE),
            typeface = ArtFont.INTER.typeface(context),
        )
        val bitmap = checkNotNull(square.render(plan(), plan().localDate, 1080, 1080))

        assertThat(bitmap.width).isEqualTo(1080)
        assertThat(bitmap.height).isEqualTo(1080)
        bitmap.recycle()
    }

    @Test
    fun `background alpha is zero for every pixel the content does not cover`() {
        val bitmap = checkNotNull(renderer.render(plan(), plan().localDate, 1080, 1350))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()

        // Corners stay empty: every block is inside the margins.
        listOf(0, 1_080 - 24, (1_350 - 24) * 1_080, (1_350 - 24) * 1_080 + 1_080 - 24).forEach { seed ->
            (0..23).forEach { row ->
                (0..23).forEach { column ->
                    val pixel = pixels[seed + row * 1_080 + column]
                    assertThat(pixel and 0xFF000000.toInt()).isEqualTo(0)
                }
            }
        }

        // No opaque background layer: most of the canvas must stay transparent.
        val transparent = pixels.count { it and 0xFF000000.toInt() == 0 }
        assertThat(transparent).isGreaterThan(pixels.size / 2)
    }

    @Test
    fun `preview renders at half resolution with the same transparent background`() {
        val bitmap = checkNotNull(renderer.render(plan(), plan().localDate, 540, 675))

        assertThat(bitmap.width).isEqualTo(540)
        assertThat(bitmap.height).isEqualTo(675)
        assertThat(bitmap.getPixel(0, 0) and 0xFF000000.toInt()).isEqualTo(0)
        assertThat(bitmap.getPixel(539, 674) and 0xFF000000.toInt()).isEqualTo(0)
        bitmap.recycle()
    }

    @Test
    fun `pace and time columns stay inside the margins`() {
        val bitmap = checkNotNull(renderer.render(plan(), plan().localDate, 1080, 1350))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()

        // Band of the pace/time columns (design y 560..690); nothing may spill
        // past the horizontal margins by more than the glyph antialiasing.
        val top = 560
        val bottom = 690
        val leftEdge = 88 - 8
        val rightEdge = 1080 - 88 + 8
        val spills = (top until bottom).flatMap { y ->
            (0..leftEdge).map { x -> pixels[y * 1080 + x] } +
                (rightEdge until 1080).map { x -> pixels[y * 1080 + x] }
        }.filter { it and 0xFF000000.toInt() != 0 }

        assertThat(spills).isEmpty()
    }

    @Test
    fun `returns null without a route`() {
        val routeless = plan().copy(route = RouteData(emptyList()))

        assertThat(renderer.render(routeless, routeless.localDate, 1080, 1350)).isNull()
    }

    @Test
    fun `writes the card as png`() {
        val output = ByteArrayOutputStream()

        assertThat(renderer.writePng(plan(), plan().localDate, 1080, 1350, output)).isTrue()

        val png = output.toByteArray()
        assertThat(png.size).isGreaterThan(1_000)
        assertThat(png.copyOfRange(0, PNG_SIGNATURE.size)).isEqualTo(PNG_SIGNATURE)
        val decoded = checkNotNull(BitmapFactory.decodeByteArray(png, 0, png.size))
        assertThat(decoded.width).isEqualTo(1080)
        assertThat(decoded.height).isEqualTo(1350)
        decoded.recycle()
    }

    @Test
    fun `route keeps its aspect inside the square area`() {
        // Closed-ish loop whose endpoints stay away from the bbox extremes, so
        // the start/end markers never clip the measured extent.
        val loop = listOf(
            RoutePoint(0.50f, 0.02f),
            RoutePoint(0.90f, 0.02f),
            RoutePoint(0.90f, 0.40f),
            RoutePoint(0.10f, 0.40f),
            RoutePoint(0.10f, 0.06f),
            RoutePoint(0.45f, 0.05f),
        )
        val plan = plan().copy(route = RouteData(loop))
        val spans = plan.route.let { points ->
            val xs = points.points.map { it.x }
            val ys = points.points.map { it.y }
            (xs.max() - xs.min()) to (ys.max() - ys.min())
        }

        val bitmap = checkNotNull(renderer.render(plan, plan.localDate, 1080, 1350))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()

        val lime = ArtColor.LIME.argb
        val xs = pixels.withIndex().filter { it.value == lime }.map { it.index % bitmap.width }
        val ys = pixels.withIndex().filter { it.value == lime }.map { it.index / bitmap.width }
        val drawnAspect = (xs.max() - xs.min()).toFloat() / (ys.max() - ys.min()).toFloat()
        val routeAspect = (spans.first / spans.second).toFloat()

        assertThat(drawnAspect).isCloseTo(routeAspect, withinPercentage(5.0))
    }

    @Test
    fun `renders with the recipe colors and markers`() {
        val recipe = ArtRecipe(
            extras = listOf(StoryExtra.DATE, StoryExtra.AVG_HR),
            font = ArtFont.ANTON,
            textColor = ArtColor.WHITE,
            routeColor = ArtColor.LIME,
        )
        val renderer = StoryArtRenderer(
            recipe = recipe,
            typeface = recipe.font.typeface(context),
            logoBitmap = StoryBranding.logo(context),
        )
        val bitmap = checkNotNull(renderer.render(plan(), plan().localDate, 1080, 1350))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()

        assertThat(pixels.count { it == ArtColor.LIME.argb }).isGreaterThan(0)
        assertThat(pixels.count { it == ArtColor.CORAL.argb }).isGreaterThan(0)
        assertThat(pixels.count { it == ArtColor.WHITE.argb }).isGreaterThan(0)
    }

    @Test
    fun `square card renders with one line of pace and time`() {
        val square = StoryArtRenderer(
            recipe = ArtRecipe(format = ArtFormat.SQUARE, font = ArtFont.OSWALD),
            typeface = ArtFont.OSWALD.typeface(context),
        )
        val bitmap = checkNotNull(square.render(plan(), plan().localDate, 1080, 1080))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()

        assertThat(pixels.count { it == ArtColor.LIME.argb }).isGreaterThan(0)
        assertThat(pixels.count { it == ArtColor.WHITE.argb }).isGreaterThan(0)
    }

    private fun plan(): StoryPlan {
        val samples = (0..5).map { km ->
            ActivitySample(
                offsetSec = km * 360,
                latitude = -5.790 + km * 0.009,
                longitude = -35.210 + if (km % 2 == 0) 0.0 else 0.002,
                heartRate = 150,
                cadenceSpm = 170,
            )
        }
        val activity = ImportedActivity(
            source = ActivitySource.GPX,
            sport = Sport.RUNNING,
            startTime = Instant.parse("2026-10-24T12:00:00Z"),
            durationSec = 1_800,
            distanceM = 5_000.0,
            calories = 310,
            averageHeartRate = 150,
            maxHeartRate = 170,
            averagePace = Pace.fromSecondsPerKm(360),
            averageCadenceSpm = 170,
            elevationGainM = 24.0,
            samples = samples,
        )
        return checkNotNull(StoryPlanCalculator.calculate(activity))
            .copy(localDate = LocalDate.parse("2026-10-24"))
    }

    private fun withinPercentage(percentage: Double): org.assertj.core.data.Percentage =
        org.assertj.core.data.Percentage.withPercentage(percentage)

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        )
    }
}
