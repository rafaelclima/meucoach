package com.meucoach.app.ui.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McDangerButton
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.component.McSectionHeader
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import java.time.LocalDate

private const val EXPORT_MIME = "application/json"

@Composable
fun DataManagementScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DataManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(EXPORT_MIME),
        onResult = { uri -> uri?.let(viewModel::export) },
    )

    DataManagementContent(
        state = state,
        onExport = { createDocument.launch(exportFileName(viewModel.currentDate())) },
        onDeleteAll = viewModel::deleteAllData,
        onRestoreTrash = viewModel::restoreActivity,
        onDeleteTrash = viewModel::deletePermanently,
        onToggleIncludeTrash = viewModel::toggleIncludeTrash,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun DataManagementContent(
    state: DataManagementUiState,
    onExport: () -> Unit,
    onDeleteAll: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRestoreTrash: (String) -> Unit = {},
    onDeleteTrash: (String) -> Unit = {},
    onToggleIncludeTrash: () -> Unit = {},
) {
    val colors = McTheme.colors
    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingPurge by remember { mutableStateOf<TrashItemUi?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 20.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Voltar",
                        tint = colors.textPrimary,
                    )
                }
                Text(text = "DADOS", style = McTextStyles.overline, color = colors.textTertiary)
            }

            LazyColumn(
                modifier = Modifier.testTag("data-management-list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Seus dados",
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Tudo o que o app guarda fica neste aparelho. " +
                                "Você pode levar um backup ou apagar tudo quando quiser.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
                item {
                    McCard(variant = McCardVariant.INSET) {
                        Text(
                            text = "Exportar backup",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Um arquivo JSON com perfil, corridas, check-ins, métricas diárias e planos. " +
                                "As amostras aparecem só como contagem; o blob comprimido não sai do app.",
                            style = McTextStyles.caption,
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Incluir itens da lixeira",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = "Desmarcado, o backup não contém corridas apagadas.",
                                    style = McTextStyles.caption,
                                    color = colors.textTertiary,
                                )
                            }
                            Switch(
                                checked = state.includeTrash,
                                onCheckedChange = { onToggleIncludeTrash() },
                                enabled = !state.isBusy,
                                modifier = Modifier.testTag("data-include-trash"),
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        McSecondaryButton(
                            text = if (state.isExporting) "Exportando..." else "Exportar JSON",
                            onClick = onExport,
                            enabled = !state.isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("data-export-button"),
                        )
                    }
                }
                if (state.trash.isNotEmpty()) {
                    item { McSectionHeader(title = "Lixeira · 7 dias") }
                    item {
                        McCard(variant = McCardVariant.INSET) {
                            Text(
                                text = "Corridas apagadas ficam aqui por 7 dias. Depois disso, " +
                                    "são removidas definitivamente do aparelho.",
                                style = McTextStyles.caption,
                                color = colors.textSecondary,
                            )
                        }
                    }
                    items(state.trash, key = { it.id }) { item ->
                        TrashCard(
                            item = item,
                            onRestore = { onRestoreTrash(item.id) },
                            onDelete = { confirmingPurge = item },
                        )
                    }
                }
                item {
                    McCard(variant = McCardVariant.INSET) {
                        Text(
                            text = "Apagar todos os dados",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Remove deste aparelho o perfil, as corridas, os check-ins, as métricas diárias " +
                                "e os planos, além de caches e arquivos internos do app. O Health Connect " +
                                "não é alterado. O app volta para a configuração inicial e não dá para desfazer.",
                            style = McTextStyles.caption,
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        McDangerButton(
                            text = if (state.isDeleting) "Apagando..." else "Apagar tudo",
                            onClick = { confirmingDelete = true },
                            enabled = !state.isBusy,
                            leadingIcon = Icons.Outlined.DeleteOutline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("data-delete-button"),
                        )
                    }
                }
                state.message?.let { message ->
                    item {
                        McCard(variant = McCardVariant.INSET) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.success,
                            )
                        }
                    }
                }
                state.error?.let { error ->
                    item {
                        McCard(variant = McCardVariant.INSET) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.danger,
                            )
                        }
                    }
                }
                item {
                    Text(
                        text = "Sem nuvem, sem conta e sem analytics. " +
                            "O backup só existe depois que você escolhe onde salvar.",
                        style = McTextStyles.caption,
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(text = "Apagar todos os dados?") },
            text = {
                Text(
                    text = "Perfil, corridas, check-ins, métricas, planos, caches e arquivos internos serão " +
                        "removidos deste aparelho. O Health Connect não é alterado. " +
                        "Exporte um backup antes se quiser guardar o histórico.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDeleteAll()
                    },
                    modifier = Modifier.testTag("data-delete-confirm"),
                ) {
                    Text(text = "Apagar tudo", color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(text = "Cancelar", color = colors.textPrimary)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
        )
    }

    confirmingPurge?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmingPurge = null },
            title = { Text(text = "Apagar definitivamente?") },
            text = {
                Text(
                    text = "${item.title} será removida agora, sem passar pelos 7 dias da lixeira. " +
                        "Essa ação não pode ser desfeita.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingPurge = null
                        onDeleteTrash(item.id)
                    },
                    modifier = Modifier.testTag("trash-delete-confirm"),
                ) {
                    Text(text = "Apagar", color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingPurge = null }) {
                    Text(text = "Cancelar", color = colors.textPrimary)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
        )
    }
}

@Composable
private fun TrashCard(item: TrashItemUi, onRestore: () -> Unit, onDelete: () -> Unit) {
    val colors = McTheme.colors
    McCard(
        variant = McCardVariant.INSET,
        modifier = Modifier.testTag("trash-item-${item.id}"),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = item.detail,
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (item.daysRemaining > 0) {
                "Expira em ${item.daysRemaining} dia(s)."
            } else {
                "Expira hoje; será removida no próximo expurgo."
            },
            style = McTextStyles.caption,
            color = colors.warning,
        )
        Spacer(modifier = Modifier.height(8.dp))
        McDivider()
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            McTextButton(
                text = "Restaurar",
                onClick = onRestore,
                trailingArrow = false,
                modifier = Modifier.testTag("trash-restore-${item.id}"),
            )
            McTextButton(
                text = "Apagar definitivamente",
                onClick = onDelete,
                trailingArrow = false,
                contentColor = colors.danger,
                modifier = Modifier.testTag("trash-delete-${item.id}"),
            )
        }
    }
}

internal fun exportFileName(date: LocalDate): String = "meucoach-backup-$date.json"
