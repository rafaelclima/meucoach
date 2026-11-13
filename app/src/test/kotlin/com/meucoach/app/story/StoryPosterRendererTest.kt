package com.meucoach.app.story

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import com.meucoach.core.domain.story.RouteData
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryPlanCalculator
import com.meucoach.core.domain.story.StoryRecipe
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
class StoryPosterRendererTest {
    private val renderer = StoryPosterRenderer(
        palette = testPalette(),
        typeface = Typeface.DEFAULT,
    )

    @Test
    fun `renders a 1080 by 1350 poster with the palette`() {
        val bitmap = checkNotNull(renderer.render(plan()))

        assertThat(bitmap.width).isEqualTo(1080)
        assertThat(bitmap.height).isEqualTo(1350)
        assertThat(bitmap.config).isEqualTo(Bitmap.Config.ARGB_8888)
        assertThat(bitmap.getPixel(0, 0)).isEqualTo(BACKGROUND)

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertThat(pixels.count { it == ACCENT }).isGreaterThan(0)
        bitmap.recycle()
    }

    @Test
    fun `writes the poster as png`() {
        val output = ByteArrayOutputStream()

        assertThat(renderer.writePng(plan(), StoryRecipe(), output)).isTrue()

        val png = output.toByteArray()
        assertThat(png.size).isGreaterThan(1_000)
        assertThat(png.copyOfRange(0, PNG_SIGNATURE.size)).isEqualTo(PNG_SIGNATURE)
        val decoded = checkNotNull(BitmapFactory.decodeByteArray(png, 0, png.size))
        assertThat(decoded.width).isEqualTo(StoryPosterRenderer.WIDTH)
        assertThat(decoded.height).isEqualTo(StoryPosterRenderer.HEIGHT)
        decoded.recycle()
    }

    @Test
    fun `returns null without a route`() {
        val plan = plan().copy(route = RouteData(emptyList()))
        val output = ByteArrayOutputStream()

        assertThat(renderer.render(plan)).isNull()
        assertThat(renderer.writePng(plan, StoryRecipe(), output)).isFalse()
        assertThat(output.size()).isZero()
    }

    @Test
    fun `renders without a date when the plan has none`() {
        val bitmap = checkNotNull(renderer.render(plan().copy(localDate = null)))

        assertThat(bitmap.width).isEqualTo(1080)
        bitmap.recycle()
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

    private fun testPalette() = StoryPalette(
        background = BACKGROUND,
        surface = 0xFF15171C.toInt(),
        surfaceInset = 0xFF1D2027.toInt(),
        border = 0xFF313640.toInt(),
        textPrimary = 0xFFF1F4F8.toInt(),
        textSecondary = 0xFFB2BAC6.toInt(),
        textTertiary = 0xFF7D8795.toInt(),
        accent = ACCENT,
        coral = 0xFFFF6B5E.toInt(),
        onAccent = 0xFF0A0B0D.toInt(),
        mapCasing = 0xFF08090B.toInt(),
        mapBaseRoute = 0xFF4A545F.toInt(),
        zone1 = 0xFF6DD3FF.toInt(),
        zone2 = 0xFF8DE06A.toInt(),
        zone3 = 0xFFD8FF3E.toInt(),
        zone4 = 0xFFFFB547.toInt(),
        zone5 = 0xFFFF6B5E.toInt(),
    )

    private companion object {
        val BACKGROUND = 0xFF0A0B0D.toInt()
        val ACCENT = 0xFFD8FF3E.toInt()
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
