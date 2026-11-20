package com.meucoach.app.ui.coach

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.coach.CoachMemoryMessage
import com.meucoach.core.domain.coach.CoachMemorySummary
import com.meucoach.core.domain.coach.CoachMessageKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoachHistoryTest {
    @get:Rule
    val compose = createComposeRule()

    private val createdAt = Instant.parse("2026-11-18T12:00:00Z")

    @Test
    fun `historico mostra resumos e conversa em leitura`() {
        setContent(history = readyHistory())

        compose.onNodeWithTag("coach-history-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nossa conversa").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Resumo da semana de 02/11").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Semana 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3 de 4 sessões.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Explicação da semana", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Foco").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mantivemos 30 min.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Editar").assertDoesNotExist()
        compose.onNodeWithText("Salvar").assertDoesNotExist()
    }

    @Test
    fun `historico vazio explica que ainda nao ha conversa`() {
        setContent(history = CoachHistoryState.Empty)

        compose.onNodeWithTag("coach-history-empty").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nada guardado ainda", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `historico em erro oferece tentar de novo`() {
        var retried = 0
        setContent(
            history = CoachHistoryState.Error("Não foi possível carregar o histórico."),
            onRetry = { retried++ },
        )

        compose.onNodeWithTag("coach-history-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-history-retry").performScrollTo().performClick()

        assertThat(retried).isEqualTo(1)
    }

    @Test
    fun `historico em carregamento mostra progresso`() {
        setContent(history = CoachHistoryState.Loading)

        compose.onNodeWithTag("coach-history-loading").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Carregando histórico…").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `historico somente aparece com opt-in`() {
        setContent(history = readyHistory(), consented = false)

        compose.onNodeWithTag("coach-history-card").assertDoesNotExist()
    }

    @Test
    fun `apagar historico pede confirmacao e nao apaga ao cancelar`() {
        var cleared = 0
        setContent(history = readyHistory(), onClear = { cleared++ })

        compose.onNodeWithContentDescription("Apagar todo o histórico do Coach")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        compose.onNodeWithTag("coach-history-clear-dialog").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        assertThat(cleared).isZero()

        compose.onNodeWithContentDescription("Apagar todo o histórico do Coach")
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag("coach-history-clear-confirm").performClick()

        assertThat(cleared).isEqualTo(1)
    }

    @Test
    fun `apagar mensagem individual pede confirmacao`() {
        var deletedId: String? = null
        setContent(history = readyHistory(), onDeleteMessage = { deletedId = it })

        compose.onNodeWithTag("coach-history-delete-message-message-1")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        compose.onNodeWithTag("coach-history-delete-dialog").assertIsDisplayed()
        compose.onNodeWithText("Apagar esta mensagem do histórico?", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        assertThat(deletedId).isNull()

        compose.onNodeWithTag("coach-history-delete-message-message-1")
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag("coach-history-delete-confirm").performClick()

        assertThat(deletedId).isEqualTo("message-1")
    }

    @Test
    fun `apagar resumo individual pede confirmacao`() {
        var deletedWeek: LocalDate? = null
        setContent(history = readyHistory(), onDeleteSummary = { deletedWeek = it })

        compose.onNodeWithTag("coach-history-delete-summary-2026-11-02")
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Apagar este resumo da semana?", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("coach-history-delete-confirm").performClick()

        assertThat(deletedWeek).isEqualTo(LocalDate.of(2026, 11, 2))
    }

    @Test
    fun `historico aparece no tema claro`() {
        setContent(history = readyHistory(), dark = false)

        compose.onNodeWithTag("coach-history-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Resumo da semana de 02/11").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `historico suporta fonte 200 porcento`() {
        setContent(history = readyHistory(), fontScale = 2f)

        compose.onNodeWithTag("coach-history-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Foco").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Apagar todo o histórico do Coach")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun readyHistory() = CoachHistoryState.Ready(
        summaries = listOf(
            CoachMemorySummary(
                weekStart = LocalDate.of(2026, 11, 2),
                inputsHash = "review-hash",
                title = "Semana 1",
                body = "3 de 4 sessões.",
                bullets = listOf("Adesão"),
                createdAt = createdAt,
            ),
        ),
        messages = listOf(
            CoachMemoryMessage(
                id = "message-1",
                kind = CoachMessageKind.EXPLAIN,
                inputsHash = "explain-hash",
                title = "Foco",
                body = "Mantivemos 30 min.",
                bullets = listOf("Base"),
                createdAt = createdAt,
            ),
        ),
    )

    private fun setContent(
        history: CoachHistoryState,
        consented: Boolean = true,
        dark: Boolean = true,
        fontScale: Float = 1f,
        onClear: () -> Unit = {},
        onRetry: () -> Unit = {},
        onDeleteMessage: (String) -> Unit = {},
        onDeleteSummary: (LocalDate) -> Unit = {},
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale),
            ) {
                McTheme(darkTheme = dark) {
                    CoachScreen(
                        consented = consented,
                        showConsent = !consented,
                        explanation = CoachExplanationState.Ready(
                            title = "Semana atual",
                            body = "Plano da semana.",
                            bullets = emptyList(),
                        ),
                        onGrant = {},
                        onDismiss = {},
                        onRevoke = {},
                        onRetry = {},
                        history = history,
                        onClearHistory = onClear,
                        onRetryHistory = onRetry,
                        onDeleteHistoryMessage = onDeleteMessage,
                        onDeleteHistorySummary = onDeleteSummary,
                    )
                }
            }
        }
    }
}
