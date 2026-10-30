package com.meucoach.app.ui.activitydetail

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.app.ui.activities.formatDateTime
import com.meucoach.app.ui.activities.formatDuration
import com.meucoach.app.ui.activities.paceLabel
import com.meucoach.app.ui.activities.sourcesLabel
import com.meucoach.app.ui.activities.sportLabel
import com.meucoach.core.data.repository.StoredActivity
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McEmptyState
import com.meucoach.core.designsystem.component.McPill
import com.meucoach.core.designsystem.component.McSectionHeader
import com.meucoach.core.designsystem.component.McStat
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import java.time.ZoneId

@Composable
fun ActivityDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ActivityDetailContent(
        state = state,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun ActivityDetailContent(state: ActivityDetailUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(McTheme.colors.background),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("activity-detail-list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DetailHeader(onBack = onBack)
            }
            when {
                state.isLoading -> item { LoadingRow() }

                state.notFound -> item {
                    McEmptyState(
                        icon = Icons.AutoMirrored.Outlined.DirectionsRun,
                        title = "Atividade não encontrada",
                        description = "Ela pode ter sido apagada. Volte para a lista de corridas.",
                        actionLabel = "Voltar",
                        onAction = onBack,
                    )
                }

                state.activity != null -> {
                    item { HeroCard(state.activity) }
                    item { MetricsCard(state.activity) }
                    if (state.activity.imported.samples.size >= 2) {
                        item { ActivityChartCard(samples = state.activity.imported.samples) }
                    }
                    state.hrZones?.let { zones ->
                        state.lthr?.let { lthr ->
                            item { HrZonesCard(distribution = zones, lthr = lthr) }
                        }
                    }
                    if (state.activity.imported.laps.isNotEmpty()) {
                        item { McSectionHeader(title = "Splits") }
                        item { SplitsCard(state.activity) }
                    }
                    if (state.activity.imported.samples.any { it.latitude != null && it.longitude != null }) {
                        item { ActivityRouteCard(samples = state.activity.imported.samples) }
                    }
                    if (state.insights.isNotEmpty()) {
                        item { ActivityInsightsCard(insights = state.insights) }
                    }
                    state.activity.sampleWarning?.let { warning ->
                        item { SampleWarningCard(warning) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(onBack: () -> Unit) {
    val colors = McTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Voltar",
                tint = colors.textPrimary,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "ATIVIDADE", style = McTextStyles.overline, color = colors.textTertiary)
    }
}

@Composable
private fun LoadingRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(24.dp)
                .testTag("activity-detail-loading"),
            color = McTheme.colors.accent,
            strokeWidth = 2.dp,
        )
    }
}

@Composable
private fun HeroCard(activity: StoredActivity) {
    val colors = McTheme.colors
    val imported = activity.imported
    McCard(variant = McCardVariant.ELEVATED) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = sportLabel(imported.sport),
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            McPill(text = sourcesLabel(activitySources(activity)))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatDateTime(imported.startTime, ZoneId.of(activity.zoneId)),
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatDistanceMeters(imported.distanceM),
                style = McTextStyles.metricHero,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "km",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            McStat(
                label = "Tempo",
                value = formatDuration(imported.durationSec),
                modifier = Modifier.weight(1f),
            )
            McStat(
                label = "Pace médio",
                value = paceLabel(imported.averagePace),
                unit = if (imported.averagePace != null) "/km" else null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricsCard(activity: StoredActivity) {
    val metrics = detailMetrics(activity.imported)
    if (metrics.isEmpty()) return
    McCard {
        metrics.chunked(2).forEachIndexed { index, row ->
            if (index > 0) Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { metric ->
                    McStat(
                        label = metric.label,
                        value = metric.value,
                        unit = metric.unit,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SplitsCard(activity: StoredActivity) {
    val colors = McTheme.colors
    val laps = activity.imported.laps
    McCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "SPLIT",
                style = McTextStyles.overline,
                color = colors.textTertiary,
                modifier = Modifier.weight(1.4f),
            )
            Text(
                text = "PACE",
                style = McTextStyles.overline,
                color = colors.textTertiary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "TEMPO",
                style = McTextStyles.overline,
                color = colors.textTertiary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "FC",
                style = McTextStyles.overline,
                color = colors.textTertiary,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.8f),
            )
        }
        laps.forEachIndexed { index, lap ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                McDivider()
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1.4f)) {
                    Text(
                        text = "#${lap.index + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = lapDistanceLabel(lap),
                        style = McTextStyles.caption,
                        color = colors.textTertiary,
                    )
                }
                Text(
                    text = paceLabel(lapPace(lap)),
                    style = McTextStyles.metricSmall,
                    color = colors.textPrimary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatDuration(lap.durationSec),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = lap.averageHeartRate?.toString() ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.8f),
                )
            }
        }
    }
}

@Composable
private fun SampleWarningCard(warning: String) {
    val colors = McTheme.colors
    McCard(variant = McCardVariant.INSET) {
        Text(
            text = "Séries temporais indisponíveis",
            style = MaterialTheme.typography.titleSmall,
            color = colors.warning,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = warning,
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
    }
}
