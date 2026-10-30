package com.meucoach.app.ui.activities

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.core.data.db.entity.ImportJobStatus
import com.meucoach.core.data.repository.ImportJobSummary
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McEmptyState
import com.meucoach.core.designsystem.component.McPill
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSectionHeader
import com.meucoach.core.designsystem.component.McStat
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.ActivitySummary

@Composable
fun ActivitiesScreen(modifier: Modifier = Modifier, viewModel: ActivitiesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = viewModel::import,
    )

    ActivitiesContent(
        state = state,
        onImportClick = { launcher.launch(IMPORT_MIME_TYPES) },
        onMessageShown = viewModel::consumeMessage,
        modifier = modifier,
    )
}

@Composable
internal fun ActivitiesContent(
    state: ActivitiesUiState,
    onImportClick: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ActivitiesHeader(state = state, onImportClick = onImportClick)
            }
            when {
                state.isLoading -> item { LoadingRow() }

                state.activities.isEmpty() -> item { EmptyActivities() }

                else -> {
                    item { McSectionHeader(title = "Corridas") }
                    items(state.activities, key = { it.id }) { activity ->
                        ActivityCard(activity)
                    }
                }
            }
            if (state.recentImports.isNotEmpty()) {
                item { McSectionHeader(title = "Últimos imports") }
                items(state.recentImports, key = { it.id }) { job ->
                    ImportJobCard(job)
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun ActivitiesHeader(state: ActivitiesUiState, onImportClick: () -> Unit) {
    val colors = McTheme.colors
    Column {
        Text(text = "ATIVIDADES", style = McTextStyles.overline, color = colors.textTertiary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Suas corridas",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Importe arquivos FIT, TCX ou GPX. Tudo é lido no aparelho, sem enviar dados para a nuvem.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        McPrimaryButton(
            text = if (state.isImporting) "Preparando arquivos..." else "Importar arquivo",
            onClick = onImportClick,
            enabled = !state.isImporting,
            leadingIcon = Icons.Outlined.Add,
        )
    }
}

@Composable
private fun LoadingRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            color = McTheme.colors.accent,
            strokeWidth = 2.dp,
        )
    }
}

@Composable
private fun EmptyActivities() {
    McEmptyState(
        icon = Icons.AutoMirrored.Outlined.DirectionsRun,
        title = "Nenhuma corrida importada",
        description = "Selecione um ou mais arquivos para começar. Você também pode compartilhar " +
            "um arquivo FIT, TCX ou GPX direto para o MeuCoach.",
    )
}

@Composable
private fun ActivityCard(activity: ActivitySummary) {
    val colors = McTheme.colors
    McCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sportLabel(activity.sport),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatDateTime(activity.startTime),
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
            }
            McPill(text = sourceLabel(activity.source))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            McStat(
                label = "Distância",
                value = formatDistance(activity.distance),
                unit = "km",
                modifier = Modifier.weight(1f),
            )
            McStat(
                label = "Tempo",
                value = formatDuration(activity.durationSec),
                modifier = Modifier.weight(1f),
            )
            McStat(
                label = "Pace",
                value = paceLabel(activity.averagePace),
                unit = if (activity.averagePace != null) "/km" else null,
                modifier = Modifier.weight(1f),
            )
        }
        val details = buildList {
            heartRateLabel(activity.averageHeartRate, activity.maxHeartRate)?.let { add("FC $it bpm") }
            activity.averageCadenceSpm?.let { add("Cadência $it spm") }
        }
        if (details.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = details.joinToString(" · "),
                style = McTextStyles.caption,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ImportJobCard(job: ImportJobSummary) {
    val colors = McTheme.colors
    val tone = when (job.status) {
        ImportJobStatus.QUEUED, ImportJobStatus.PARSING -> colors.warning
        ImportJobStatus.DONE -> colors.success
        ImportJobStatus.DUPLICATE -> colors.textSecondary
        ImportJobStatus.FAILED -> colors.danger
    }
    McCard(variant = McCardVariant.INSET) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = importJobDetail(job),
                    style = McTextStyles.caption,
                    color = if (job.status == ImportJobStatus.FAILED) colors.danger else colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            McPill(
                text = importStatusLabel(job.status),
                containerColor = tone.copy(alpha = 0.12f),
                contentColor = tone,
            )
        }
    }
}

private val IMPORT_MIME_TYPES = arrayOf("*/*")
