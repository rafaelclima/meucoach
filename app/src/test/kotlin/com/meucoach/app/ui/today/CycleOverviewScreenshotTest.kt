package com.meucoach.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
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
class CycleOverviewScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cycleOverviewDark() {
        capture(darkTheme = true, file = "src/test/screenshots/cycle-overview-dark.png")
    }

    @Test
    fun cycleOverviewLight() {
        capture(darkTheme = false, file = "src/test/screenshots/cycle-overview-light.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun cycleOverviewNarrow200Dark() {
        capture(
            darkTheme = true,
            file = "src/test/screenshots/cycle-overview-narrow-200-dark.png",
            fontScale = 2f,
        )
    }

    @Test
    fun cycleOverviewNoRaceDark() {
        capture(
            darkTheme = true,
            file = "src/test/screenshots/cycle-overview-no-race-dark.png",
            state = CycleOverviewSheetState.Empty(),
        )
    }

    @Test
    fun cycleSummaryDark() {
        compose.setContent {
            McTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(McTheme.colors.surface),
                ) {
                    CycleSummaryRow(
                        overview = CycleOverviewFixtures.overview(),
                        onClick = {},
                    )
                }
            }
        }

        compose.onRoot().captureRoboImage("src/test/screenshots/today-cycle-summary-dark.png")
    }

    private fun capture(
        darkTheme: Boolean,
        file: String,
        fontScale: Float = 1f,
        state: CycleOverviewSheetState = CycleOverviewSheetState.Ready(CycleOverviewFixtures.overview()),
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                McTheme(darkTheme = darkTheme) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(McTheme.colors.surface),
                    ) {
                        CycleOverviewContent(state = state, onClose = {})
                    }
                }
            }
        }

        compose.onRoot().captureRoboImage(file)
    }
}
