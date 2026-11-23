package com.meucoach.app.ui.activitydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.story.ArtColor
import com.meucoach.core.domain.story.ArtFont
import com.meucoach.core.domain.story.ArtRecipe
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

    @Test
    fun artStudioTabDark() {
        captureArtTab(name = "art-studio-tab-dark.png", fontScale = 1f, darkTheme = true)
    }

    @Test
    fun artStudioTabLight() {
        captureArtTab(name = "art-studio-tab-light.png", fontScale = 1f, darkTheme = false)
    }

    @Test
    fun artStudioColorsLight() {
        captureArtTab(
            name = "art-studio-colors-light.png",
            fontScale = 1f,
            darkTheme = false,
            sectionTag = "art-colors",
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun artStudioTabNarrow200Dark() {
        captureArtTab(name = "art-studio-tab-narrow-200-dark.png", fontScale = 2f, darkTheme = true)
    }

    private fun captureArtTab(name: String, fontScale: Float, darkTheme: Boolean, sectionTag: String? = null) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale),
            ) {
                McTheme(darkTheme = darkTheme) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(McTheme.colors.background),
                    ) {
                        StoryStudioContent(
                            state = StoryStudioUiState(
                                recipe = StoryRecipe(),
                                plan = checkNotNull(plan())
                                    .copy(localDate = java.time.LocalDate.parse("2026-10-24")),
                                extras = options(),
                                artRecipe = ArtRecipe(
                                    extras = listOf(StoryExtra.DATE, StoryExtra.AVG_HR),
                                    font = ArtFont.ANTON,
                                    routeColor = ArtColor.CORAL,
                                ),
                            ),
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
                            artActions = ArtStudioActions(),
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("story-tab-art").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("art-preview-ready-anton").fetchSemanticsNodes().isNotEmpty()
        }
        if (sectionTag != null) {
            compose.onNodeWithTag(sectionTag).performScrollTo()
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name")
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
