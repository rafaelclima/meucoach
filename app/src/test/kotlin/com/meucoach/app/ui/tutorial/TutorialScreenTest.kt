package com.meucoach.app.ui.tutorial

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
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
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TutorialScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `walks through the three scenes and reaches the first action`() {
        var checkedIn = false
        setContent(onCheckIn = { checkedIn = true })

        compose.onNodeWithText("Rafael, sua semana está pronta.").assertIsDisplayed()

        compose.onNodeWithTag("tutorial-secondary-plan").performClick()
        compose.onNodeWithText("Você corre. Eu ajusto.").assertIsDisplayed()

        compose.onNodeWithTag("tutorial-next").performClick()
        compose.onNodeWithText("Comece com 30 segundos.").assertIsDisplayed()
        compose.onNodeWithText("Fazer check-in agora").performClick()

        assertThat(checkedIn).isTrue()
    }

    @Test
    fun `finishes from the plan reveal`() {
        var finished = false
        setContent(onFinish = { finished = true })

        compose.onNodeWithText("Ver meu treino de hoje").performClick()

        assertThat(finished).isTrue()
    }

    @Test
    fun `shows a readable step progress`() {
        setContent()

        compose.onNodeWithTag("tutorial-progress").assertContentDescriptionEquals("Passo 1 de 3")

        compose.onNodeWithTag("tutorial-secondary-plan").performClick()

        compose.onNodeWithTag("tutorial-progress").assertContentDescriptionEquals("Passo 2 de 3")
    }

    @Test
    fun `goes back to the previous scene`() {
        setContent()

        compose.onNodeWithTag("tutorial-secondary-plan").performClick()
        compose.onNodeWithText("Você corre. Eu ajusto.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Voltar").performClick()

        compose.onNodeWithText("Rafael, sua semana está pronta.").assertIsDisplayed()
    }

    @Test
    fun `skip reports dismissal`() {
        var dismissed = false
        setContent(onDismiss = { dismissed = true })

        compose.onNodeWithTag("tutorial-skip").performClick()

        assertThat(dismissed).isTrue()
    }

    @Test
    fun `first action prioritizes the check-in`() {
        var checkedIn = false
        setContent(onCheckIn = { checkedIn = true })
        goToFirstAction()

        compose.onNodeWithText("Fazer check-in agora").assertIsDisplayed()
        compose.onNodeWithTag("tutorial-next").performClick()

        assertThat(checkedIn).isTrue()
    }

    @Test
    fun `first action switches to import after the check-in`() {
        var imported = false
        setContent(
            state = tutorialState(hasCheckedInToday = true, hasActivities = false),
            onImport = { imported = true },
        )
        goToFirstAction()

        compose.onNodeWithTag("tutorial-next").assertTextEquals("Importar uma corrida")
        compose.onNodeWithTag("tutorial-next").performClick()

        assertThat(imported).isTrue()
    }

    @Test
    fun `first action switches to health after check-in and activity`() {
        var health = false
        setContent(
            state = tutorialState(hasCheckedInToday = true, hasActivities = true, healthConnected = false),
            onHealth = { health = true },
        )
        goToFirstAction()

        compose.onNodeWithTag("tutorial-next").assertTextEquals("Conectar Health Connect")
        compose.onNodeWithTag("tutorial-next").performClick()

        assertThat(health).isTrue()
    }

    @Test
    fun `first action finishes when everything is done`() {
        var finished = false
        setContent(
            state = tutorialState(hasCheckedInToday = true, hasActivities = true, healthConnected = true),
            onFinish = { finished = true },
        )
        goToFirstAction()

        compose.onNodeWithText("Ver meu treino de hoje").assertIsDisplayed()
        compose.onNodeWithTag("tutorial-next").performClick()

        assertThat(finished).isTrue()
    }

    @Test
    fun `keeps the primary action visible at 200 percent font scale`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                McTheme(darkTheme = true) {
                    TutorialContent(
                        state = tutorialState(
                            hasCheckedInToday = true,
                            hasActivities = true,
                            healthConnected = false,
                        ),
                        onFinish = {},
                        onDismiss = {},
                        onCheckIn = {},
                        onImport = {},
                        onHealth = {},
                        onRetry = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("tutorial-next").assertIsDisplayed()
        compose.onNodeWithTag("tutorial-page-list-1")
            .performScrollToNode(hasText("Como o coach pensa"))
        compose.onNodeWithTag("tutorial-secondary-plan").performClick()
        compose.onNodeWithTag("tutorial-next").performClick()
        compose.onNodeWithTag("tutorial-page-3")
            .performScrollToNode(hasText("Conectar Health Connect"))
        compose.onNodeWithTag("tutorial-next").assertTextEquals("Conectar Health Connect")
    }

    @Test
    fun `shows the loading fallback`() {
        setContent(state = tutorialState(isLoading = true))

        compose.onNodeWithText("Montando sua semana…").assertIsDisplayed()
    }

    @Test
    fun `shows the error fallback and retry`() {
        var retried = false
        setContent(state = tutorialState(error = "Falha ao carregar"), onRetry = { retried = true })

        compose.onNodeWithText("Não consegui carregar seu plano agora.").assertIsDisplayed()
        compose.onNodeWithTag("tutorial-retry").performClick()

        assertThat(retried).isTrue()
    }

    private fun goToFirstAction() {
        compose.onNodeWithTag("tutorial-page-list-1")
            .performScrollToNode(hasText("Como o coach pensa"))
        compose.onNodeWithTag("tutorial-secondary-plan").performClick()
        compose.onNodeWithTag("tutorial-next").performClick()
    }

    private fun setContent(
        state: TutorialUiState = tutorialState(),
        onFinish: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onCheckIn: () -> Unit = {},
        onImport: () -> Unit = {},
        onHealth: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        compose.setContent {
            McTheme(darkTheme = true) {
                TutorialContent(
                    state = state,
                    onFinish = onFinish,
                    onDismiss = onDismiss,
                    onCheckIn = onCheckIn,
                    onImport = onImport,
                    onHealth = onHealth,
                    onRetry = onRetry,
                )
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class TutorialSystemBackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `system back on the first page dismisses the tutorial`() {
        var dismissed = false
        compose.setContent {
            McTheme(darkTheme = true) {
                TutorialContent(
                    state = tutorialState(),
                    onFinish = {},
                    onDismiss = { dismissed = true },
                    onCheckIn = {},
                    onImport = {},
                    onHealth = {},
                    onRetry = {},
                )
            }
        }

        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()

        assertThat(dismissed).isTrue()
    }
}
