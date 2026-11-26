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
import com.meucoach.app.story.SyntheticArtPhoto
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtColor
import com.meucoach.core.domain.story.ArtFont
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtPhotoRef
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryMetricCatalog
import com.meucoach.core.domain.story.StoryPlanCalculator
import com.meucoach.core.domain.story.StoryPreset
import com.meucoach.core.domain.story.StoryRecipe
import com.meucoach.core.domain.story.StoryRouteStroke
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
    fun storyStudioRouteStrokesDark() {
        compose.setContent {
            McTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(McTheme.colors.background),
                ) {
                    StoryStudioContent(
                        state = StoryStudioUiState(
                            recipe = StoryRecipe(routeStroke = StoryRouteStroke.DASHED),
                            plan = plan(),
                            extras = options(),
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
                    )
                }
            }
        }
        compose.onNodeWithTag("story-route-stroke-dotted").performScrollTo()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("story-preview-loading").fetchSemanticsNodes().isEmpty()
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/story-studio-route-strokes-dark.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun videoStudioNarrow200Dark() {
        captureVideo(name = "story-studio-video-narrow-200-dark.png", fontScale = 2f, darkTheme = true)
    }

    @Test
    fun artStudioTabDark() {
        captureArtTab(name = "art-studio-tab-dark.png", fontScale = 1f, darkTheme = true)
    }

    @Test
    fun artStudioTab9x16Dark() {
        captureArtTab(
            name = "art-studio-tab-9x16-dark.png",
            fontScale = 1f,
            darkTheme = true,
            format = ArtFormat.PORTRAIT_9_16,
        )
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

    @Test
    fun artStudioTabPhotoDark() {
        captureArtTabPhoto(name = "art-studio-tab-photo-dark.png", fontScale = 1f, darkTheme = true)
    }

    @Test
    fun artStudioTabPhotoLight() {
        captureArtTabPhoto(name = "art-studio-tab-photo-light.png", fontScale = 1f, darkTheme = false)
    }

    @Test
    fun artStudioTabPhoto9x16Dark() {
        captureArtTabPhoto(
            name = "art-studio-tab-photo-9x16-dark.png",
            fontScale = 1f,
            darkTheme = true,
            format = ArtFormat.PORTRAIT_9_16,
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun artStudioTabPhotoNarrow200Dark() {
        captureArtTabPhoto(
            name = "art-studio-tab-photo-narrow-200-dark.png",
            fontScale = 2f,
            darkTheme = true,
        )
    }

    private fun captureVideo(name: String, fontScale: Float, darkTheme: Boolean) {
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
                                plan = plan(),
                                extras = options(),
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
                        )
                    }
                }
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("story-preview-loading").fetchSemanticsNodes().isEmpty()
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name")
    }

    private fun captureArtTab(
        name: String,
        fontScale: Float,
        darkTheme: Boolean,
        sectionTag: String? = null,
        format: ArtFormat = ArtFormat.PORTRAIT_4_5,
    ) {
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
                                    format = format,
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

    private fun captureArtTabPhoto(
        name: String,
        fontScale: Float,
        darkTheme: Boolean,
        format: ArtFormat = ArtFormat.PORTRAIT_4_5,
    ) {
        val photoRef = ArtPhotoRef(
            photoId = "123e4567-e89b-12d3-a456-426614174000",
            activityId = "activity-1",
        )
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
                                    format = format,
                                    extras = listOf(StoryExtra.DATE, StoryExtra.AVG_HR),
                                    font = ArtFont.ANTON,
                                    routeColor = ArtColor.CORAL,
                                    background = ArtBackgroundMode.PHOTO,
                                    photo = photoRef,
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
                            artActions = ArtStudioActions(
                                onLoadPhoto = { _, requestFormat, _ ->
                                    SyntheticArtPhoto.landscape(
                                        requestFormat.width,
                                        requestFormat.height,
                                    )
                                },
                            ),
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("story-tab-art").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("art-preview-ready-anton").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("art-photo-thumbnail").performScrollTo()
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
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("story-preview-loading").fetchSemanticsNodes().isEmpty()
        }
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
