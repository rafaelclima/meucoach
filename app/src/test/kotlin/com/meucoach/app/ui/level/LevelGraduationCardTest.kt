package com.meucoach.app.ui.level

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
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
class LevelGraduationCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shows the transition reasons and the intact today note`() {
        setContent(suggestion())

        compose.onNodeWithTag("level-graduation-card").assertIsDisplayed()
        compose.onNodeWithText("PRONTO PARA SUBIR DE NÍVEL?", substring = false).assertIsDisplayed()
        compose.onNodeWithTag("level-graduation-transition").assertIsDisplayed()
        compose.onNodeWithText("De Iniciante para Intermediário").assertIsDisplayed()
        compose.onNodeWithText("O treino de hoje não muda", substring = true).assertIsDisplayed()
    }

    @Test
    fun `keeps rule ids out of the surface and reveals them in por que`() {
        setContent(suggestion())

        compose.onNodeWithText("R-LVL-01", substring = true).assertDoesNotExist()

        compose.onNodeWithTag("level-graduation-why-toggle").performClick()

        compose.onNodeWithTag("level-graduation-why").assertIsDisplayed()
        compose.onNodeWithTag("level-graduation-sources-toggle").assertIsDisplayed()
        compose.onNodeWithText("R-LVL-01", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Ver fontes e detalhes técnicos", substring = false).assertIsDisplayed()

        compose.onNodeWithTag("level-graduation-sources-toggle").performClick()

        compose.onNodeWithTag("level-graduation-sources").assertIsDisplayed()
        compose.onNodeWithText("R-LVL-01", substring = true).assertIsDisplayed()
        compose.onNodeWithText("R-LVL-02", substring = true).assertDoesNotExist()
    }

    @Test
    fun `accept decline and suppress actions are reachable`() {
        var accepted = false
        var declined = false
        var suppressed = false
        compose.setContent {
            McTheme(darkTheme = true) {
                LevelGraduationCard(
                    state = LevelGraduationUiState(suggestion = suggestion()),
                    onAccept = { accepted = true },
                    onDecline = { declined = true },
                    onSuppress = { suppressed = true },
                )
            }
        }

        compose.onNodeWithTag("level-graduation-accept").assertHasClickAction().performClick()
        compose.onNodeWithTag("level-graduation-decline").performClick()
        compose.onNodeWithTag("level-graduation-suppress").performClick()

        assertThat(accepted).isTrue()
        assertThat(declined).isTrue()
        assertThat(suppressed).isTrue()
    }

    @Test
    fun `actions meet the 48dp target`() {
        setContent(suggestion())

        compose.onNodeWithTag("level-graduation-accept").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("level-graduation-decline").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("level-graduation-suppress").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("level-graduation-why-toggle").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `renders in the light theme`() {
        compose.setContent {
            McTheme(darkTheme = false) {
                LevelGraduationCard(
                    state = LevelGraduationUiState(suggestion = suggestion()),
                    onAccept = {},
                    onDecline = {},
                    onSuppress = {},
                )
            }
        }

        compose.onNodeWithTag("level-graduation-card").assertIsDisplayed()
        compose.onNodeWithText("De Iniciante para Intermediário").assertIsDisplayed()
    }

    @Test
    fun `exposes the title as a heading for talkback`() {
        setContent(suggestion())

        compose.onNodeWithText("PRONTO PARA SUBIR DE NÍVEL?", substring = false)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
    }

    @Test
    fun `next level card shows missing criteria and hint without accept`() {
        compose.setContent {
            McTheme(darkTheme = true) {
                LevelGraduationSection(
                    state = LevelGraduationUiState(
                        nextLevel = LevelGraduationNextUi(
                            reason = "Ainda não há base para subir de nível.",
                            missing = listOf("Longão real de pelo menos 60 min"),
                            availabilityHint = "Sua agenda máxima é 50 min. Libere um dia de 60 min.",
                        ),
                    ),
                    onAccept = {},
                    onDecline = {},
                    onSuppress = {},
                    onDismissMessage = {},
                )
            }
        }

        compose.onNodeWithTag("level-next-card").assertIsDisplayed()
        compose.onNodeWithText("PRÓXIMO NÍVEL").assertIsDisplayed()
        compose.onNodeWithText("Longão real de pelo menos 60 min", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("level-next-availability").assertIsDisplayed()
        compose.onNodeWithTag("level-graduation-accept").assertDoesNotExist()
    }

    @Test
    fun `section is empty without suggestion or message`() {
        compose.setContent {
            McTheme(darkTheme = true) {
                LevelGraduationSection(
                    state = LevelGraduationUiState(),
                    onAccept = {},
                    onDecline = {},
                    onSuppress = {},
                    onDismissMessage = {},
                )
            }
        }

        compose.onNodeWithTag("level-graduation-card").assertDoesNotExist()
        compose.onNodeWithTag("level-graduation-message-card").assertDoesNotExist()
        compose.onNodeWithTag("level-graduation-loading").assertDoesNotExist()
    }

    @Test
    fun `section shows the loading state`() {
        compose.setContent {
            McTheme(darkTheme = true) {
                LevelGraduationSection(
                    state = LevelGraduationUiState(isLoading = true),
                    onAccept = {},
                    onDecline = {},
                    onSuppress = {},
                    onDismissMessage = {},
                )
            }
        }
        compose.onNodeWithTag("level-graduation-loading").assertIsDisplayed()
    }

    @Test
    fun `section shows a dismissible message`() {
        var dismissed = false
        compose.setContent {
            McTheme(darkTheme = true) {
                LevelGraduationSection(
                    state = LevelGraduationUiState(message = "Nível atualizado para Intermediário."),
                    onAccept = {},
                    onDecline = {},
                    onSuppress = {},
                    onDismissMessage = { dismissed = true },
                )
            }
        }
        compose.onNodeWithTag("level-graduation-message-card").assertIsDisplayed()
        compose.onNodeWithTag("level-graduation-message").assertIsDisplayed()
        compose.onNodeWithTag("level-graduation-dismiss-message").performClick()
        assertThat(dismissed).isTrue()
    }

    private fun setContent(suggestion: LevelGraduationSuggestionUi) {
        compose.setContent {
            McTheme(darkTheme = true) {
                LevelGraduationSection(
                    state = LevelGraduationUiState(suggestion = suggestion),
                    onAccept = {},
                    onDecline = {},
                    onSuppress = {},
                    onDismissMessage = {},
                )
            }
        }
    }

    private fun suggestion() = LevelGraduationSuggestionUi(
        from = Experience.BEGINNER,
        to = Experience.INTERMEDIATE,
        reasons = listOf(
            "4 de 6 semanas com pelo menos 80% dos treinos concluídos com evidência forte",
            "Longão de pelo menos 60 minutos concluído",
        ),
        ruleIds = listOf("R-LVL-01"),
        evidenceIds = listOf("EV-SEILER-2010", "EV-DANIELS-2014"),
        confidence = DataConfidence.MEDIUM,
        inputsHash = "hash-a",
    )
}
