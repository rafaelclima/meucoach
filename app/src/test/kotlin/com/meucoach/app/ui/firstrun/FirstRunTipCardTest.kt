package com.meucoach.app.ui.firstrun

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FirstRunTipCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `runs the action and hides`() {
        val tipId = "tip-action-${System.nanoTime()}"
        var acted = false

        compose.setContent {
            McTheme(darkTheme = true) {
                FirstRunTipCard(
                    tipId = tipId,
                    title = "Prontidão atualizada",
                    body = "O treino de hoje já considera este check-in.",
                    actionLabel = "Ver treino",
                    onAction = { acted = true },
                )
            }
        }

        compose.onNodeWithTag("first-run-tip-$tipId").assertIsDisplayed()
        compose.onNodeWithTag("first-run-tip-action-$tipId").performClick()

        assertThat(acted).isTrue()
        compose.onNodeWithTag("first-run-tip-$tipId").assertDoesNotExist()
    }

    @Test
    fun `dismisses without running the action`() {
        val tipId = "tip-dismiss-${System.nanoTime()}"
        var acted = false

        compose.setContent {
            McTheme(darkTheme = true) {
                FirstRunTipCard(
                    tipId = tipId,
                    title = "Corrida importada",
                    body = "Toque na corrida para ver o porquê.",
                    actionLabel = "Ver corrida",
                    onAction = { acted = true },
                )
            }
        }

        compose.onNodeWithTag("first-run-tip-dismiss-$tipId").performClick()

        assertThat(acted).isFalse()
        compose.onNodeWithTag("first-run-tip-$tipId").assertDoesNotExist()
    }
}
