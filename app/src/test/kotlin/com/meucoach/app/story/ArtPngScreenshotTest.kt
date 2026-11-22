package com.meucoach.app.story

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.story.ArtColor
import com.meucoach.core.domain.story.ArtFont
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryPlanCalculator
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtPngScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun artPng4x5Default() {
        capture(recipe = ArtRecipe(), name = "art-png-4x5-default.png")
    }

    @Test
    fun artPng1x1Default() {
        capture(recipe = ArtRecipe(format = ArtFormat.SQUARE), name = "art-png-1x1-default.png")
    }

    @Test
    fun artPng4x5Customized() {
        val recipe = ArtRecipe(
            extras = listOf(StoryExtra.DATE, StoryExtra.AVG_HR),
            font = ArtFont.ANTON,
            textColor = ArtColor.WHITE,
            routeColor = ArtColor.CORAL,
        )
        capture(recipe = recipe, name = "art-png-4x5-customized.png")
    }

    /** The card floats over the dark checkerboard, the same preview the Studio shows. */
    private fun capture(recipe: ArtRecipe, name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val plan = fiveKPlan()
        val bitmap = checkNotNull(
            StoryArtRenderer(recipe, recipe.font.typeface(context), StoryBranding.logo(context))
                .render(plan, plan.localDate, recipe.format.width, recipe.format.height),
        )
        compose.setContent {
            McTheme(darkTheme = true) {
                Box(modifier = Modifier.fillMaxSize()) {
                    DarkCheckerboard()
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Arte transparente da rota",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name")
        bitmap.recycle()
    }

    @Composable
    private fun DarkCheckerboard() {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cell = 24.dp.toPx()
            val columns = ceil(size.width / cell).toInt()
            val rows = ceil(size.height / cell).toInt()
            for (row in 0 until rows) {
                for (column in 0 until columns) {
                    drawRect(
                        color = if ((row + column) % 2 == 0) Color(0xFF0E1013) else Color(0xFF16191E),
                        topLeft = Offset(column * cell, row * cell),
                        size = Size(cell, cell),
                    )
                }
            }
        }
    }

    private fun fiveKPlan(): StoryPlan {
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
}
