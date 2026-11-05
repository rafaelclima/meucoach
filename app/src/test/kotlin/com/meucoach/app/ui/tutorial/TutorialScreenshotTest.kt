package com.meucoach.app.ui.tutorial

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
class TutorialScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tutorialPage1Dark() {
        capture(darkTheme = true, page = 0, file = "src/test/screenshots/tutorial-dark.png")
    }

    @Test
    fun tutorialPage1Light() {
        capture(darkTheme = false, page = 0, file = "src/test/screenshots/tutorial-light.png")
    }

    @Test
    fun tutorialPage2Dark() {
        capture(darkTheme = true, page = 1, file = "src/test/screenshots/tutorial-2-dark.png")
    }

    @Test
    fun tutorialPage2Light() {
        capture(darkTheme = false, page = 1, file = "src/test/screenshots/tutorial-2-light.png")
    }

    @Test
    fun tutorialPage3Dark() {
        capture(darkTheme = true, page = 2, file = "src/test/screenshots/tutorial-3-dark.png")
    }

    @Test
    fun tutorialPage3Light() {
        capture(darkTheme = false, page = 2, file = "src/test/screenshots/tutorial-3-light.png")
    }

    @Test
    fun tutorialPage4Dark() {
        capture(darkTheme = true, page = 3, file = "src/test/screenshots/tutorial-4-dark.png")
    }

    @Test
    fun tutorialPage4Light() {
        capture(darkTheme = false, page = 3, file = "src/test/screenshots/tutorial-4-light.png")
    }

    private fun capture(darkTheme: Boolean, page: Int, file: String) {
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                TutorialContent(onFinish = {}, onDismiss = {}, initialPage = page)
            }
        }

        compose.onRoot().captureRoboImage(file)
    }
}
