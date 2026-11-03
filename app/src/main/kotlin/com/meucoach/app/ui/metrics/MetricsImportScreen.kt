package com.meucoach.app.ui.metrics

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme

@Composable
fun MetricsImportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MetricsImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val healthViewModel: HealthConnectViewModel = hiltViewModel()
    val healthState by healthViewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(viewModel::import) },
    )
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
        onResult = { healthViewModel.onPermissionsResult() },
    )

    MetricsImportContent(
        state = state,
        healthState = healthState,
        onPick = { launcher.launch(CSV_MIME_TYPES) },
        onAuthorize = { permissionLauncher.launch(healthState.requiredPermissions) },
        onSync = healthViewModel::sync,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun MetricsImportContent(
    state: MetricsImportUiState,
    onPick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    healthState: HealthConnectUiState = HealthConnectUiState(),
    onAuthorize: () -> Unit = {},
    onSync: () -> Unit = {},
) {
    val colors = McTheme.colors
    var showCsvFormat by remember { mutableStateOf(false) }
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
                modifier = Modifier.testTag("metrics-import-list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Métricas diárias",
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Sono, FC de repouso, HRV e SpO₂, do Health Connect ou de um CSV lido no aparelho.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
                item {
                    HealthConnectContent(
                        state = healthState,
                        onAuthorize = onAuthorize,
                        onSync = onSync,
                    )
                }
                item {
                    McCard(variant = McCardVariant.INSET) {
                        Text(
                            text = "Formato do CSV",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Data, sono, FC de repouso, HRV e SpO₂.",
                            style = McTextStyles.caption,
                            color = colors.textSecondary,
                        )
                        McTextButton(
                            text = if (showCsvFormat) "Ocultar formato" else "Ver formato do CSV",
                            onClick = { showCsvFormat = !showCsvFormat },
                            trailingArrow = false,
                            modifier = Modifier.testTag("csv-format-toggle"),
                        )
                        if (showCsvFormat) {
                            Text(
                                text = "date,sleep_minutes,resting_heart_rate,hrv_rmssd,spo2\n" +
                                    "2026-10-30,450,58,60,97",
                                style = McTextStyles.caption,
                                color = colors.textSecondary,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Só a coluna date é obrigatória. Datas ISO ou dd/mm/aaaa. " +
                                    "Campos vazios continuam ausentes.",
                                style = McTextStyles.caption,
                                color = colors.textTertiary,
                            )
                        }
                    }
                }
                item {
                    McSecondaryButton(
                        text = if (state.isImporting) "Lendo..." else "Escolher CSV",
                        onClick = onPick,
                        enabled = !state.isImporting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.message?.let { message ->
                    item {
                        McCard(variant = McCardVariant.INSET) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (state.errors.isEmpty()) colors.success else colors.warning,
                            )
                            if (state.duplicateDates > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${state.duplicateDates} datas duplicadas; a última linha venceu.",
                                    style = McTextStyles.caption,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
                item {
                    McCard(variant = McCardVariant.INSET) {
                        Text(
                            text = "Diagnóstico local",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (state.storedDays == 0) {
                                "Nenhum dia de métricas salvo."
                            } else {
                                "${state.storedDays} dias salvos · última em " +
                                    (state.latestMetricDate?.format(DATE_FORMAT) ?: "—")
                            },
                            style = McTextStyles.caption,
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "A confiança por componente aparece na tela Hoje.",
                            style = McTextStyles.caption,
                            color = colors.textTertiary,
                        )
                    }
                }
                if (state.errors.isNotEmpty()) {
                    item {
                        Text(
                            text = "Linhas ignoradas",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.warning,
                        )
                    }
                    items(state.errors.take(10)) { error ->
                        Text(
                            text = "Linha ${error.line}: ${error.message}",
                            style = McTextStyles.caption,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HealthConnectContent(state: HealthConnectUiState, onAuthorize: () -> Unit, onSync: () -> Unit) {
    val colors = McTheme.colors
    McCard(variant = McCardVariant.INSET) {
        Text(
            text = "Health Connect",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = when {
                state.isLoading -> "Verificando..."

                state.status == com.meucoach.core.data.health.HealthConnectStatus.AVAILABLE -> "Disponível"

                state.status == com.meucoach.core.data.health.HealthConnectStatus.UPDATE_REQUIRED ->
                    "Atualize o Health Connect"

                else -> "Indisponível neste aparelho"
            },
            style = McTextStyles.caption,
            color = if (state.status == com.meucoach.core.data.health.HealthConnectStatus.AVAILABLE) {
                colors.success
            } else {
                colors.textTertiary
            },
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Sono, FC de repouso, HRV e SpO₂",
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Leitura local e incremental dos últimos 30 dias. O app nunca escreve no Health Connect.",
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
        state.message?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message, style = McTextStyles.caption, color = colors.textSecondary)
        }
        if (state.status == com.meucoach.core.data.health.HealthConnectStatus.AVAILABLE && !state.isLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            if (state.hasPermissions) {
                McPrimaryButton(
                    text = if (state.isSyncing) "Sincronizando..." else "Sincronizar agora",
                    onClick = onSync,
                    enabled = !state.isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                McSecondaryButton(
                    text = "Autorizar leitura",
                    onClick = onAuthorize,
                    enabled = !state.isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val CSV_MIME_TYPES = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")
private val DATE_FORMAT = java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")
