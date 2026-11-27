package com.meucoach.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.meucoach.core.designsystem.theme.McTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class CycleOverviewSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `ready state answers the cycle without a second prescription`() {
        setContent(CycleOverviewSheetState.Ready(CycleOverviewFixtures.overview()))

        compose.onNodeWithText("CAMINHO ATÉ A PROVA").assertIsDisplayed()
        compose.onNodeWithText("5 km · 10 de janeiro").assertExists()
        compose.onNodeWithText("Faltam 5 semanas para a prova.").assertExists()
        compose.onNodeWithText("120 min").assertExists()
        compose.onNodeWithText("130 min").assertExists()
        compose.onAllNodesWithText("A confirmar").onFirst().assertExists()
        compose.onNodeWithText("Iniciar").assertDoesNotExist()
        compose.onNodeWithText("Concluir").assertDoesNotExist()
    }

    @Test
    fun `future rows do not show minutes`() {
        setContent(CycleOverviewSheetState.Ready(CycleOverviewFixtures.overview()))

        compose.onNodeWithText("TAPER").assertDoesNotExist()
        compose.onNodeWithText("90 min").assertDoesNotExist()
        compose.onAllNodesWithText("Até 2 treinos fortes").onFirst().assertExists()
    }

    @Test
    fun `conditional deload explains the condition and the footer is explicit`() {
        setContent(CycleOverviewSheetState.Ready(CycleOverviewFixtures.overview()))

        compose.onNodeWithTag("cycle-footer").performScrollTo()
        compose.onNodeWithText(
            "Se você completar as próximas 2 semanas de carga, a descarga cai aqui.",
        ).assertExists()
        compose.onNodeWithText("Semanas futuras não contam como histórico.").assertExists()
    }

    @Test
    fun `empty state is honest without a race`() {
        setContent(CycleOverviewSheetState.Empty())

        compose.onNodeWithTag("cycle-empty").assertIsDisplayed()
        compose.onNodeWithText("Sem prova planejada").assertIsDisplayed()
        compose.onNodeWithText(
            "Marque uma prova no Perfil para ver o caminho até ela. " +
                "Sem prova, o app não inventa etapas.",
        ).assertIsDisplayed()
    }

    @Test
    fun `failed state keeps the current plan as authority`() {
        setContent(CycleOverviewSheetState.Failed("Não foi possível calcular o caminho."))

        compose.onNodeWithTag("cycle-error").assertIsDisplayed()
        compose.onNodeWithText(
            "Não foi possível calcular o caminho. O plano da semana continua valendo.",
        ).assertIsDisplayed()
    }

    @Test
    fun `loading state is accessible`() {
        setContent(CycleOverviewSheetState.Loading)

        compose.onNodeWithTag("cycle-loading").assertIsDisplayed()
        compose.onNodeWithText("Montando o caminho até a prova…").assertIsDisplayed()
    }

    @Test
    fun `week row has a merged accessibility description`() {
        setContent(CycleOverviewSheetState.Ready(CycleOverviewFixtures.overview()))

        compose.onNodeWithContentDescription(
            "Semana de 30 de novembro a 6 de dezembro, Base, Constrói, " +
                "1 treino forte: qua, Esta semana",
        ).assertExists()
    }

    private fun setContent(state: CycleOverviewSheetState) {
        compose.setContent {
            McTheme(darkTheme = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(McTheme.colors.surface),
                ) {
                    CycleOverviewContent(state = state, onClose = {})
                }
            }
        }
    }
}
