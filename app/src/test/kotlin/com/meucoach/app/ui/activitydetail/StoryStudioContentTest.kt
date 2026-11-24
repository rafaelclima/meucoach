package com.meucoach.app.ui.activitydetail

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.story.StoryDuration
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryMapStyle
import com.meucoach.core.domain.story.StoryMetricCatalog
import com.meucoach.core.domain.story.StoryPlanCalculator
import com.meucoach.core.domain.story.StoryPreset
import com.meucoach.core.domain.story.StoryRecipe
import com.meucoach.core.domain.story.StoryRouteStroke
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
class StoryStudioContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shows the fixed essentials and the extra counter`() {
        setContent()

        compose.onNodeWithText("OBRIGATÓRIAS").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Distância · Ritmo médio · Tempo total").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("EXTRAS (0/2)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the fixed preview remains visible while editing the recipe`() {
        setContent()

        compose.onNodeWithTag("story-preview").assertIsDisplayed()
        compose.onNodeWithTag("story-generate").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("story-preview").assertIsDisplayed()
        compose.onNodeWithTag("story-map-meucoach_dark").performScrollTo()
        compose.onNodeWithTag("story-preview").assertIsDisplayed()
    }

    @Test
    fun `hides the tab segment while scrolled and restores it at the top`() {
        setContent()

        compose.onNodeWithTag("story-tab-segment").assertIsDisplayed()

        compose.onNodeWithTag("story-generate").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("story-tab-segment").assertDoesNotExist()

        // Back to the very top with overscroll-safe gestures on the scrollable
        // column itself: a no-arg swipe starts at the node top, which must be
        // inside the scrollable content (the root top is the fixed chrome).
        // The loop stops as soon as the segment is back instead of guessing
        // the distance.
        var attempts = 0
        while (
            compose.onAllNodesWithTag("story-tab-segment").fetchSemanticsNodes().isEmpty() &&
            attempts < 30
        ) {
            compose.onNodeWithTag("story-video-scroll").performTouchInput { swipeDown() }
            compose.waitForIdle()
            attempts++
        }
        compose.onNodeWithTag("story-tab-segment").assertIsDisplayed()
    }

    @Test
    fun `disables an extra without data and explains why`() {
        setContent(extraOptions = extraOptions(unavailable = setOf(StoryExtra.AVG_HR)))

        compose.onNodeWithTag("story-extra-avg_hr").performScrollTo()
        compose.onNodeWithTag("story-extra-avg_hr").assertIsNotEnabled()
        compose.onNodeWithText("FC média · sem FC").assertIsDisplayed()
    }

    @Test
    fun `toggles an available extra`() {
        var toggled: StoryExtra? = null
        setContent(onToggleExtra = { toggled = it })

        compose.onNodeWithTag("story-extra-cadence").performScrollTo().performClick()

        assertThat(toggled).isEqualTo(StoryExtra.CADENCE)
    }

    @Test
    fun `blocks a third extra`() {
        setContent(
            recipe = StoryRecipe(extras = listOf(StoryExtra.AVG_HR, StoryExtra.CADENCE)),
        )

        compose.onNodeWithTag("story-extra-date").performScrollTo()
        compose.onNodeWithTag("story-extra-date").assertIsNotEnabled()
        compose.onNodeWithText("EXTRAS (2/2)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `keeps a selected extra removable even when its data disappeared`() {
        var toggled: StoryExtra? = null
        setContent(
            recipe = StoryRecipe(extras = listOf(StoryExtra.AVG_HR)),
            extraOptions = extraOptions(unavailable = setOf(StoryExtra.AVG_HR)),
            onToggleExtra = { toggled = it },
        )

        compose.onNodeWithTag("story-extra-avg_hr").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("story-extra-avg_hr").performClick()

        assertThat(toggled).isEqualTo(StoryExtra.AVG_HR)
    }

    @Test
    fun `selects a preset`() {
        var preset: StoryPreset? = null
        setContent(onPreset = { preset = it })

        compose.onNodeWithTag("story-preset-physiological").performScrollTo().performClick()

        assertThat(preset).isEqualTo(StoryPreset.PHYSIOLOGICAL)
    }

    @Test
    fun `selects duration map style and route stroke`() {
        var duration: StoryDuration? = null
        var style: StoryMapStyle? = null
        var stroke: StoryRouteStroke? = null
        setContent(
            onDuration = { duration = it },
            onMapStyle = { style = it },
            onRouteStroke = { stroke = it },
        )

        compose.onNodeWithTag("story-duration-sec_45").performScrollTo().performClick()
        compose.onNodeWithTag("story-map-meucoach_light").performScrollTo().performClick()
        compose.onNodeWithTag("story-route-stroke-dotted").performScrollTo().performClick()

        assertThat(duration).isEqualTo(StoryDuration.SEC_45)
        assertThat(style).isEqualTo(StoryMapStyle.MEUCOACH_LIGHT)
        assertThat(stroke).isEqualTo(StoryRouteStroke.DOTTED)
    }

    @Test
    fun `exposes the selected route stroke to talkback`() {
        setContent(recipe = StoryRecipe(routeStroke = StoryRouteStroke.DASHED))

        compose.onNodeWithTag("story-route-stroke-dashed").performScrollTo()
        compose.onNodeWithTag("story-route-stroke-dashed").assertIsSelected()
        compose.onNode(hasContentDescription("Tracejado, selecionado")).assertExists()
    }

    @Test
    fun `shows the map options without satellite`() {
        setContent()

        compose.onNodeWithTag("story-map-meucoach_dark").performScrollTo()
        compose.onNodeWithText("Escuro").assertIsDisplayed()
        compose.onNodeWithText("Claro").assertIsDisplayed()
        compose.onNodeWithText("Terreno").assertIsDisplayed()
        compose.onNodeWithText("Satélite").assertDoesNotExist()
    }

    @Test
    fun `shows the first-run map notice and dismisses it`() {
        var dismissed = false
        setContent(introSeen = false, onDismissIntro = { dismissed = true })

        compose.onNodeWithTag("story-intro-dismiss").performScrollTo().performClick()

        assertThat(dismissed).isTrue()
    }

    @Test
    fun `explains the terrain download and the route-only preview`() {
        setContent(recipe = StoryRecipe(mapStyle = StoryMapStyle.TERRAIN))

        compose.onNodeWithText("Relevo do terreno via Mapzen · é baixado na primeira vez.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("A prévia desenha a rota; o mapa escolhido entra no vídeo.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `toggles save as default and resets the recipe`() {
        var save: Boolean? = null
        var reset = false
        setContent(onSaveAsDefault = { save = it }, onReset = { reset = true })

        compose.onNodeWithTag("story-save-default").performScrollTo().performClick()
        compose.onNodeWithTag("story-reset-recipe").performScrollTo().performClick()

        assertThat(save).isFalse()
        assertThat(reset).isTrue()
    }

    @Test
    fun `generates when the activity has a plan`() {
        var generated = false
        setContent(onGenerate = { generated = true })

        compose.onNodeWithTag("story-generate").performScrollTo().performClick()

        assertThat(generated).isTrue()
    }

    @Test
    fun `exposes headings selection and toggle state to TalkBack`() {
        setContent(recipe = StoryRecipe(extras = listOf(StoryExtra.AVG_HR)))

        compose.onNodeWithText("EXTRAS (1/2)").performScrollTo()
        val extras = compose.onNodeWithText("EXTRAS (1/2)").fetchSemanticsNode()
        val map = compose.onNodeWithText("MAPA").performScrollTo().fetchSemanticsNode()
        assertThat(extras.config.getOrElse(SemanticsProperties.Heading) { null }).isEqualTo(Unit)
        assertThat(map.config.getOrElse(SemanticsProperties.Heading) { null }).isEqualTo(Unit)
        compose.onNodeWithTag("story-extra-avg_hr").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("story-extra-cadence").performScrollTo().assertIsNotSelected()
        compose.onNodeWithTag("story-save-default").performScrollTo().assertIsOn()
    }

    @Test
    fun `describes the preview frame to TalkBack`() {
        setContent()

        compose.waitUntil(timeoutMillis = 10_000) {
            compose
                .onAllNodes(hasContentDescription("Prévia do story — Abertura"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNode(hasContentDescription("Prévia do story — Abertura")).assertIsDisplayed()
    }

    @Test
    fun `keeps story controls at forty eight dp touch targets`() {
        setContent()

        compose.onNodeWithTag("story-studio-close").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("story-preset-essential").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("story-extra-cadence").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("story-duration-auto").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("story-map-meucoach_dark").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("story-save-default").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("story-generate").performScrollTo().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `disables generation without a gps plan`() {
        setContent(plan = null)

        compose.onNodeWithTag("story-generate").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Esta atividade não tem rota GPS suficiente para um story.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun setContent(
        recipe: StoryRecipe = StoryRecipe(),
        extraOptions: List<StoryExtraOption> = extraOptions(),
        plan: com.meucoach.core.domain.story.StoryPlan? = plan(),
        introSeen: Boolean = true,
        onDismissIntro: () -> Unit = {},
        onPreset: (StoryPreset) -> Unit = {},
        onToggleExtra: (StoryExtra) -> Unit = {},
        onDuration: (StoryDuration) -> Unit = {},
        onMapStyle: (StoryMapStyle) -> Unit = {},
        onRouteStroke: (StoryRouteStroke) -> Unit = {},
        onSaveAsDefault: (Boolean) -> Unit = {},
        onReset: () -> Unit = {},
        onGenerate: () -> Unit = {},
    ) {
        compose.setContent {
            McTheme(darkTheme = true) {
                StoryStudioContent(
                    state = StoryStudioUiState(
                        recipe = recipe,
                        plan = plan,
                        extras = extraOptions,
                        saveAsDefault = true,
                    ),
                    avatar = null,
                    introSeen = introSeen,
                    onClose = {},
                    onDismissIntro = onDismissIntro,
                    onPreset = onPreset,
                    onToggleExtra = onToggleExtra,
                    onDuration = onDuration,
                    onMapStyle = onMapStyle,
                    onRouteStroke = onRouteStroke,
                    onSaveAsDefault = onSaveAsDefault,
                    onReset = onReset,
                    onGenerate = onGenerate,
                )
            }
        }
    }

    private fun extraOptions(unavailable: Set<StoryExtra> = emptySet()): List<StoryExtraOption> =
        StoryExtra.entries.map { extra ->
            if (extra in unavailable) {
                StoryExtraOption(
                    extra = extra,
                    label = StoryMetricCatalog.label(extra),
                    isAvailable = false,
                    reason = "sem FC",
                )
            } else {
                StoryExtraOption(
                    extra = extra,
                    label = StoryMetricCatalog.label(extra),
                    isAvailable = true,
                )
            }
        }

    private fun plan() = StoryPlanCalculator.calculate(ActivityDetailTestData.activity().imported)
}
