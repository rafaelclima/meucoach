package com.meucoach.app.ui.data

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
class DataManagementScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun dataManagementDark() {
        capture(darkTheme = true, file = "src/test/screenshots/data-management-dark.png")
    }

    @Test
    fun dataManagementLight() {
        capture(darkTheme = false, file = "src/test/screenshots/data-management-light.png")
    }

    private fun capture(darkTheme: Boolean, file: String) {
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                DataManagementContent(
                    state = DataManagementUiState(),
                    onExport = {},
                    onDeleteAll = {},
                    onBack = {},
                )
            }
        }

        compose.onRoot().captureRoboImage(file)
    }
}
