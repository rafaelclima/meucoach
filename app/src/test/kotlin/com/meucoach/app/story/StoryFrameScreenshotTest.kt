package com.meucoach.app.story

import android.graphics.Bitmap
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
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.app.ui.activitydetail.ActivityDetailTestData
import com.meucoach.core.designsystem.theme.McTheme
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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
}
