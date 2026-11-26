package com.meucoach.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.app.destinations
import com.meucoach.core.designsystem.component.McBottomBar
import com.meucoach.core.designsystem.component.McBottomBarItem
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
class BottomBarScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val items = destinations.map { destination ->
        McBottomBarItem(
            route = destination.route,
            label = destination.label,
            icon = destination.icon,
        )
    }

    @Test
    fun bottomBarDark() {
        capture(darkTheme = true, file = "src/test/screenshots/bottom-bar-dark.png")
    }

    @Test
    fun bottomBarLight() {
        capture(darkTheme = false, file = "src/test/screenshots/bottom-bar-light.png")
    }

    @Test
    fun bottomBarNarrow200Dark() {
        capture(
            darkTheme = true,
            file = "src/test/screenshots/bottom-bar-narrow-200-dark.png",
            fontScale = 2f,
        )
    }

    @Test
    fun `bottom bar grows to fit two-line labels at 200 percent`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                McTheme(darkTheme = true) {
                    McBottomBar(
                        items = items,
                        selectedRoute = "coach",
                        onSelect = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("bottom-bar").assertHeightIsAtLeast(80.dp)
        destinations.forEach { destination ->
            compose.onNodeWithTag("bottom-bar-${destination.route}").assertExists()
        }
    }

    @Test
    fun `coach is selected and every tab is reachable`() {
        var selected: String? = null
        compose.setContent {
            McTheme(darkTheme = true) {
                McBottomBar(
                    items = items,
                    selectedRoute = "coach",
                    onSelect = { selected = it },
                )
            }
        }

        destinations.forEach { destination ->
            compose.onNodeWithTag("bottom-bar-${destination.route}").assertExists()
        }
        compose.onNodeWithTag("bottom-bar-coach").assertIsSelected()

        compose.onNodeWithTag("bottom-bar-today").performClick()
        assertThat(selected).isEqualTo("today")
    }

    private fun capture(darkTheme: Boolean, file: String, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale),
            ) {
                McTheme(darkTheme = darkTheme) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(McTheme.colors.background),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        McBottomBar(
                            items = items,
                            selectedRoute = "coach",
                            onSelect = {},
                        )
                    }
                }
            }
        }

        compose.onRoot().captureRoboImage(file)
    }
}
