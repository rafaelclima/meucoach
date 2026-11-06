package com.meucoach.app.ui.checkin

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
class CheckInScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun checkInDark() {
        capture(darkTheme = true, file = "src/test/screenshots/checkin-dark.png")
    }

    @Test
    fun checkInLight() {
        capture(darkTheme = false, file = "src/test/screenshots/checkin-light.png")
    }

    private fun capture(darkTheme: Boolean, file: String) {
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                CheckInContent(
                    state = CheckInUiState(
                        isLoading = false,
                        date = java.time.LocalDate.of(2026, 11, 6),
                        form = CheckInForm(sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
                    ),
                    onUpdate = {},
                    onSave = {},
                    onBack = {},
                )
            }
        }

        compose.onRoot().captureRoboImage(file)
    }
}
