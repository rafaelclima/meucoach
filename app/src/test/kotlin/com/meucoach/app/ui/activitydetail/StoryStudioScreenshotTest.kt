package com.meucoach.app.ui.activitydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryMetricCatalog
import com.meucoach.core.domain.story.StoryPlanCalculator
import com.meucoach.core.domain.story.StoryPreset
import com.meucoach.core.domain.story.StoryRecipe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoryStudioScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun storyStudioEssentialDark() {
        capture(darkTheme = true) {
            StoryStudioUiState(recipe = StoryRecipe(), plan = plan(), extras = options())
        }
    }

    @Test
    fun storyStudioTwoExtrasDark() {
        capture(darkTheme = true) {
            StoryStudioUiState(
                recipe = StoryRecipe(
                    preset = StoryPreset.CUSTOM,
                    extras = listOf(StoryExtra.AVG_HR, StoryExtra.CADENCE),
                ),
                plan = plan(),
                extras = options(),
            )
        }
    }

    @Test
    fun storyStudioEssentialLight() {
        capture(darkTheme = false) {
            StoryStudioUiState(recipe = StoryRecipe(), plan = plan(), extras = options())
        }
    }

    private fun capture(darkTheme: Boolean, state: () -> StoryStudioUiState) {
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(McTheme.colors.background),
                ) {
                    StoryStudioContent(
                        state = state(),
                        avatar = null,
                        introSeen = true,
                        onClose = {},
                        onDismissIntro = {},
                        onPreset = {},
                        onToggleExtra = {},
                        onDuration = {},
                        onMapStyle = {},
                        onSaveAsDefault = {},
                        onReset = {},
                        onGenerate = {},
                    )
                }
            }
        }

        val name = if (darkTheme) "dark" else "light"
        val extras = state().recipe.extras.size
        compose.onRoot().captureRoboImage("src/test/screenshots/story-studio-$extras-extras-$name.png")
    }

    private fun options(): List<StoryExtraOption> = StoryExtra.entries.map { extra ->
        StoryExtraOption(
            extra = extra,
            label = StoryMetricCatalog.label(extra),
            isAvailable = true,
        )
    }

    private fun plan() = StoryPlanCalculator.calculate(ActivityDetailTestData.activity().imported)
}
