package com.meucoach.app.story

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryPlanCalculator
import com.meucoach.core.domain.story.StoryRecipe
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Sport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoryPosterScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun storyPoster5kDark() {
        capture(darkTheme = true, plan = fiveKPlan(), name = "story-poster-5k-dark.png")
    }

    @Test
    fun storyPoster5kLight() {
        capture(darkTheme = false, plan = fiveKPlan(), name = "story-poster-5k-light.png")
    }

    @Test
    fun storyPoster10kDark() {
        capture(darkTheme = true, plan = tenKPlan(), name = "story-poster-10k-dark.png")
    }

    private fun capture(darkTheme: Boolean, plan: StoryPlan, name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logo = StoryBranding.logo(context)
        var poster by mutableStateOf<Bitmap?>(null)
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                val colors = McTheme.colors
                val palette = remember(colors) { colors.toStoryPalette() }
                LaunchedEffect(Unit) {
                    poster = StoryPosterRenderer(palette, Typeface.DEFAULT, logo)
                        .render(plan, StoryRecipe())
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(McTheme.colors.background),
                    contentAlignment = Alignment.Center,
                ) {
                    poster?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Pôster 4:5 do story",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name")
    }

    private fun fiveKPlan(): StoryPlan = syntheticPlan(kilometers = 5, durationSec = 1_800)

    private fun tenKPlan(): StoryPlan = syntheticPlan(kilometers = 10, durationSec = 3_600)

    /**
     * Synthetic route: no real coordinates or health data. The latitude step
     * and the lateral wobble are tuned so the planar distance measured from
     * the samples lands on the requested kilometrage.
     */
    private fun syntheticPlan(kilometers: Int, durationSec: Int): StoryPlan {
        val samples = (0..kilometers).map { km ->
            ActivitySample(
                offsetSec = km * durationSec / kilometers,
                latitude = -5.790 + km * 0.0085,
                longitude = -35.210 + if (km % 2 == 0) 0.0 else 0.003,
                heartRate = 145 + (km % 3) * 5,
                cadenceSpm = 170,
                altitudeM = 12.0 + km,
            )
        }
        val activity = ImportedActivity(
            source = ActivitySource.GPX,
            sport = Sport.RUNNING,
            startTime = Instant.parse("2026-10-24T09:00:00Z"),
            durationSec = durationSec,
            distanceM = kilometers * 1_000.0,
            calories = 620,
            averageHeartRate = 150,
            maxHeartRate = 168,
            averageCadenceSpm = 170,
            elevationGainM = 35.0,
            samples = samples,
        )
        return checkNotNull(StoryPlanCalculator.calculate(activity))
            .copy(localDate = LocalDate.parse("2026-10-24"))
    }
}
