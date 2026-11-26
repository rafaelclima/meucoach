package com.meucoach.app.ui.activitydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
class ActivityComparisonScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun comparisonDark() {
        render(darkTheme = true, comparison = ActivityDetailTestData.linkedComparison())
        compose.onRoot().captureRoboImage("src/test/screenshots/activity-comparison-dark.png")
    }

    @Test
    fun comparisonLight() {
        render(darkTheme = false, comparison = ActivityDetailTestData.linkedComparison())
        compose.onRoot().captureRoboImage("src/test/screenshots/activity-comparison-light.png")
    }

    @Test
    fun freeRunDark() {
        render(darkTheme = true, comparison = ActivityDetailTestData.freeRunComparison())
        compose.onRoot().captureRoboImage("src/test/screenshots/activity-comparison-free-dark.png")
    }

    @Test
    fun comparisonNarrow200Dark() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                McTheme(darkTheme = true) {
                    Box(
                        modifier = Modifier
                            .background(McTheme.colors.background)
                            .padding(20.dp),
                    ) {
                        ActivityComparisonCard(ActivityDetailTestData.linkedComparison())
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/activity-comparison-narrow-200-dark.png")
    }

    private fun render(darkTheme: Boolean, comparison: com.meucoach.core.domain.feedback.SessionComparison) {
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                Box(
                    modifier = Modifier
                        .background(McTheme.colors.background)
                        .padding(20.dp),
                ) {
                    ActivityComparisonCard(comparison)
                }
            }
        }
    }
}
