package com.meucoach.app.ui.metrics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.importer.CsvRowError
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
class MetricsImportScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shows the template and opens the picker`() {
        var picked = false
        setContent(MetricsImportUiState(), onPick = { picked = true })

        compose.onNodeWithText("Métricas diárias").assertIsDisplayed()
        compose.onNodeWithTag("metrics-import-list").performScrollToNode(hasText("Formato do CSV"))
        compose.onNodeWithText("Formato do CSV").assertIsDisplayed()
        compose.onNodeWithText("date,sleep_minutes", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Ver formato do CSV").performClick()
        compose.onNodeWithText("date,sleep_minutes", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("metrics-import-list").performScrollToNode(hasText("Escolher CSV"))
        compose.onNodeWithText("Escolher CSV").performClick()

        assertThat(picked).isTrue()
    }

    @Test
    fun `shows imported rows and errors`() {
        setContent(
            MetricsImportUiState(
                importedDays = 2,
                duplicateDates = 1,
                message = "2 dias importados.",
                errors = listOf(CsvRowError(3, "Data inválida.")),
            ),
        )

        compose.onNodeWithTag("metrics-import-list")
            .performScrollToNode(hasText("2 dias importados."))
        compose.onNodeWithText("2 dias importados.").assertIsDisplayed()
        compose.onNodeWithTag("metrics-import-list")
            .performScrollToNode(hasText("Linhas ignoradas"))
        compose.onNodeWithText("Linhas ignoradas").assertIsDisplayed()
        compose.onNodeWithTag("metrics-import-list")
            .performScrollToNode(hasText("Linha 3: Data inválida."))
        compose.onNodeWithText("Linha 3: Data inválida.").assertIsDisplayed()
    }

    private fun setContent(state: MetricsImportUiState, onPick: () -> Unit = {}) {
        compose.setContent {
            McTheme(darkTheme = true) {
                MetricsImportContent(state = state, onPick = onPick, onBack = {})
            }
        }
    }
}
