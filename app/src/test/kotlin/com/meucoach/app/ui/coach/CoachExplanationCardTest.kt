package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
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
class CoachExplanationCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `explicacao longa vira bolhas curtas e bullets expandem`() {
        setContent(
            state = CoachExplanationState.Ready(
                title = "Plano da semana",
                body = "Esta é uma semana de base leve. ".repeat(20),
                bullets = listOf("Mantenha o volume atual"),
            ),
        )

        assertThat(compose.onAllNodesWithTag("coach-explanation-bubble").fetchSemanticsNodes().size)
            .isGreaterThan(1)
        compose.onNodeWithTag("coach-explanation-bullets").assertDoesNotExist()
        compose.onNodeWithTag("coach-explanation-toggle").performScrollTo().performClick()

        compose.onNodeWithText("• Mantenha o volume atual").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mostrar menos").assertExists()
    }

    @Test
    fun `explicacao longa preserva a pergunta final e esconde o meio`() {
        val body = "Primeira frase com detalhe suficiente para ocupar a bolha inteira sem caber junto. " +
            "Segunda frase também comprida para forçar outra bolha e preservar o corte. " +
            "Terceira frase fica escondida no meio até você pedir os detalhes. " +
            "Bora seguir assim?"
        setContent(
            state = CoachExplanationState.Ready(
                title = "Semana",
                body = body,
                bullets = emptyList(),
            ),
        )

        compose.onNodeWithText("Bora seguir assim?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Terceira frase fica escondida", substring = true).assertDoesNotExist()

        compose.onNodeWithTag("coach-explanation-toggle").performScrollTo().performClick()

        compose.onNodeWithText("Terceira frase fica escondida", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `explicacao curta sem bullets nao mostra o toggle`() {
        setContent(
            state = CoachExplanationState.Ready(
                title = "Plano da semana",
                body = "Volume mantido.",
                bullets = emptyList(),
            ),
        )

        compose.onNodeWithTag("coach-explanation-toggle").assertDoesNotExist()
        compose.onAllNodesWithTag("coach-explanation-bubble").onFirst().assertIsDisplayed()
    }

    private fun setContent(state: CoachExplanationState.Ready) {
        compose.setContent {
            McTheme(darkTheme = true) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    CoachExplanationCard(state = state)
                }
            }
        }
    }
}
