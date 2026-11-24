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
import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtBlock
import com.meucoach.core.domain.story.ArtColor
import com.meucoach.core.domain.story.ArtFont
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtLayout
import com.meucoach.core.domain.story.ArtOffset
import com.meucoach.core.domain.story.ArtPhotoCrop
import com.meucoach.core.domain.story.ArtPhotoRef
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.ArtTemplate
import com.meucoach.core.domain.story.RouteData
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryMetricCatalog
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryPlanCalculator
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
    fun `selects the 9x16 story format`() {
        var format: ArtFormat? = null
        setContent(onFormat = { format = it })

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-format-portrait_9_16").performScrollTo().performClick()

        assertThat(format).isEqualTo(ArtFormat.PORTRAIT_9_16)
    }

    @Test
    fun `the summary line follows the 9x16 format`() {
        setContent(artRecipe = ArtRecipe(format = ArtFormat.PORTRAIT_9_16, font = ArtFont.ANTON))

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNodeWithText("9:16 · Anton · texto branco · traçado lima.").assertIsDisplayed()
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
    fun `selects the route stroke`() {
        var stroke: StoryRouteStroke? = null
        setContent(onRouteStroke = { stroke = it })

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-route-stroke-dashed").performScrollTo().performClick()

        assertThat(stroke).isEqualTo(StoryRouteStroke.DASHED)
    }

    @Test
    fun `exposes the selected route stroke to talkback`() {
        setContent(artRecipe = ArtRecipe(routeStroke = StoryRouteStroke.NEON))

        compose.onNodeWithTag("story-tab-art").performClick()
        val node = compose.onNodeWithTag("art-route-stroke-neon").performScrollTo().fetchSemanticsNode()

        assertThat(node.config.getOrElse(SemanticsProperties.Selected) { false }).isTrue()
        compose.onNode(hasContentDescription("Neon, selecionado")).assertExists()
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
        compose.onNodeWithTag("art-preview-empty").assertIsDisplayed()
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
        compose.onNodeWithText("Fundo transparente").assertIsDisplayed()
        compose.onNodeWithTag("art-checker-light").performScrollTo().performClick()
        compose.onNodeWithTag("art-checker-dark").assertIsDisplayed()
    }

    @Test
    fun `describes the preview to talkback`() {
        setContent()

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNode(
            hasContentDescription(
                "Prévia da arte 4:5, texto branco, traçado lima, fonte Inter, fundo transparente, layout Topo, texto 100%",
            ),
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
        compose.onNodeWithTag("art-export-done").performScrollTo().assertIsDisplayed()
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

        compose.onNodeWithTag("art-export-stale").performScrollTo().assertIsDisplayed()
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
                "Arte: transparente (PNG) ou com sua foto (JPG).",
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
    fun `the fixed preview remains visible while editing colors and fonts`() {
        setContent()

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-text-color-cyan").performScrollTo()
        compose.onNodeWithTag("art-preview").assertIsDisplayed()
        compose.onNodeWithTag("art-font-anton").performScrollTo()
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
                    "extras data e fc média, fundo transparente, layout Topo, texto 100%",
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
    }

    @Test
    fun `an export error explains and retries`() {
        var retried = false
        setContent(
            artExport = ArtExportUiState.Error(com.meucoach.app.story.StoryMessages.ART_EXPORT_FAILED),
            onRetry = { retried = true },
        )

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-export-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("art-export-retry").performScrollTo().performClick()

        assertThat(retried).isTrue()
    }

    @Test
    fun `hides the tab segment while scrolled and restores it at the top`() {
        setContent()

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("story-tab-segment").assertIsDisplayed()

        compose.onNodeWithTag("art-save").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("story-tab-segment").assertDoesNotExist()

        compose.onNodeWithTag("art-summary").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("story-tab-segment").assertIsDisplayed()
    }

    @Test
    fun `a saved jpg confirms the gallery path in a snackbar`() {
        setContent(
            artRecipe = ArtRecipe(
                background = ArtBackgroundMode.PHOTO,
                photo = ArtPhotoRef(
                    photoId = "123e4567-e89b-12d3-a456-426614174000",
                    activityId = "activity-1",
                ),
            ),
            artExport = ArtExportUiState.Done(
                uri = android.net.Uri.parse("content://media/external/images/1"),
                savedToGallery = true,
                forShare = false,
                reused = false,
            ),
        )

        compose.onNodeWithTag("story-tab-art").performClick()
        // Inline confirmation below the CTAs plus the snackbar on top.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("JPG salvo em Pictures/MeuCoach.").fetchSemanticsNodes().size == 2
        }
    }

    @Test
    fun `a saved png confirms the gallery path in a snackbar`() {
        setContent(
            artExport = ArtExportUiState.Done(
                uri = android.net.Uri.parse("content://media/external/images/1"),
                savedToGallery = true,
                forShare = false,
                reused = false,
            ),
        )

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("PNG salvo em Pictures/MeuCoach.").fetchSemanticsNodes().size == 2
        }
    }

    @Test
    fun `a save failure surfaces in a snackbar`() {
        setContent(
            artExport = ArtExportUiState.Error(com.meucoach.app.story.StoryMessages.ART_EXPORT_FAILED),
        )

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Não foi possível gerar a imagem da arte. Tente novamente.")
                .fetchSemanticsNodes().size == 2
        }
    }

    @Test
    fun `rapid nudges on the extras block settle on a ready preview`() {
        // Device crash 2× (24/11/2026): the producer recycled the displayed
        // preview bitmap while Compose still drew it ("Canvas: trying to use
        // a recycled bitmap"). The JVM cannot reproduce the GPU race; this
        // test pins the scenario (rapid recipe churn with a photo) and the
        // fix is verified on device.
        val photo = ArtPhotoRef(
            photoId = "123e4567-e89b-12d3-a456-426614174000",
            activityId = "activity-1",
        )
        val recipe = mutableStateOf(
            ArtRecipe(
                background = ArtBackgroundMode.PHOTO,
                photo = photo,
                extras = listOf(StoryExtra.DATE, StoryExtra.AVG_HR),
            ),
        )
        setContent(
            recipeState = recipe,
            onNudge = { block, dx, dy ->
                val current = recipe.value.layout.offset(block)
                val desired = ArtOffset(
                    dx = (current.dx + dx).coerceIn(ArtOffset.MIN, ArtOffset.MAX),
                    dy = (current.dy + dy).coerceIn(ArtOffset.MIN, ArtOffset.MAX),
                )
                recipe.value = recipe.value.copy(
                    layout = recipe.value.layout.withOffset(block, desired),
                )
            },
            onLoadPhoto = { _, _, _ ->
                android.graphics.Bitmap.createBitmap(64, 48, android.graphics.Bitmap.Config.ARGB_8888)
            },
        )

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-block-extras").performScrollTo().performClick()
        repeat(15) {
            compose.onNodeWithTag("art-nudge-right").performClick()
        }
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithTag("art-preview-ready-inter").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun setContent(
        artRecipe: ArtRecipe = ArtRecipe(),
        extraOptions: List<StoryExtraOption> = extraOptions(),
        plan: StoryPlan? = plan(),
        artExport: ArtExportUiState = ArtExportUiState.Idle,
        recipeState: State<ArtRecipe>? = null,
        exportState: State<ArtExportUiState>? = null,
        fontScale: Float = 1f,
        artSelectedBlock: ArtBlock = ArtBlock.DISTANCE,
        artPhotoBusy: Boolean = false,
        artPhotoError: String? = null,
        artMissingPhotoWarning: Boolean = false,
        artCropEditing: Boolean = false,
        artCropDraft: ArtPhotoCrop = ArtPhotoCrop(),
        artPhotoQualityWarning: String? = null,
        artShowRemoveConfirm: Boolean = false,
        onFormat: (ArtFormat) -> Unit = {},
        onToggleExtra: (StoryExtra) -> Unit = {},
        onTextColor: (ArtColor) -> Unit = {},
        onRouteColor: (ArtColor) -> Unit = {},
        onRouteStroke: (StoryRouteStroke) -> Unit = {},
        onFont: (ArtFont) -> Unit = {},
        onArtSaveAsDefault: (Boolean) -> Unit = {},
        onArtReset: () -> Unit = {},
        onShare: (android.net.Uri) -> Unit = {},
        onShareConsumed: () -> Unit = {},
        onRetry: () -> Unit = {},
        onBackground: (ArtBackgroundMode) -> Unit = {},
        onTemplate: (ArtTemplate) -> Unit = {},
        onBlock: (ArtBlock) -> Unit = {},
        onNudge: (ArtBlock, Float, Float) -> Unit = { _, _, _ -> },
        onTextScale: (Int) -> Unit = {},
        onPickPhoto: (android.net.Uri?) -> Unit = {},
        onRetryPhoto: () -> Unit = {},
        onDismissPhotoError: () -> Unit = {},
        onDismissMissingPhoto: () -> Unit = {},
        onOpenCrop: () -> Unit = {},
        onCropChange: (Float, Float, Float) -> Unit = { _, _, _ -> },
        onCenterCrop: () -> Unit = {},
        onFrameCrop: () -> Unit = {},
        onApplyCrop: () -> Unit = {},
        onCancelCrop: () -> Unit = {},
        onRequestRemove: () -> Unit = {},
        onCancelRemove: () -> Unit = {},
        onRemovePhoto: () -> Unit = {},
        onLoadPhoto: (suspend (ArtPhotoRef, ArtFormat, Int) -> android.graphics.Bitmap?) = { _, _, _ -> null },
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
                            artSelectedBlock = artSelectedBlock,
                            artPhotoBusy = artPhotoBusy,
                            artPhotoError = artPhotoError,
                            artMissingPhotoWarning = artMissingPhotoWarning,
                            artCropEditing = artCropEditing,
                            artCropDraft = artCropDraft,
                            artPhotoQualityWarning = artPhotoQualityWarning,
                            artShowRemoveConfirm = artShowRemoveConfirm,
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
                            onRouteStroke = onRouteStroke,
                            onFont = onFont,
                            onSaveAsDefault = onArtSaveAsDefault,
                            onReset = onArtReset,
                            onShare = onShare,
                            onShareConsumed = onShareConsumed,
                            onRetry = onRetry,
                            onBackground = onBackground,
                            onTemplate = onTemplate,
                            onBlock = onBlock,
                            onNudge = onNudge,
                            onTextScale = onTextScale,
                            onPickPhoto = onPickPhoto,
                            onRetryPhoto = onRetryPhoto,
                            onDismissPhotoError = onDismissPhotoError,
                            onDismissMissingPhoto = onDismissMissingPhoto,
                            onOpenCrop = onOpenCrop,
                            onCropChange = onCropChange,
                            onCenterCrop = onCenterCrop,
                            onFrameCrop = onFrameCrop,
                            onApplyCrop = onApplyCrop,
                            onCancelCrop = onCancelCrop,
                            onRequestRemove = onRequestRemove,
                            onCancelRemove = onCancelRemove,
                            onRemovePhoto = onRemovePhoto,
                            onLoadPhoto = onLoadPhoto,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun `the background toggle switches between transparent and photo`() {
        var background: ArtBackgroundMode? = null
        setContent(onBackground = { background = it })

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-background-photo").performScrollTo().performClick()

        assertThat(background).isEqualTo(ArtBackgroundMode.PHOTO)
    }

    @Test
    fun `transparent mode hides the photo card`() {
        setContent(artRecipe = ArtRecipe(background = ArtBackgroundMode.TRANSPARENT))

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-photo-pick").assertDoesNotExist()
        compose.onNodeWithText("Fundo transparente").assertIsDisplayed()
    }

    @Test
    fun `the photo card appears in photo mode`() {
        setContent(artRecipe = ArtRecipe(background = ArtBackgroundMode.PHOTO))

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-photo-label").assertIsDisplayed()
        compose.onNodeWithTag("art-photo-empty").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("art-photo-pick").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `photo mode hides the checker and shows the photo label`() {
        setContent(artRecipe = ArtRecipe(background = ArtBackgroundMode.PHOTO))

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNodeWithTag("art-photo-label").assertIsDisplayed()
        compose.onNodeWithTag("art-checker-dark").assertDoesNotExist()
    }

    @Test
    fun `photo mode without a photo disables the ctas with a reason`() {
        setContent(artRecipe = ArtRecipe(background = ArtBackgroundMode.PHOTO, photo = null))

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNodeWithTag("art-share").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("art-save").assertIsNotEnabled()
        compose.onNodeWithTag("art-photo-required").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `photo mode ctas say salvar jpg`() {
        val photo = ArtPhotoRef(
            photoId = "123e4567-e89b-12d3-a456-426614174000",
            activityId = "activity-1",
        )
        setContent(artRecipe = ArtRecipe(background = ArtBackgroundMode.PHOTO, photo = photo))

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNodeWithText("Salvar JPG").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Salvar PNG").assertDoesNotExist()
    }

    @Test
    fun `transparent ctas keep salvar png`() {
        setContent()

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNodeWithText("Salvar PNG").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a done photo export confirms the jpg gallery path`() {
        val photo = ArtPhotoRef(
            photoId = "123e4567-e89b-12d3-a456-426614174000",
            activityId = "activity-1",
        )
        setContent(
            artRecipe = ArtRecipe(background = ArtBackgroundMode.PHOTO, photo = photo),
            artExport = ArtExportUiState.Done(
                uri = android.net.Uri.parse("content://media/external/images/1"),
                savedToGallery = true,
                forShare = false,
                reused = false,
            ),
        )

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-export-done").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the position card is always visible with templates and blocks`() {
        setContent()

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNodeWithTag("art-position-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("art-template-top").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("art-template-center").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("art-template-bottom").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("art-block-distance").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("art-nudge-up").performScrollTo().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `templates expose the radio role`() {
        setContent(artRecipe = ArtRecipe(layout = ArtLayout(template = ArtTemplate.CENTER)))

        compose.onNodeWithTag("story-tab-art").performClick()
        val node = compose.onNodeWithTag("art-template-center").performScrollTo().fetchSemanticsNode()

        assertThat(node.config.getOrElse(SemanticsProperties.Selected) { false }).isTrue()
    }

    @Test
    fun `nudge buttons name the block and direction`() {
        setContent()

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNode(hasContentDescription("Mover distância para cima")).assertExists()
        compose.onNodeWithTag("art-nudge-up").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("art-nudge-down").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("art-nudge-left").performScrollTo().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("art-nudge-right").performScrollTo().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `a far nudge persists the clamped offset`() {
        var nudged: Triple<ArtBlock, Float, Float>? = null
        setContent(onNudge = { block, dx, dy -> nudged = Triple(block, dx, dy) })

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-nudge-right").performScrollTo().performClick()

        assertThat(nudged?.first).isEqualTo(ArtBlock.DISTANCE)
        assertThat(nudged?.second ?: 0f).isCloseTo(0.02f, org.assertj.core.api.Assertions.within(0.001f))
    }

    @Test
    fun `the text scale stepper labels the value and steps by five`() {
        var scale: Int? = null
        setContent(
            artRecipe = ArtRecipe(layout = ArtLayout(textScalePercent = 100)),
            onTextScale = { scale = it },
        )

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithText("Tamanho do texto: 100%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("art-text-scale-plus").performScrollTo().performClick()

        assertThat(scale).isEqualTo(105)
    }

    @Test
    fun `the preview description names mode template and scale`() {
        val photo = ArtPhotoRef(
            photoId = "123e4567-e89b-12d3-a456-426614174000",
            activityId = "activity-1",
        )
        setContent(
            artRecipe = ArtRecipe(
                background = ArtBackgroundMode.PHOTO,
                photo = photo,
                layout = ArtLayout(template = ArtTemplate.CENTER, textScalePercent = 100),
            ),
        )

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNode(
            hasContentDescription(
                "Prévia da arte 4:5, texto branco, traçado lima, fonte Inter, " +
                    "fundo foto, layout Centro, texto 100%",
            ),
        ).assertExists()
    }

    @Test
    fun `photo mode without a photo shows the empty preview`() {
        setContent(artRecipe = ArtRecipe(background = ArtBackgroundMode.PHOTO, photo = null))

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNodeWithTag("art-preview-photo-empty").assertIsDisplayed()
    }

    @Test
    fun `the crop editor exposes accessible controls`() {
        val photo = ArtPhotoRef(
            photoId = "123e4567-e89b-12d3-a456-426614174000",
            activityId = "activity-1",
        )
        setContent(
            artRecipe = ArtRecipe(background = ArtBackgroundMode.PHOTO, photo = photo),
            artCropEditing = true,
        )

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithTag("art-crop-preview").assertIsDisplayed()
        compose.onNodeWithTag("art-crop-up").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("art-crop-zoom-in").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("art-crop-center").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("art-crop-frame").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("art-crop-cancel").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("art-crop-apply").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the remove confirmation names the fallback`() {
        val photo = ArtPhotoRef(
            photoId = "123e4567-e89b-12d3-a456-426614174000",
            activityId = "activity-1",
        )
        var removed = false
        setContent(
            artRecipe = ArtRecipe(background = ArtBackgroundMode.PHOTO, photo = photo),
            artShowRemoveConfirm = true,
            onRemovePhoto = { removed = true },
        )

        compose.onNodeWithTag("story-tab-art").performClick()
        compose.onNodeWithText("Remover a foto? A arte volta ao fundo transparente.").assertIsDisplayed()
        compose.onNodeWithTag("art-photo-confirm-remove").performClick()

        assertThat(removed).isTrue()
    }

    @Test
    fun `a photo error shows a retry without technical detail`() {
        setContent(artPhotoError = "Não foi possível ler a foto. Tente outra imagem.")

        compose.onNodeWithTag("story-tab-art").performClick()

        compose.onNodeWithTag("art-photo-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("art-photo-retry").performScrollTo().assertIsDisplayed()
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
