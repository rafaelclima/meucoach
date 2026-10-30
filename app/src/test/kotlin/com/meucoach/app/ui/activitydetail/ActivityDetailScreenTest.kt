package com.meucoach.app.ui.activitydetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
        list.performScrollToNode(hasText("Pace × FC × tempo"))
        compose.onNodeWithText("Pace × FC × tempo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Gráfico de pace e frequência cardíaca ao longo do tempo")
            .assertIsDisplayed()

        list.performScrollToNode(hasText("Splits"))
        compose.onNodeWithText("Splits").assertIsDisplayed()

        list.performScrollToNode(hasText("O que aprendemos"))
        compose.onNodeWithText("O que aprendemos").assertIsDisplayed()
        compose.onNodeWithText("R-ACT-01", substring = true).assertIsDisplayed()
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
