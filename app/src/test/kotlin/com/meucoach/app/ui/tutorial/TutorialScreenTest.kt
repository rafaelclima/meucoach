package com.meucoach.app.ui.tutorial

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
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
    fun `shows a readable step progress`() {
        setContent()

        compose.onNodeWithTag("tutorial-progress").assertContentDescriptionEquals("Passo 1 de 4")

        compose.onNodeWithTag("tutorial-next").performClick()

        compose.onNodeWithTag("tutorial-progress").assertContentDescriptionEquals("Passo 2 de 4")
    }

    @Test
    fun `goes back to the previous page`() {
        setContent()

        compose.onNodeWithTag("tutorial-next").performClick()
        compose.onNodeWithText("Quatro abas, um coach").assertIsDisplayed()
        compose.onNodeWithContentDescription("Voltar").performClick()

        compose.onNodeWithText("Seu coach de corrida").assertIsDisplayed()
    }

    @Test
    fun `skip reports dismissal`() {
        var dismissed = false
        setContent(onDismiss = { dismissed = true })

        compose.onNodeWithTag("tutorial-skip").performClick()

        assertThat(dismissed).isTrue()
    }

    @Test
    fun `keeps the primary action visible at 200 percent font scale`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                McTheme(darkTheme = true) {
                    TutorialContent(onFinish = {}, onDismiss = {})
                }
            }
        }

        compose.onNodeWithText("Seu coach de corrida").assertExists()
        compose.onNodeWithTag("tutorial-next").assertIsDisplayed()

        compose.onNodeWithTag("tutorial-next").performClick()
        compose.onNodeWithTag("tutorial-next").performClick()
        compose.onNodeWithTag("tutorial-page-list")
            .performScrollToNode(hasText("Calibração", substring = true))
        compose.onNodeWithText("Calibração", substring = true).assertIsDisplayed()
    }

    private fun setContent(onFinish: () -> Unit = {}, onDismiss: () -> Unit = {}) {
        compose.setContent {
            McTheme(darkTheme = true) {
                TutorialContent(onFinish = onFinish, onDismiss = onDismiss)
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TutorialSystemBackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `system back on the first page dismisses the tutorial`() {
        var dismissed = false
        compose.setContent {
            McTheme(darkTheme = true) {
                TutorialContent(onFinish = {}, onDismiss = { dismissed = true })
            }
        }

        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()

        assertThat(dismissed).isTrue()
    }
}
