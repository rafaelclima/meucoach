package com.meucoach.app.ui.activitydetail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.core.designsystem.theme.McTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActivityDetailScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun activityDetailDark() {
        compose.setContent {
            McTheme(darkTheme = true) {
                ActivityDetailContent(state = ActivityDetailTestData.state(), onBack = {})
            }
        }

        compose.onRoot().captureRoboImage("src/test/screenshots/activity-detail-dark.png")
    }

    @Test
    fun activityDetailLight() {
        compose.setContent {
            McTheme(darkTheme = false) {
                ActivityDetailContent(state = ActivityDetailTestData.state(), onBack = {})
            }
        }

        compose.onRoot().captureRoboImage("src/test/screenshots/activity-detail-light.png")
    }
}
