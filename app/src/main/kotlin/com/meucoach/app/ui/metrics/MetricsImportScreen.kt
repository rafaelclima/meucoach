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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme

@Composable
fun MetricsImportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MetricsImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(viewModel::import) },
    )

    MetricsImportContent(
        state = state,
        onPick = { launcher.launch(CSV_MIME_TYPES) },
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
) {
    val colors = McTheme.colors
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
                            text = "Importe sono, FC de repouso, HRV e SpO2 de um CSV. " +
                                "O arquivo é lido no aparelho e não sai daqui.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
                item {
                    McCard(variant = McCardVariant.INSET) {
                        Text(
                            text = "Formato esperado",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
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
                item {
                    McPrimaryButton(
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

private val CSV_MIME_TYPES = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")
