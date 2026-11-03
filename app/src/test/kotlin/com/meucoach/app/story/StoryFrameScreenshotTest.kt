package com.meucoach.app.story

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.app.ui.activitydetail.ActivityDetailTestData
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
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
class StoryFrameScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun storyFrameDark() {
        val activity = ActivityDetailTestData.state().activity!!.imported
        val plan = StoryPlanCalculator.calculate(activity, targetDurationSec = 30)
        assertThat(plan).isNotNull
        val storyPlan = plan!!
        var frame by mutableStateOf<Bitmap?>(null)
        compose.setContent {
            McTheme(darkTheme = true) {
                val colors = McTheme.colors
                val palette = remember(colors) { colors.toStoryPalette() }
                LaunchedEffect(Unit) {
                    frame = StoryFrameRenderer(palette, Typeface.DEFAULT)
                        .render(storyPlan, timeSec = 8f, width = 720, height = 1280)
                }
                frame?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Quadro do story",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/story-frame-dark.png")
    }

    @Test
    fun storyFrameWithLogoDark() {
        val activity = ActivityDetailTestData.state().activity!!.imported
        val storyPlan = checkNotNull(StoryPlanCalculator.calculate(activity, targetDurationSec = 30))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logo = checkNotNull(StoryBranding.logo(context)) { "The MeuCoach wordmark must ship with the app" }
        var frame by mutableStateOf<Bitmap?>(null)
        compose.setContent {
            McTheme(darkTheme = true) {
                val colors = McTheme.colors
                val palette = remember(colors) { colors.toStoryPalette() }
                LaunchedEffect(Unit) {
                    frame = StoryFrameRenderer(palette, Typeface.DEFAULT, logo)
                        .render(storyPlan, timeSec = 8f, width = 720, height = 1280)
                }
                frame?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Quadro do story com logo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/story-frame-logo-dark.png")
    }

    @Test
    fun storyFinalFrameWithLogoDark() {
        val activity = ActivityDetailTestData.state().activity!!.imported
        val storyPlan = checkNotNull(StoryPlanCalculator.calculate(activity, targetDurationSec = 30))
        val finalStart = storyPlan.segments.last { it.type == StorySegmentType.FINAL }.startSec
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logo = checkNotNull(StoryBranding.logo(context)) { "The MeuCoach wordmark must ship with the app" }
        var frame by mutableStateOf<Bitmap?>(null)
        compose.setContent {
            McTheme(darkTheme = true) {
                val colors = McTheme.colors
                val palette = remember(colors) { colors.toStoryPalette() }
                LaunchedEffect(Unit) {
                    frame = StoryFrameRenderer(palette, Typeface.DEFAULT, logo)
                        .render(storyPlan, timeSec = finalStart + 1f, width = 720, height = 1280)
                }
                frame?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Quadro final do story",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/story-final-dark.png")
    }

    @Test
    fun storyFrameOverMapDark() {
        val activity = ActivityDetailTestData.state().activity!!.imported
        val storyPlan = checkNotNull(StoryPlanCalculator.calculate(activity, targetDurationSec = 30))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logo = checkNotNull(StoryBranding.logo(context)) { "The MeuCoach wordmark must ship with the app" }
        val map = fakeMap()
        var frame by mutableStateOf<Bitmap?>(null)
        compose.setContent {
            McTheme(darkTheme = true) {
                val colors = McTheme.colors
                val palette = remember(colors) { colors.toStoryPalette() }
                LaunchedEffect(Unit) {
                    frame = StoryFrameRenderer(palette, Typeface.DEFAULT, logo, map)
                        .render(storyPlan, timeSec = 8f, width = 720, height = 1280)
                }
                frame?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Quadro do story sobre o mapa",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/story-map-dark.png")
    }

    @Test
    fun storyFinalFrameOverMapDark() {
        val activity = ActivityDetailTestData.state().activity!!.imported
        val storyPlan = checkNotNull(StoryPlanCalculator.calculate(activity, targetDurationSec = 30))
        val finalStart = storyPlan.segments.last { it.type == StorySegmentType.FINAL }.startSec
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logo = checkNotNull(StoryBranding.logo(context)) { "The MeuCoach wordmark must ship with the app" }
        val map = fakeMap()
        var frame by mutableStateOf<Bitmap?>(null)
        compose.setContent {
            McTheme(darkTheme = true) {
                val colors = McTheme.colors
                val palette = remember(colors) { colors.toStoryPalette() }
                LaunchedEffect(Unit) {
                    frame = StoryFrameRenderer(palette, Typeface.DEFAULT, logo, map)
                        .render(storyPlan, timeSec = finalStart + 1f, width = 720, height = 1280)
                }
                frame?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Quadro final do story sobre o mapa",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/story-final-map-dark.png")
    }

    @Test
    fun storyCardWithVerdictDark() {
        val storyPlan = easyPlan()
        val cardStart = checkNotNull(
            storyPlan.segments.firstOrNull {
                it.type == StorySegmentType.CARD && it.split?.km == 1
            },
        ).startSec
        val map = fakeMap()
        var frame by mutableStateOf<Bitmap?>(null)
        compose.setContent {
            McTheme(darkTheme = true) {
                val colors = McTheme.colors
                val palette = remember(colors) { colors.toStoryPalette() }
                LaunchedEffect(Unit) {
                    frame = StoryFrameRenderer(palette, Typeface.DEFAULT, null, map)
                        .render(storyPlan, timeSec = cardStart + 1f, width = 720, height = 1280)
                }
                frame?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Card do story com selo de veredito",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/story-card-verdict-dark.png")
    }

    @Test
    fun storyFinalWithVerdictDark() {
        val storyPlan = easyPlan()
        val finalStart = storyPlan.segments.last { it.type == StorySegmentType.FINAL }.startSec
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logo = checkNotNull(StoryBranding.logo(context)) { "The MeuCoach wordmark must ship with the app" }
        var frame by mutableStateOf<Bitmap?>(null)
        compose.setContent {
            McTheme(darkTheme = true) {
                val colors = McTheme.colors
                val palette = remember(colors) { colors.toStoryPalette() }
                LaunchedEffect(Unit) {
                    frame = StoryFrameRenderer(palette, Typeface.DEFAULT, logo)
                        .render(storyPlan, timeSec = finalStart + 1f, width = 720, height = 1280)
                }
                frame?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Frame final do story com veredito",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/story-final-verdict-dark.png")
    }

    /** Synthetic 2 km easy run: every sample sits inside Z2 (`LTHR` 180, bpm 150). */
    private fun easyPlan(): StoryPlan {
        val activity = ImportedActivity(
            source = ActivitySource.GPX,
            sport = Sport.RUNNING,
            startTime = Instant.parse("2026-10-24T12:00:00Z"),
            durationSec = 720,
            distanceM = 2_000.0,
            calories = 140,
            samples = listOf(
                ActivitySample(offsetSec = 0, latitude = -5.790, longitude = -35.210, heartRate = 150),
                ActivitySample(offsetSec = 360, latitude = -5.781, longitude = -35.210, heartRate = 150),
                ActivitySample(offsetSec = 720, latitude = -5.772, longitude = -35.210, heartRate = 150),
            ),
        )
        val session = PlannedSession(
            date = LocalDate.parse("2026-10-24"),
            type = WorkoutType.EASY,
            title = "Fácil 40 min",
            durationMin = 40,
            rationale = "Teste determinístico.",
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-FRIEL-2018"),
        )
        return checkNotNull(
            StoryPlanCalculator.calculate(
                activity = activity,
                lthrBpm = 180,
                session = session,
                nextSession = session.copy(
                    date = session.date.plusDays(1),
                    durationMin = 40,
                ),
            ),
        )
    }

    /**
     * Synthetic tiles: a street grid with a park and water block. Never real
     * location data (privacy rules in AGENTS.md §4).
     */
    private fun fakeMap(): Bitmap {
        val bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFE9E5DA.toInt())
        val street = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            strokeWidth = 10f
        }
        var x = 0f
        while (x < 720f) {
            canvas.drawLine(x, 0f, x, 1280f, street)
            x += 90f
        }
        var y = 0f
        while (y < 1280f) {
            canvas.drawLine(0f, y, 720f, y, street)
            y += 90f
        }
        canvas.drawRect(
            420f,
            380f,
            700f,
            720f,
            Paint().apply { color = 0xFFB7D3B0.toInt() },
        )
        canvas.drawRect(
            0f,
            900f,
            260f,
            1280f,
            Paint().apply { color = 0xFFA9C7DD.toInt() },
        )
        return bitmap
    }
}
