package com.meucoach.app.ui.activitydetail

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.story.ArtColor
import com.meucoach.core.domain.story.ArtFont
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.RouteData
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryMetricCatalog
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryPlanCalculator
import com.meucoach.core.domain.story.StoryRecipe
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtStudioContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the chrome segment switches between video and art`() {
        setContent()

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNodeWithTag("art-preview").assertIsDisplayed()
        compose.onNodeWithText("Personalize sua arte").assertIsDisplayed()
        compose.onNodeWithTag("story-generate").assertDoesNotExist()

        compose.onNodeWithTag("story-tab-video").performClick()

        compose.onNodeWithTag("story-generate").assertExists()
    }

    @Test
    fun `the summary line follows the art recipe`() {
        setContent(artRecipe = ArtRecipe(format = ArtFormat.SQUARE, font = ArtFont.ANTON))

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNodeWithText("1:1 · Anton · texto branco · traçado lima.").assertIsDisplayed()
    }

    @Test
    fun `selects the square format`() {
        var format: ArtFormat? = null
        setContent(onFormat = { format = it })

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-format-square").performScrollTo().performClick()

        assertThat(format).isEqualTo(ArtFormat.SQUARE)
    }

    @Test
    fun `toggles art extras up to two`() {
        val toggled = mutableListOf<StoryExtra>()
        setContent(onToggleExtra = { toggled.add(it) })

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-extra-date").performScrollTo().performClick()
        compose.onNodeWithTag("art-extra-avg_hr").performScrollTo().performClick()

        assertThat(toggled).containsExactly(StoryExtra.DATE, StoryExtra.AVG_HR)
    }

    @Test
    fun `blocks a third art extra`() {
        setContent(
            artRecipe = ArtRecipe(extras = listOf(StoryExtra.DATE, StoryExtra.AVG_HR)),
        )

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithText("EXTRAS (2/2)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("art-extra-cadence").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `disables an art extra without data and explains why`() {
        setContent(extraOptions = extraOptions(unavailable = setOf(StoryExtra.AVG_HR)))

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-extra-avg_hr").performScrollTo()
        compose.onNodeWithTag("art-extra-avg_hr").assertIsNotEnabled()
        compose.onNodeWithText("FC média · sem FC").assertIsDisplayed()
    }

    @Test
    fun `selects text and route colors`() {
        var text: ArtColor? = null
        var route: ArtColor? = null
        setContent(
            onTextColor = { text = it },
            onRouteColor = { route = it },
        )

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-text-color-cyan").performScrollTo().performClick()
        compose.onNodeWithTag("art-route-color-coral").performScrollTo().performClick()

        assertThat(text).isEqualTo(ArtColor.CYAN)
        assertThat(route).isEqualTo(ArtColor.CORAL)
    }

    @Test
    fun `exposes swatch state to talkback`() {
        setContent(
            artRecipe = ArtRecipe(
                textColor = ArtColor.CYAN,
                routeColor = ArtColor.LIME,
                font = ArtFont.INTER,
                format = ArtFormat.PORTRAIT_4_5,
            ),
        )

        compose.onNodeWithTag("story-tab-art").performClick()
        val node = compose.onNodeWithTag("art-text-color-cyan").performScrollTo().fetchSemanticsNode()

        assertThat(node.config.getOrElse(SemanticsProperties.Selected) { false }).isTrue()
        compose.onNode(hasContentDescription("Cor do texto: ciano, selecionada")).assertExists()
    }

    @Test
    fun `selects a font`() {
        var font: ArtFont? = null
        setContent(onFont = { font = it })

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-font-anton").performScrollTo().performClick()

        assertThat(font).isEqualTo(ArtFont.ANTON)
    }

    @Test
    fun `shows the single font preview of the selected family`() {
        setContent(artRecipe = ArtRecipe(font = ArtFont.BEBAS_NEUE))

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNodeWithTag("art-font-preview").performScrollTo().assertIsDisplayed()
        // One in the selected pill, one in the single preview.
        compose.onAllNodesWithText("Bebas Neue").fetchSemanticsNodes().size.let { count ->
            org.assertj.core.api.Assertions.assertThat(count).isEqualTo(2)
        }
    }

    @Test
    fun `keeps share and save disabled without a route`() {
        setContent(plan = plan().copy(route = RouteData(emptyList())))

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-share").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("art-save").assertIsNotEnabled()
        compose.onNodeWithText("Sem rota suficiente para a arte.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `enables the ctas with a route`() {
        setContent()

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-share").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("art-save").assertIsEnabled()
    }

    @Test
    fun `labels the transparent background and toggles the checker`() {
        setContent()

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithText("Fundo transparente").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("art-checker-light").performScrollTo().performClick()
        compose.onNodeWithTag("art-checker-dark").assertIsDisplayed()
    }

    @Test
    fun `describes the preview to talkback`() {
        setContent()

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNode(
            hasContentDescription("Prévia da arte 4:5, texto branco, traçado lima, fonte Inter, fundo transparente"),
        ).assertExists()
    }

    @Test
    fun `toggles art save as default and resets the recipe`() {
        var save: Boolean? = null
        var reset = false
        setContent(onArtSaveAsDefault = { save = it }, onArtReset = { reset = true })

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-save-default").performScrollTo().performClick()
        compose.onNodeWithTag("art-reset-recipe").performScrollTo().performClick()

        assertThat(save).isFalse()
        assertThat(reset).isTrue()
    }

    @Test
    fun `keeps art controls at forty eight dp touch targets`() {
        setContent()

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-format-square").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("art-extra-date").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("art-text-color-lime").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("art-font-anton").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("art-save-default").performScrollTo().assertHeightIsAtLeast(48.dp)
    }

    private fun setContent(
        artRecipe: ArtRecipe = ArtRecipe(),
        extraOptions: List<StoryExtraOption> = extraOptions(),
        plan: StoryPlan? = plan(),
        onFormat: (ArtFormat) -> Unit = {},
        onToggleExtra: (StoryExtra) -> Unit = {},
        onTextColor: (ArtColor) -> Unit = {},
        onRouteColor: (ArtColor) -> Unit = {},
        onFont: (ArtFont) -> Unit = {},
        onArtSaveAsDefault: (Boolean) -> Unit = {},
        onArtReset: () -> Unit = {},
    ) {
        compose.setContent {
            McTheme(darkTheme = true) {
                StoryStudioContent(
                    state = StoryStudioUiState(
                        recipe = StoryRecipe(),
                        plan = plan,
                        extras = extraOptions,
                        saveAsDefault = true,
                        artRecipe = artRecipe,
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
                        onFormat = onFormat,
                        onToggleExtra = onToggleExtra,
                        onTextColor = onTextColor,
                        onRouteColor = onRouteColor,
                        onFont = onFont,
                        onSaveAsDefault = onArtSaveAsDefault,
                        onReset = onArtReset,
                    ),
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

    private fun plan(): StoryPlan =
        checkNotNull(StoryPlanCalculator.calculate(ActivityDetailTestData.activity().imported))
}
