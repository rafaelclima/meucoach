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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.meucoach.core.designsystem.component.McSecondaryButton
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
        onExport = { createDocument.launch(exportFileName(LocalDate.now())) },
        onDeleteAll = viewModel::deleteAllData,
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
) {
    val colors = McTheme.colors
    var confirmingDelete by remember { mutableStateOf(false) }

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
                                "e os planos. O Health Connect e os arquivos originais não são alterados. " +
                                "O app volta para a configuração inicial e não dá para desfazer.",
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
                    text = "Perfil, corridas, check-ins, métricas e planos serão removidos deste aparelho. " +
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
}

internal fun exportFileName(date: LocalDate): String = "meucoach-backup-$date.json"
