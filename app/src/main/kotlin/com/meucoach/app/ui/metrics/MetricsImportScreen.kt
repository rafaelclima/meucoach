package com.meucoach.app.ui.metrics

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.app.ui.common.healthConnectLastSyncLabel
import com.meucoach.app.ui.common.healthConnectStatusLabel
import com.meucoach.app.ui.common.isHealthConnectConnected
import com.meucoach.core.data.health.HealthConnectStatus
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McPill
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
    val context = LocalContext.current
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
        onInstallHealthConnect = { openHealthConnectStore(context) },
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
    onInstallHealthConnect: () -> Unit = {},
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
                Text(text = "SAÚDE E DADOS", style = McTextStyles.overline, color = colors.textTertiary)
            }

            LazyColumn(
                modifier = Modifier.testTag("metrics-import-list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Seus dados de saúde",
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Sono e recuperação entram na prontidão. Conecte uma fonte e o " +
                                "coach passa a usar seus dados todos os dias.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
                item {
                    HealthConnectContent(
                        state = healthState,
                        storedDays = state.storedDays,
                        onAuthorize = onAuthorize,
                        onSync = onSync,
                        onInstall = onInstallHealthConnect,
                    )
                }
                item { WhyItMattersCard() }
                item {
                    McCard(variant = McCardVariant.INSET) {
                        Text(
                            text = "Sem Health Connect? Use um CSV",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Uma planilha com data, sono, FC de repouso, variabilidade " +
                                "cardíaca e oxigenação. Só a data é obrigatória.",
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
                                text = "Datas ISO ou dd/mm/aaaa. Campos vazios continuam ausentes.",
                                style = McTextStyles.caption,
                                color = colors.textTertiary,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        McSecondaryButton(
                            text = if (state.isImporting) "Lendo..." else "Escolher CSV",
                            onClick = onPick,
                            enabled = !state.isImporting,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
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
                            text = "O que já está salvo",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (state.storedDays == 0) {
                                "Nenhum dia ainda. Conecte o Health Connect ou importe um CSV."
                            } else {
                                "${state.storedDays} dias de sono e recuperação · última em " +
                                    (state.latestMetricDate?.format(DATE_FORMAT) ?: "—")
                            },
                            style = McTextStyles.caption,
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "A confiança de cada métrica aparece na tela Hoje.",
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

/**
 * Explains the benefit before the mechanics. The example uses the same
 * guardrails the engine already applies, so the promise is auditable.
 */
@Composable
private fun WhyItMattersCard() {
    val colors = McTheme.colors
    McCard {
        Text(
            text = "O que isso muda no treino",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        BenefitRow(
            title = "Prontidão mais precisa",
            description = "Sono e FC de repouso mostram se o corpo está pronto para o treino de hoje.",
        )
        Spacer(modifier = Modifier.height(10.dp))
        BenefitRow(
            title = "Menos risco de exagerar",
            description = "Variabilidade cardíaca e oxigenação ajudam a notar o cansaço antes da lesão.",
        )
        Spacer(modifier = Modifier.height(10.dp))
        BenefitRow(
            title = "Exemplo",
            description = "Sono curto e FC de repouso acima da média? O treino forte de hoje vira leve.",
        )
    }
}

@Composable
private fun BenefitRow(title: String, description: String) {
    val colors = McTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(5.dp)
                .background(colors.accent, CircleShape),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Text(
                text = description,
                style = McTextStyles.caption,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
internal fun HealthConnectContent(
    state: HealthConnectUiState,
    onAuthorize: () -> Unit,
    onSync: () -> Unit,
    storedDays: Int = 0,
    onInstall: () -> Unit = {},
) {
    val colors = McTheme.colors
    val available = state.status == HealthConnectStatus.AVAILABLE
    val connected = isHealthConnectConnected(state.status, state.hasPermissions)
    McCard(variant = McCardVariant.INSET) {
        Text(
            text = "Health Connect",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        McPill(
            text = if (state.isLoading) {
                "Verificando..."
            } else {
                healthConnectStatusLabel(state.status, state.hasPermissions, storedDays)
            },
            containerColor = if (connected) colors.accentSoft else colors.surface,
            contentColor = if (connected) colors.accentText else colors.textSecondary,
            modifier = Modifier.testTag("health-connect-status"),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "É o app de saúde do Android. Reúne o que seu relógio (Zepp, Garmin, " +
                "Samsung) e outros apps registram.",
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "O APP LÊ",
            style = McTextStyles.overline,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Sono · FC de repouso · variabilidade cardíaca (HRV) · oxigenação (SpO₂).",
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "O MeuCoach só lê, nunca escreve. A primeira conexão traz 30 dias; depois, " +
                "só o que ainda falta. Sincroniza sozinho uma vez por dia.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        healthConnectLastSyncLabel(state.lastSyncDate)?.let { label ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, style = McTextStyles.caption, color = colors.textTertiary)
        }
        state.message?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message, style = McTextStyles.caption, color = colors.textSecondary)
        }
        if (!state.isLoading) {
            Spacer(modifier = Modifier.height(12.dp))
            when {
                available && state.hasPermissions -> McPrimaryButton(
                    text = if (state.isSyncing) "Sincronizando..." else "Atualizar agora",
                    onClick = onSync,
                    enabled = !state.isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("health-connect-sync"),
                )

                available -> McPrimaryButton(
                    text = "Conectar Health Connect",
                    onClick = onAuthorize,
                    enabled = !state.isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("health-connect-authorize"),
                )

                else -> McSecondaryButton(
                    text = if (state.status == HealthConnectStatus.UPDATE_REQUIRED) {
                        "Atualizar o Health Connect"
                    } else {
                        "Instalar o Health Connect"
                    },
                    onClick = onInstall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("health-connect-install"),
                )
            }
            if (!available) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Grátis, do Google. No Android 14 ou mais novo, já vem no sistema.",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

private fun openHealthConnectStore(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE"))
    val web = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE"),
    )
    runCatching { context.startActivity(market) }.onFailure {
        runCatching { context.startActivity(web) }
    }
}

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
private val CSV_MIME_TYPES = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")
private val DATE_FORMAT = java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")
