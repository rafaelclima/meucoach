package com.meucoach.app.ui.activitydetail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
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
        compose.onNodeWithTag("art-preview-empty").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("art-no-route").performScrollTo().assertIsDisplayed()
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

    @Test
    fun `exporting keeps the ctas busy and disabled`() {
        setContent(artExport = ArtExportUiState.Exporting(forShare = true))

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithText("Compartilhando…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("art-share").assertIsNotEnabled()
        compose.onNodeWithTag("art-save").assertIsNotEnabled()
        compose.onNodeWithText("Compartilhar").assertDoesNotExist()
    }

    @Test
    fun `the saving state names the save target`() {
        setContent(artExport = ArtExportUiState.Exporting(forShare = false))

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithText("Salvando…").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("art-share").assertIsNotEnabled()
        compose.onNodeWithText("Compartilhando…").assertDoesNotExist()
    }

    @Test
    fun `a saved png confirms the gallery path`() {
        setContent(
            artExport = ArtExportUiState.Done(
                uri = android.net.Uri.parse("content://media/external/images/1"),
                savedToGallery = true,
                forShare = false,
                reused = false,
            ),
        )

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithText("PNG salvo em Pictures/MeuCoach.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a done share export hands the uri to the share sheet once`() {
        val shared = mutableListOf<android.net.Uri>()
        var consumed = 0
        setContent(
            artExport = ArtExportUiState.Done(
                uri = android.net.Uri.parse("content://media/external/images/1"),
                savedToGallery = true,
                forShare = true,
                reused = true,
            ),
            onShare = { shared.add(it) },
            onShareConsumed = { consumed += 1 },
        )

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.waitForIdle()

        assertThat(shared).hasSize(1)
        assertThat(consumed).isEqualTo(1)
    }

    @Test
    fun `returning to the art tab does not replay a consumed share`() {
        val exportState = mutableStateOf<ArtExportUiState>(ArtExportUiState.Idle)
        val shared = mutableListOf<android.net.Uri>()
        setContent(
            exportState = exportState,
            onShare = { shared.add(it) },
            onShareConsumed = { exportState.value = ArtExportUiState.Idle },
        )
        compose.onNodeWithTag("story-tab-art").performClick()

        exportState.value = ArtExportUiState.Done(
            uri = android.net.Uri.parse("content://media/external/images/1"),
            savedToGallery = true,
            forShare = true,
            reused = false,
        )
        compose.waitForIdle()
        assertThat(shared).hasSize(1)

        compose.onNodeWithTag("story-tab-video").performClick()
        compose.onNodeWithTag("story-tab-art").performClick()
        compose.waitForIdle()

        assertThat(shared).hasSize(1)
    }

    @Test
    fun `a stale export asks for a new render`() {
        setContent(artExport = ArtExportUiState.Stale)

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNodeWithText("Receita alterada — gere de novo.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `switching the font re-renders the preview with the new family`() {
        val recipeState = mutableStateOf(ArtRecipe(font = ArtFont.INTER))
        setContent(recipeState = recipeState)
        compose.onNodeWithTag("story-tab-art").performClick()

        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("art-preview-ready-inter").fetchSemanticsNodes().isNotEmpty()
        }

        recipeState.value = recipeState.value.copy(font = ArtFont.ANTON)
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithTag("art-preview-ready-anton").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `the art tab explains the poster and the transparent art`() {
        setContent()

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNodeWithTag("art-poster-vs-art").assertIsDisplayed()
        compose.onNodeWithText(
            "Pôster automático: opaco, nasce com o vídeo. " +
                "Arte PNG: transparente, para colar sobre uma foto.",
        ).assertIsDisplayed()
    }

    @Test
    fun `the format badge names both dimensions`() {
        setContent()

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNodeWithTag("art-format-badge").assertIsDisplayed()
        compose.onNodeWithText("1080×1350").assertIsDisplayed()
    }

    @Test
    fun `the preview anchor returns to the top`() {
        setContent()

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-scroll-preview").performScrollTo().performClick()

        compose.onNodeWithTag("art-preview").assertIsDisplayed()
    }

    @Test
    fun `switching tabs resets the scroll to the preview`() {
        setContent()

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-scroll-preview").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("story-tab-video").performScrollTo().performClick()
        compose.onNodeWithTag("story-tab-art").performScrollTo().performClick()

        compose.onNodeWithTag("art-preview").assertIsDisplayed()
    }

    @Test
    fun `the selected swatch label names the color`() {
        setContent(artRecipe = ArtRecipe(textColor = ArtColor.CYAN, routeColor = ArtColor.CORAL))

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNodeWithTag("art-text-color-selected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Selecionada: ciano").assertExists()
        compose.onNodeWithTag("art-route-color-selected").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Selecionada: coral").assertExists()
    }

    @Test
    fun `swatches expose the radio role`() {
        setContent()

        compose.onNodeWithTag("story-tab-art").performClick()
        val node = compose.onNodeWithTag("art-text-color-lime").performScrollTo().fetchSemanticsNode()

        assertThat(node.config.getOrElse(SemanticsProperties.Role) { androidx.compose.ui.semantics.Role.Button })
            .isEqualTo(androidx.compose.ui.semantics.Role.RadioButton)
    }

    @Test
    fun `swatch group titles are headings`() {
        setContent()

        compose.onNodeWithTag("story-tab-art").performClick()
        val node = compose.onNodeWithText("Cor do texto").performScrollTo().fetchSemanticsNode()

        assertThat(node.config.contains(SemanticsProperties.Heading)).isTrue()
    }

    @Test
    fun `a check tint always wins the contrast against the swatch`() {
        ArtColor.entries.forEach { color ->
            val tint = checkTint(color)
            val tintArgb = if (tint == androidx.compose.ui.graphics.Color.White) {
                0xFFFFFFFF.toInt()
            } else {
                0xFF000000.toInt()
            }
            assertThat(com.meucoach.app.story.ArtContrast.ratio(tintArgb, color.argb)).isGreaterThanOrEqualTo(3.0)
        }
    }

    @Test
    fun `the third extra tile explains the limit instead of the missing data`() {
        setContent(artRecipe = ArtRecipe(extras = listOf(StoryExtra.DATE, StoryExtra.AVG_HR)))

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-extra-cadence").performScrollTo()

        compose.onNode(
            hasTestTag("art-extra-cadence") and
                hasText("Limite de 2 — desmarque uma para incluir", substring = true),
        ).assertExists()
        compose.onNode(hasContentDescription("Cadência, limite de 2 atingido")).assertExists()
    }

    @Test
    fun `exporting shows a spinner inside the busy cta`() {
        setContent(artExport = ArtExportUiState.Exporting(forShare = true))

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-share-progress").performScrollTo().assertExists()
        compose.onNodeWithTag("art-share").assertIsNotEnabled()
        compose.onAllNodesWithTag("art-save-progress").assertCountEquals(0)
    }

    @Test
    fun `saving shows the spinner on the secondary cta only`() {
        setContent(artExport = ArtExportUiState.Exporting(forShare = false))

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNodeWithTag("art-save-progress").performScrollTo().assertExists()
        compose.onAllNodesWithTag("art-share-progress").assertCountEquals(0)
    }

    @Test
    fun `the preview description names the selected extras`() {
        setContent(artRecipe = ArtRecipe(extras = listOf(StoryExtra.DATE, StoryExtra.AVG_HR)))

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNode(
            hasContentDescription(
                "Prévia da arte 4:5, texto branco, traçado lima, fonte Inter, " +
                    "extras data e fc média, fundo transparente",
            ),
        ).assertExists()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun `the art tab remains usable at a 200 percent font scale on a narrow screen`() {
        setContent(
            artRecipe = ArtRecipe(extras = listOf(StoryExtra.DATE, StoryExtra.AVG_HR)),
            fontScale = 2f,
        )

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-preview").assertIsDisplayed()
        compose.onNodeWithTag("art-checker-light").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("art-share").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("art-scroll-preview").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `an export error explains and retries`() {
        var retried = false
        setContent(
            artExport = ArtExportUiState.Error(com.meucoach.app.story.StoryMessages.ART_EXPORT_FAILED),
            onRetry = { retried = true },
        )

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithText("Não foi possível gerar o PNG da arte. Tente novamente.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag("art-export-retry").performScrollTo().performClick()

        assertThat(retried).isTrue()
    }

    private fun setContent(
        artRecipe: ArtRecipe = ArtRecipe(),
        extraOptions: List<StoryExtraOption> = extraOptions(),
        plan: StoryPlan? = plan(),
        artExport: ArtExportUiState = ArtExportUiState.Idle,
        recipeState: State<ArtRecipe>? = null,
        exportState: State<ArtExportUiState>? = null,
        fontScale: Float = 1f,
        onFormat: (ArtFormat) -> Unit = {},
        onToggleExtra: (StoryExtra) -> Unit = {},
        onTextColor: (ArtColor) -> Unit = {},
        onRouteColor: (ArtColor) -> Unit = {},
        onFont: (ArtFont) -> Unit = {},
        onArtSaveAsDefault: (Boolean) -> Unit = {},
        onArtReset: () -> Unit = {},
        onShare: (android.net.Uri) -> Unit = {},
        onShareConsumed: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
                McTheme(darkTheme = true) {
                    StoryStudioContent(
                        state = StoryStudioUiState(
                            recipe = StoryRecipe(),
                            plan = plan,
                            extras = extraOptions,
                            saveAsDefault = true,
                            artRecipe = recipeState?.value ?: artRecipe,
                            artExport = exportState?.value ?: artExport,
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
                            onShare = onShare,
                            onShareConsumed = onShareConsumed,
                            onRetry = onRetry,
                        ),
                    )
                }
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
