package com.meucoach.app.ui.tutorial

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
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
class TutorialScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `walks through the pages and finishes`() {
        var finished = false
        setContent(onFinish = { finished = true })

        compose.onNodeWithText("Seu coach de corrida").assertIsDisplayed()

        compose.onNodeWithTag("tutorial-next").performClick()
        compose.onNodeWithText("Quatro abas, um coach").assertIsDisplayed()

        compose.onNodeWithTag("tutorial-next").performClick()
        compose.onNodeWithText("O que mantém o coach informado").assertIsDisplayed()

        compose.onNodeWithTag("tutorial-next").performClick()
        compose.onNodeWithText("Nada sai do aparelho").assertIsDisplayed()

        compose.onNodeWithText("Tudo pronto").performClick()
        assertThat(finished).isTrue()
    }

    @Test
    fun `goes back to the previous page`() {
        setContent()

        compose.onNodeWithTag("tutorial-next").performClick()
        compose.onNodeWithText("Quatro abas, um coach").assertIsDisplayed()
        compose.onNodeWithTag("tutorial-back").performClick()

        compose.onNodeWithText("Seu coach de corrida").assertIsDisplayed()
    }

    @Test
    fun `skip reports dismissal`() {
        var dismissed = false
        setContent(onDismiss = { dismissed = true })

        compose.onNodeWithTag("tutorial-skip").performClick()

        assertThat(dismissed).isTrue()
    }

    private fun setContent(onFinish: () -> Unit = {}, onDismiss: () -> Unit = {}) {
        compose.setContent {
            McTheme(darkTheme = true) {
                TutorialContent(onFinish = onFinish, onDismiss = onDismiss)
            }
        }
    }
}
