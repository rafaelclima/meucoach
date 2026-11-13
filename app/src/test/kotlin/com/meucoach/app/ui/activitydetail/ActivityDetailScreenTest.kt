package com.meucoach.app.ui.activitydetail

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.calibration.CalibrationCandidate
import com.meucoach.core.domain.story.StoryDuration
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryMapStyle
import com.meucoach.core.domain.story.StoryPlanCalculator
import com.meucoach.core.domain.story.StoryPreset
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
class ActivityDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shows loading`() {
        setContent { ActivityDetailContent(state = ActivityDetailUiState(), onBack = {}) }

        compose.onNodeWithTag("activity-detail-loading").assertIsDisplayed()
    }

    @Test
    fun `shows not found with a back action`() {
        var back = false
        setContent {
            ActivityDetailContent(
                state = ActivityDetailUiState(isLoading = false, notFound = true),
                onBack = { back = true },
            )
        }

        compose.onNodeWithText("Atividade não encontrada").assertIsDisplayed()
        compose.onNodeWithText("Voltar").performClick()
        assertThat(back).isTrue()
    }

    @Test
    fun `shows hero metrics and explainable sections`() {
        setContent {
            ActivityDetailContent(state = ActivityDetailTestData.state(), onBack = {})
        }

        compose.onNodeWithText("Corrida").assertIsDisplayed()
        compose.onNodeWithText("5,00").assertIsDisplayed()
        compose.onNodeWithContentDescription("Voltar").assertIsDisplayed()

        val list = compose.onNodeWithTag("activity-detail-list")
        list.performScrollToNode(hasText("Ritmo × FC × tempo"))
        compose.onNodeWithText("Ritmo × FC × tempo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Gráfico de ritmo e frequência cardíaca ao longo do tempo")
            .assertIsDisplayed()

        list.performScrollToNode(hasText("Tempo em cada intensidade", substring = true))
        compose.onNodeWithText("Zonas de FC").assertIsDisplayed()
        compose.onNodeWithText("Tempo em cada intensidade", substring = true).assertIsDisplayed()

        list.performScrollToNode(hasText("Trechos"))
        compose.onNodeWithText("Trechos").assertIsDisplayed()

        list.performScrollToNode(hasText("O que aprendemos"))
        compose.onNodeWithText("O que aprendemos").assertIsDisplayed()
        compose.onNodeWithText("R-ACT-01", substring = true).assertDoesNotExist()
        compose.onNodeWithText("EV-", substring = true).assertDoesNotExist()
    }

    @Test
    fun `compares the planned and executed run`() {
        setContent {
            ActivityDetailContent(
                state = ActivityDetailTestData.state(comparison = ActivityDetailTestData.linkedComparison()),
                onBack = {},
            )
        }

        val list = compose.onNodeWithTag("activity-detail-list")
        list.performScrollToNode(hasTestTag("activity-comparison"))
        compose.onNodeWithText("PLANEJADO × EXECUTADO").assertIsDisplayed()
        compose.onNodeWithText("Fácil 30 min").assertIsDisplayed()
        compose.onNodeWithText("Duração", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Tudo dentro do planejado.").assertIsDisplayed()
        compose.onNodeWithText("R-", substring = true).assertDoesNotExist()
    }

    @Test
    fun `labels a free run without inventing a target`() {
        setContent {
            ActivityDetailContent(
                state = ActivityDetailTestData.state(comparison = ActivityDetailTestData.freeRunComparison()),
                onBack = {},
            )
        }

        val list = compose.onNodeWithTag("activity-detail-list")
        list.performScrollToNode(hasTestTag("activity-comparison"))
        compose.onNodeWithText("Treino livre").assertIsDisplayed()
        compose.onNodeWithText("Sem plano vinculado a esta corrida.").assertIsDisplayed()
        compose.onNodeWithText("Ritmo 6:00/km", substring = true).assertIsDisplayed()
        compose.onNodeWithText("no alvo", substring = true).assertDoesNotExist()
    }

    @Test
    fun `offers calibration when the run can be a threshold effort`() {
        var calibratedId: String? = null
        setContent {
            ActivityDetailContent(
                state = ActivityDetailTestData.state().copy(
                    calibrationCandidate = CalibrationCandidate(
                        distanceM = 7_000.0,
                        durationSec = 1_800,
                        date = java.time.LocalDate.parse("2026-10-24"),
                        averageHeartRate = 176,
                        heartRateCoverage = 1.0,
                    ),
                ),
                onBack = {},
                onCalibrate = { calibratedId = it },
            )
        }

        val list = compose.onNodeWithTag("activity-detail-list")
        list.performScrollToNode(hasText("Esta corrida pode calibrar seu limite"))
        compose.onNodeWithText("Esta corrida pode calibrar seu limite").assertIsDisplayed()
        list.performScrollToNode(hasText("Usar esta corrida para calibrar"))
        compose.onNodeWithTag("activity-calibrate").performClick()

        assertThat(calibratedId).isEqualTo("activity-1")
    }

    @Test
    fun `hides calibration when the run is not a threshold effort`() {
        setContent {
            ActivityDetailContent(state = ActivityDetailTestData.state(), onBack = {})
        }

        compose.onNodeWithTag("activity-detail-list")
            .performScrollToNode(hasText("Story do treino"))
        compose.onNodeWithTag("activity-calibrate").assertDoesNotExist()
    }

    @Test
    fun `confirms before deleting the activity`() {
        var deleted = false
        setContent {
            ActivityDetailContent(
                state = ActivityDetailTestData.state(),
                onBack = {},
                onDelete = { deleted = true },
            )
        }

        compose.onNodeWithTag("activity-menu").performClick()
        compose.onNodeWithText("Apagar atividade").performClick()
        compose.onNodeWithText("Apagar esta atividade?").assertIsDisplayed()
        assertThat(deleted).isFalse()

        compose.onNodeWithTag("activity-delete-confirm").performClick()
        assertThat(deleted).isTrue()
    }

    @Test
    fun `opens the story studio from the card`() {
        var opened = false
        setContent {
            ActivityDetailContent(
                state = ActivityDetailTestData.state(),
                onBack = {},
                storyActions = StoryStudioActions(onOpen = { opened = true }),
            )
        }

        val list = compose.onNodeWithTag("activity-detail-list")
        list.performScrollToNode(hasTestTag("story-studio-open"))
        compose.onNodeWithTag("story-studio-open").performClick()

        assertThat(opened).isTrue()
    }

    @Test
    fun `summarizes the recipe and keeps the options inside the studio`() {
        setContent {
            ActivityDetailContent(
                state = ActivityDetailTestData.state().copy(
                    storyStudio = StoryStudioUiState(
                        recipe = StoryRecipe(
                            preset = StoryPreset.CUSTOM,
                            extras = listOf(StoryExtra.AVG_HR),
                            duration = StoryDuration.SEC_30,
                            mapStyle = StoryMapStyle.MEUCOACH_LIGHT,
                        ),
                    ),
                ),
                onBack = {},
            )
        }

        val list = compose.onNodeWithTag("activity-detail-list")
        list.performScrollToNode(hasText("Três essenciais + FC média · 30 s · mapa claro."))
        compose.onNodeWithText("Três essenciais + FC média · 30 s · mapa claro.").assertIsDisplayed()
        compose.onNodeWithTag("story-style-dark").assertDoesNotExist()
        compose.onNodeWithTag("story-style-light").assertDoesNotExist()
        compose.onNodeWithTag("story-style-terrain").assertDoesNotExist()
    }

    @Test
    fun `shows the estimate, background hint and cancel while generating`() {
        var cancelled = false
        setContent {
            ActivityDetailContent(
                state = ActivityDetailTestData.state().copy(
                    storyExport = StoryExportUiState.Generating(progress = 0.25f, etaSec = 12),
                ),
                onBack = {},
                onCancelStory = { cancelled = true },
            )
        }

        val list = compose.onNodeWithTag("activity-detail-list")
        list.performScrollToNode(hasText("Pode sair da tela: avisamos quando ficar pronto."))
        compose.onNodeWithText("Gerando vídeo… 25%").assertExists()
        compose.onNodeWithText("Cerca de 12 s restantes").assertIsDisplayed()
        compose.onNodeWithText("Pode sair da tela: avisamos quando ficar pronto.").assertIsDisplayed()
        list.performScrollToNode(hasText("Cancelar"))
        compose.onNodeWithTag("story-cancel").performClick()

        assertThat(cancelled).isTrue()
    }

    @Test
    fun `shares the video and explains a missing poster`() {
        val uri = Uri.parse("content://media/external/video/media/42")
        var shared: Uri? = null
        setContent {
            ActivityDetailContent(
                state = ActivityDetailTestData.state().copy(
                    storyExport = StoryExportUiState.Done(uri = uri, savedToGallery = true),
                ),
                onBack = {},
                onShareStory = { shared = it },
            )
        }

        val list = compose.onNodeWithTag("activity-detail-list")
        list.performScrollToNode(hasText("Vídeo salvo na galeria."))
        compose.onNodeWithText("Vídeo salvo na galeria.").assertIsDisplayed()
        list.performScrollToNode(hasText("O pôster 4:5 não pôde ser gerado; o vídeo está pronto."))
        compose.onNodeWithText("O pôster 4:5 não pôde ser gerado; o vídeo está pronto.").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("story-share-video"))
        compose.onNodeWithTag("story-share-video").performClick()
        compose.onNodeWithTag("story-share-poster").assertDoesNotExist()

        assertThat(shared).isEqualTo(uri)
    }

    @Test
    fun `shares the cover and the poster separately`() {
        val uri = Uri.parse("content://media/external/video/media/42")
        val cover = Uri.parse("content://media/external/images/media/43")
        val poster = Uri.parse("content://media/external/images/media/44")
        var sharedCover: Uri? = null
        var sharedPoster: Uri? = null
        setContent {
            ActivityDetailContent(
                state = ActivityDetailTestData.state().copy(
                    storyExport = StoryExportUiState.Done(
                        uri = uri,
                        savedToGallery = true,
                        coverUri = cover,
                        posterUri = poster,
                    ),
                ),
                onBack = {},
                onShareCover = { sharedCover = it },
                onSharePoster = { sharedPoster = it },
            )
        }

        val list = compose.onNodeWithTag("activity-detail-list")
        list.performScrollToNode(hasText("Vídeo, capa 9:16 e pôster 4:5 salvos na galeria."))
        compose.onNodeWithText("Vídeo, capa 9:16 e pôster 4:5 salvos na galeria.").assertIsDisplayed()
        list.performScrollToNode(hasText("A capa 9:16 é o frame final, para stories. O pôster 4:5 é a capa do feed."))
        compose.onNodeWithText(
            "A capa 9:16 é o frame final, para stories. O pôster 4:5 é a capa do feed.",
        ).assertIsDisplayed()
        compose.onNodeWithText("pronta para o feed", substring = true).assertDoesNotExist()

        list.performScrollToNode(hasTestTag("story-share-poster"))
        compose.onNodeWithTag("story-share-poster").performClick()
        list.performScrollToNode(hasTestTag("story-share-cover"))
        compose.onNodeWithTag("story-share-cover").performClick()

        assertThat(sharedPoster).isEqualTo(poster)
        assertThat(sharedCover).isEqualTo(cover)
    }

    @Test
    fun `renders in the light theme`() {
        compose.setContent {
            McTheme(darkTheme = false) {
                ActivityDetailContent(state = ActivityDetailTestData.state(), onBack = {})
            }
        }

        compose.onNodeWithText("Corrida").assertIsDisplayed()
    }

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            McTheme(darkTheme = true) {
                content()
            }
        }
    }
}
