package com.meucoach.app.ui.data

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.core.designsystem.theme.McTheme
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DataManagementScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `exports and deletes from the data screen`() {
        var export = false
        var delete = false
        setContent(
            state = DataManagementUiState(),
            onExport = { export = true },
            onDeleteAll = { delete = true },
        )

        val list = compose.onNodeWithTag("data-management-list")
        list.performScrollToNode(hasText("Exportar JSON"))
        compose.onNodeWithText("Exportar JSON").performClick()
        assertThat(export).isTrue()

        list.performScrollToNode(hasText("Apagar tudo"))
        compose.onNodeWithText("Apagar tudo").performClick()
        compose.onNodeWithText("Apagar todos os dados?").assertIsDisplayed()
        compose.onNodeWithTag("data-delete-confirm").performClick()

        assertThat(delete).isTrue()
    }

    @Test
    fun `cancelling the dialog keeps the data`() {
        var delete = false
        setContent(
            state = DataManagementUiState(),
            onExport = {},
            onDeleteAll = { delete = true },
        )

        val list = compose.onNodeWithTag("data-management-list")
        list.performScrollToNode(hasText("Apagar tudo"))
        compose.onNodeWithText("Apagar tudo").performClick()
        compose.onNodeWithText("Cancelar").performClick()

        assertThat(delete).isFalse()
    }

    @Test
    fun `shows the export result and disables actions while busy`() {
        setContent(
            state = DataManagementUiState(isExporting = true, message = "Backup exportado."),
            onExport = {},
            onDeleteAll = {},
        )

        val list = compose.onNodeWithTag("data-management-list")
        list.performScrollToNode(hasText("Exportando..."))
        compose.onNodeWithText("Exportando...").assertIsDisplayed()
        list.performScrollToNode(hasText("Backup exportado."))
        compose.onNodeWithText("Backup exportado.").assertIsDisplayed()
    }

    @Test
    fun `shows the trash with restore and permanent delete actions`() {
        var restored: String? = null
        var deleted: String? = null
        setContent(
            state = DataManagementUiState(
                trash = listOf(TrashItemUi("activity-1", "Corrida", "5,00 km", 3)),
            ),
            onExport = {},
            onDeleteAll = {},
            onRestoreTrash = { restored = it },
            onDeleteTrash = { deleted = it },
        )

        val list = compose.onNodeWithTag("data-management-list")
        list.performScrollToNode(hasText("Corrida"))
        compose.onNodeWithTag("trash-restore-activity-1").performClick()
        assertThat(restored).isEqualTo("activity-1")

        compose.onNodeWithTag("trash-delete-activity-1").performClick()
        compose.onNodeWithText("Apagar definitivamente?").assertIsDisplayed()
        compose.onNodeWithTag("trash-delete-confirm").performClick()
        assertThat(deleted).isEqualTo("activity-1")
    }

    @Test
    fun `toggles the trash option for the backup`() {
        var toggled = false
        setContent(
            state = DataManagementUiState(),
            onExport = {},
            onDeleteAll = {},
            onToggleIncludeTrash = { toggled = true },
        )

        compose.onNodeWithTag("data-management-list").performScrollToNode(hasText("Incluir itens da lixeira"))
        compose.onNodeWithTag("data-include-trash").performClick()

        assertThat(toggled).isTrue()
    }

    @Test
    fun `names the backup after the day`() {
        assertThat(exportFileName(LocalDate.of(2026, 11, 3))).isEqualTo("meucoach-backup-2026-11-03.json")
    }

    private fun setContent(
        state: DataManagementUiState,
        onExport: () -> Unit,
        onDeleteAll: () -> Unit,
        onRestoreTrash: (String) -> Unit = {},
        onDeleteTrash: (String) -> Unit = {},
        onToggleIncludeTrash: () -> Unit = {},
    ) {
        compose.setContent {
            McTheme(darkTheme = true) {
                DataManagementContent(
                    state = state,
                    onExport = onExport,
                    onDeleteAll = onDeleteAll,
                    onBack = {},
                    onRestoreTrash = onRestoreTrash,
                    onDeleteTrash = onDeleteTrash,
                    onToggleIncludeTrash = onToggleIncludeTrash,
                )
            }
        }
    }
}
