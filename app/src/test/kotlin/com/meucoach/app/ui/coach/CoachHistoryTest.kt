package com.meucoach.app.ui.coach

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.coach.ChatTurn
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
    fun `historico mostra a timeline unificada em leitura`() {
        setContent(history = readyHistory())
        openHistory()

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
        openHistory()

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
        openHistory()

        compose.onNodeWithTag("coach-history-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-history-retry").performScrollTo().performClick()

        assertThat(retried).isEqualTo(1)
    }

    @Test
    fun `historico em carregamento mostra progresso`() {
        setContent(history = CoachHistoryState.Loading)
        openHistory()

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
        openHistory()

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
    fun `apagar item individual pede confirmacao`() {
        var deleted: ChatTurn? = null
        setContent(history = readyHistory(), onDeleteTurn = { deleted = it })
        openHistory()

        compose.onNodeWithTag("coach-history-delete-turn-message-message-1")
            .performScrollTo()
            .assertHasClickAction()
            .performClick()
        compose.onNodeWithTag("coach-history-delete-dialog").assertIsDisplayed()
        compose.onNodeWithText("Apagar este item do histórico?", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        assertThat(deleted).isNull()

        compose.onNodeWithTag("coach-history-delete-turn-message-message-1")
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag("coach-history-delete-confirm").performClick()

        assertThat((deleted as? ChatTurn.Coach)?.title).isEqualTo("Foco")
    }

    @Test
    fun `apagar resumo individual passa o turn do resumo`() {
        var deleted: ChatTurn? = null
        setContent(history = readyHistory(), onDeleteTurn = { deleted = it })
        openHistory()

        compose.onNodeWithTag("coach-history-delete-turn-summary-2026-11-02")
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag("coach-history-delete-confirm").performClick()

        val turn = deleted as? ChatTurn.Coach
        assertThat(turn?.origin).isEqualTo(ChatTurn.Origin.SUMMARY)
        assertThat(turn?.weekStart).isEqualTo(LocalDate.of(2026, 11, 2))
    }

    @Test
    fun `historico aparece no tema claro`() {
        setContent(history = readyHistory(), dark = false)
        openHistory()

        compose.onNodeWithTag("coach-history-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Resumo da semana de 02/11").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `historico suporta fonte 200 porcento`() {
        setContent(history = readyHistory(), fontScale = 2f)
        openHistory()

        compose.onNodeWithTag("coach-history-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Foco").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Apagar todo o histórico do Coach")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `historico longo mostra recentes com ver conversa completa`() {
        setContent(
            history = CoachHistoryState.Ready(
                turns = listOf(
                    turn("summary-2026-11-09", ChatTurn.Origin.SUMMARY, "Semana nova", weekStart = "2026-11-09"),
                    turn("message-new-1", ChatTurn.Origin.EXPLAIN, "Nova 1"),
                    turn("message-new-2", ChatTurn.Origin.EXPLAIN, "Nova 2"),
                    turn("summary-2026-11-02", ChatTurn.Origin.SUMMARY, "Semana velha", weekStart = "2026-11-02"),
                    turn("message-old-1", ChatTurn.Origin.EXPLAIN, "Velha"),
                ),
            ),
        )
        openHistory()

        compose.onNodeWithText("Semana nova").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nova 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nova 2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Semana velha").assertDoesNotExist()
        compose.onNodeWithText("Velha").assertDoesNotExist()
        compose.onNodeWithTag("coach-history-more").performScrollTo().performClick()

        compose.onNodeWithText("Semana velha").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Velha").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-history-less").performScrollTo().performClick()
        compose.onNodeWithText("Velha").assertDoesNotExist()
    }

    private fun readyHistory() = CoachHistoryState.Ready(
        turns = listOf(
            turn(
                "summary-2026-11-02",
                ChatTurn.Origin.SUMMARY,
                "Semana 1",
                body = "3 de 4 sessões.",
                bullets = listOf("Adesão"),
                weekStart = "2026-11-02",
            ),
            turn(
                "message-message-1",
                ChatTurn.Origin.EXPLAIN,
                "Foco",
                body = "Mantivemos 30 min.",
                bullets = listOf("Base"),
            ),
        ),
    )

    private fun turn(
        id: String,
        origin: ChatTurn.Origin,
        title: String,
        body: String = "Corpo de $title.",
        bullets: List<String> = emptyList(),
        weekStart: String? = null,
    ) = ChatTurn.Coach(
        id = id,
        title = title,
        body = body,
        bullets = bullets,
        createdAt = createdAt,
        origin = origin,
        weekStart = weekStart?.let(LocalDate::parse),
    )

    private fun openHistory() {
        compose.onNodeWithTag("coach-retro-history").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithTag("coach-history-sheet").assertIsDisplayed()
    }

    private fun setContent(
        history: CoachHistoryState,
        consented: Boolean = true,
        dark: Boolean = true,
        fontScale: Float = 1f,
        onClear: () -> Unit = {},
        onRetry: () -> Unit = {},
        onDeleteTurn: (ChatTurn) -> Unit = {},
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
                        onDeleteHistoryTurn = onDeleteTurn,
                    )
                }
            }
        }
    }
}
