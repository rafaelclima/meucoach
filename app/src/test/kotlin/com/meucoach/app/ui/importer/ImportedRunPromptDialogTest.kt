package com.meucoach.app.ui.importer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class ImportedRunPromptDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `offers to turn the imported run into today's workout`() {
        var linked = false
        var kept = false
        compose.setContent {
            McTheme(darkTheme = true) {
                ImportedRunPromptDialog(
                    state = ImportedRunPromptState(
                        prompt = ImportedRunPromptUi.Offer(
                            activityId = "activity-1",
                            activityTitle = "Corrida",
                            activityDetail = "6,00 km · 40:00 · 6:40 /km",
                            sessionTitle = "Fácil e conversável",
                            sessionDurationMin = 40,
                        ),
                    ),
                    onLink = { linked = true },
                    onReplaceManualRecord = {},
                    onKeepAsFreeRun = { kept = true },
                    onConsumeMessage = {},
                )
            }
        }

        compose.onNodeWithText("Corrida importada").assertIsDisplayed()
        compose.onNodeWithText("Fácil e conversável · 40 min").assertIsDisplayed()
        compose.onNodeWithTag("imported-run-link").performClick()
        assertThat(linked).isTrue()

        compose.onNodeWithTag("imported-run-keep").performClick()
        assertThat(kept).isTrue()
    }

    @Test
    fun `explains that the run stays out of today when the session is done`() {
        var kept = false
        compose.setContent {
            McTheme(darkTheme = true) {
                ImportedRunPromptDialog(
                    state = ImportedRunPromptState(
                        prompt = ImportedRunPromptUi.AlreadyDone(
                            activityId = "activity-1",
                            activityTitle = "Corrida",
                            activityDetail = "6,00 km · 40:00",
                            evidenceLabel = "sem registro de corrida",
                            canReplace = false,
                        ),
                    ),
                    onLink = {},
                    onReplaceManualRecord = {},
                    onKeepAsFreeRun = { kept = true },
                    onConsumeMessage = {},
                )
            }
        }

        compose.onNodeWithText("Treino de hoje já concluído").assertIsDisplayed()
        compose
            .onNodeWithText("O coach não vai usá-la como o treino do dia.", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Usar como treino de hoje").assertDoesNotExist()
        compose.onNodeWithTag("imported-run-keep").performClick()
        assertThat(kept).isTrue()
    }

    @Test
    fun `offers to correct a manual record with the real run`() {
        var replaced = false
        compose.setContent {
            McTheme(darkTheme = true) {
                ImportedRunPromptDialog(
                    state = ImportedRunPromptState(
                        prompt = ImportedRunPromptUi.AlreadyDone(
                            activityId = "activity-1",
                            activityTitle = "Corrida",
                            activityDetail = "6,00 km · 40:00",
                            evidenceLabel = "sem registro de corrida",
                            canReplace = true,
                        ),
                    ),
                    onLink = {},
                    onReplaceManualRecord = { replaced = true },
                    onKeepAsFreeRun = {},
                    onConsumeMessage = {},
                )
            }
        }

        compose.onNodeWithText("Corrigir registro do treino").assertIsDisplayed()
        compose
            .onNodeWithText("substituir o registro manual", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithTag("imported-run-replace").performClick()

        assertThat(replaced).isTrue()
    }
}
