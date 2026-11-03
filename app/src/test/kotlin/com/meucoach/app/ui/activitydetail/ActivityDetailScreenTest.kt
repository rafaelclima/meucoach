package com.meucoach.app.ui.activitydetail

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.app.story.StoryPlanCalculator
import com.meucoach.core.designsystem.theme.McTheme
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
    fun `previews a story before exporting`() {
        val activity = ActivityDetailTestData.state().activity!!.imported
        val plan = StoryPlanCalculator.calculate(activity)!!
        var exported = false
        setContent {
            ActivityDetailContent(
                state = ActivityDetailTestData.state().copy(
                    storyExport = StoryExportUiState.Preview(plan),
                ),
                onBack = {},
                onExportStory = { exported = true },
            )
        }

        val list = compose.onNodeWithTag("activity-detail-list")
        list.performScrollToNode(hasContentDescription("Prévia do story"))
        compose.onNodeWithContentDescription("Prévia do story").assertIsDisplayed()
        list.performScrollToNode(hasText("Gerar vídeo"))
        compose.onNodeWithText("Gerar vídeo").performClick()

        assertThat(exported).isTrue()
    }

    @Test
    fun `shares a finished story`() {
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
        list.performScrollToNode(hasText("Story do treino"))
        compose.onNodeWithText("Story do treino").assertIsDisplayed()
        list.performScrollToNode(hasText("Vídeo salvo na galeria."))
        compose.onNodeWithText("Vídeo salvo na galeria.").assertIsDisplayed()
        list.performScrollToNode(hasText("Compartilhar"))
        compose.onNodeWithText("Compartilhar").performClick()

        assertThat(shared).isEqualTo(uri)
    }

    @Test
    fun `mentions the cover when the story also saves one`() {
        val uri = Uri.parse("content://media/external/video/media/42")
        val cover = Uri.parse("content://media/external/images/media/43")
        setContent {
            ActivityDetailContent(
                state = ActivityDetailTestData.state().copy(
                    storyExport = StoryExportUiState.Done(
                        uri = uri,
                        savedToGallery = true,
                        coverUri = cover,
                    ),
                ),
                onBack = {},
            )
        }

        val list = compose.onNodeWithTag("activity-detail-list")
        list.performScrollToNode(hasText("Vídeo e capa salvos na galeria."))
        compose.onNodeWithText("Vídeo e capa salvos na galeria.").assertIsDisplayed()
        list.performScrollToNode(hasText("A capa é o frame final, pronta para o feed."))
        compose.onNodeWithText("A capa é o frame final, pronta para o feed.").assertIsDisplayed()
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
