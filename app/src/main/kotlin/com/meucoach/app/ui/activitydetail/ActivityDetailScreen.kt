package com.meucoach.app.ui.activitydetail

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.app.story.StoryBranding
import com.meucoach.app.story.StoryFrameRenderer
import com.meucoach.app.story.StoryMapStyle
import com.meucoach.app.story.StoryPlan
import com.meucoach.app.story.StorySegmentType
import com.meucoach.app.story.toStoryPalette
import com.meucoach.app.ui.activities.formatDateTime
import com.meucoach.app.ui.activities.formatDuration
import com.meucoach.app.ui.activities.paceLabel
import com.meucoach.app.ui.activities.sourcesLabel
import com.meucoach.app.ui.activities.sportLabel
import com.meucoach.core.data.repository.StoredActivity
import com.meucoach.core.designsystem.R
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McEmptyState
import com.meucoach.core.designsystem.component.McPill
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.component.McSectionHeader
import com.meucoach.core.designsystem.component.McStat
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.calibration.CalibrationCandidate
import com.meucoach.core.domain.story.StoryVerdict
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable
fun ActivityDetailScreen(
    onBack: () -> Unit,
    onCalibrate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var storyStyle by rememberSaveable { mutableStateOf(StoryMapStyle.CLASSIC) }
    val palette = McTheme.colors.toStoryPalette(storyStyle)

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }

    ActivityDetailContent(
        state = state,
        onBack = onBack,
        onCalibrate = onCalibrate,
        onCreateStory = { viewModel.createStory(storyStyle) },
        onExportStory = { viewModel.exportStory(palette) },
        onCancelStory = viewModel::cancelStory,
        onShareStory = { uri -> shareStory(context, uri) },
        onDelete = viewModel::deleteActivity,
        onClearDeleteError = viewModel::clearDeleteError,
        storyStyle = storyStyle,
        onStoryStyleChange = { storyStyle = it },
        onDismissStoryIntro = viewModel::dismissStoryIntro,
        modifier = modifier,
    )
}

@Composable
internal fun ActivityDetailContent(
    state: ActivityDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onCalibrate: (String) -> Unit = {},
    onCreateStory: () -> Unit = {},
    onExportStory: () -> Unit = {},
    onCancelStory: () -> Unit = {},
    onShareStory: (Uri) -> Unit = {},
    onDelete: () -> Unit = {},
    onClearDeleteError: () -> Unit = {},
    storyStyle: StoryMapStyle = StoryMapStyle.CLASSIC,
    onStoryStyleChange: (StoryMapStyle) -> Unit = {},
    onDismissStoryIntro: () -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }

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
                DetailHeader(
                    onBack = onBack,
                    canDelete = state.activity != null,
                    isDeleting = state.isDeleting,
                    onDeleteRequest = { confirmDelete = true },
                )
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
                    state.coachVerdict?.let { verdict ->
                        item { CoachReadingCard(verdict) }
                    }
                    if (state.activity.imported.samples.size >= 2) {
                        item { ActivityChartCard(samples = state.activity.imported.samples) }
                    }
                    state.hrZones?.let { zones ->
                        state.lthr?.let { lthr ->
                            item { HrZonesCard(distribution = zones, lthr = lthr) }
                        }
                    }
                    state.calibrationCandidate?.let { candidate ->
                        item {
                            CalibrationCandidateCard(
                                candidate = candidate,
                                onCalibrate = { onCalibrate(state.activity.id) },
                            )
                        }
                    }
                    if (state.activity.imported.laps.isNotEmpty()) {
                        item { McSectionHeader(title = "Trechos") }
                        item { SplitsCard(state.activity) }
                    }
                    if (state.activity.imported.samples.any { it.latitude != null && it.longitude != null }) {
                        item { ActivityRouteCard(samples = state.activity.imported.samples) }
                        item {
                            StoryCard(
                                state = state,
                                onCreateStory = onCreateStory,
                                onExportStory = onExportStory,
                                onCancelStory = onCancelStory,
                                onShareStory = onShareStory,
                                storyStyle = storyStyle,
                                onStoryStyleChange = onStoryStyleChange,
                                onDismissStoryIntro = onDismissStoryIntro,
                            )
                        }
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

    if (confirmDelete) {
        val colors = McTheme.colors
        AlertDialog(
            onDismissRequest = { if (!state.isDeleting) confirmDelete = false },
            title = { Text(text = "Apagar esta atividade?") },
            text = {
                Text(
                    text = "Ela sai do histórico, das tendências e do cálculo de carga. " +
                        "Essa ação não pode ser desfeita.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    enabled = !state.isDeleting,
                    modifier = Modifier.testTag("activity-delete-confirm"),
                ) {
                    Text(
                        text = if (state.isDeleting) "Apagando..." else "Apagar",
                        color = colors.danger,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                    enabled = !state.isDeleting,
                ) {
                    Text(text = "Cancelar", color = colors.textPrimary)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
        )
    }

    state.deleteError?.let { message ->
        val colors = McTheme.colors
        AlertDialog(
            onDismissRequest = onClearDeleteError,
            title = { Text(text = "Não foi possível apagar") },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(onClick = onClearDeleteError) {
                    Text(text = "Fechar", color = colors.textPrimary)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
        )
    }
}

@Composable
private fun DetailHeader(onBack: () -> Unit, canDelete: Boolean, isDeleting: Boolean, onDeleteRequest: () -> Unit) {
    val colors = McTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Voltar",
                tint = colors.textPrimary,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "ATIVIDADE",
            style = McTextStyles.overline,
            color = colors.textTertiary,
            modifier = Modifier.weight(1f),
        )
        if (canDelete) {
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    enabled = !isDeleting,
                    modifier = Modifier.testTag("activity-menu"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Mais opções",
                        tint = colors.textPrimary,
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = colors.surface,
                ) {
                    DropdownMenuItem(
                        text = { Text(text = "Apagar atividade", color = colors.danger) },
                        onClick = {
                            menuOpen = false
                            onDeleteRequest()
                        },
                        modifier = Modifier.testTag("activity-delete-menu"),
                    )
                }
            }
        }
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
                label = "Ritmo médio",
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
                text = "TRECHO",
                style = McTextStyles.overline,
                color = colors.textTertiary,
                modifier = Modifier.weight(1.4f),
            )
            Text(
                text = "RITMO",
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
private fun CalibrationCandidateCard(candidate: CalibrationCandidate, onCalibrate: () -> Unit) {
    val colors = McTheme.colors
    McCard(variant = McCardVariant.INSET) {
        Text(
            text = "Esta corrida pode calibrar seu limite",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${formatDuration(candidate.durationSec)} com FC média de " +
                "${candidate.averageHeartRate} bpm. Se foi um esforço máximo e contínuo, " +
                "use os dados para atualizar sua FC no limite. Nada é salvo sem sua confirmação.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        McSecondaryButton(
            text = "Usar esta corrida para calibrar",
            onClick = onCalibrate,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("activity-calibrate"),
        )
    }
}

@Composable
private fun StoryCard(
    state: ActivityDetailUiState,
    onCreateStory: () -> Unit,
    onExportStory: () -> Unit,
    onCancelStory: () -> Unit,
    onShareStory: (Uri) -> Unit,
    storyStyle: StoryMapStyle,
    onStoryStyleChange: (StoryMapStyle) -> Unit,
    onDismissStoryIntro: () -> Unit,
) {
    val colors = McTheme.colors
    McCard {
        Text(
            text = "Story do treino",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Vídeo de 30 s com a rota colorida pela FC, um card por km com ritmo, FC e calorias, " +
                "e um encerramento com os destaques da corrida. O mapa baixa tiles do OpenStreetMap " +
                "via OpenFreeMap; nenhuma coordenada exata é enviada.",
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(14.dp))
        when (val story = state.storyExport) {
            StoryExportUiState.Idle -> {
                if (!state.storyIntroSeen) {
                    McCard(variant = McCardVariant.INSET) {
                        Text(
                            text = "Antes de gerar",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Na primeira vez, baixamos os tiles do mapa e, no estilo Satélite ou " +
                                "Terreno, a imagem de satélite ou o relevo. Depois o app usa o cache. " +
                                "Nenhuma coordenada exata sai do aparelho.",
                            style = McTextStyles.caption,
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        McSecondaryButton(
                            text = "Entendi",
                            onClick = onDismissStoryIntro,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("story-intro-dismiss"),
                            minHeight = 44.dp,
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(
                    text = "MAPA",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StoryStyleButton(
                        text = "Clássico",
                        selected = storyStyle == StoryMapStyle.CLASSIC,
                        onClick = { onStoryStyleChange(StoryMapStyle.CLASSIC) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("story-style-classic"),
                    )
                    StoryStyleButton(
                        text = "Satélite",
                        selected = storyStyle == StoryMapStyle.SATELLITE,
                        onClick = { onStoryStyleChange(StoryMapStyle.SATELLITE) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("story-style-satellite"),
                    )
                    StoryStyleButton(
                        text = "Terreno",
                        selected = storyStyle == StoryMapStyle.TERRAIN,
                        onClick = { onStoryStyleChange(StoryMapStyle.TERRAIN) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("story-style-terrain"),
                    )
                }
                when (storyStyle) {
                    StoryMapStyle.TERRAIN -> {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Relevo do terreno via Mapzen · baixa na primeira vez.",
                            style = McTextStyles.caption,
                            color = colors.textTertiary,
                        )
                    }

                    StoryMapStyle.SATELLITE -> {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Imagem de satélite (Esri) · baixa na primeira vez.",
                            style = McTextStyles.caption,
                            color = colors.textTertiary,
                        )
                    }

                    StoryMapStyle.CLASSIC -> Unit
                }
                Spacer(modifier = Modifier.height(12.dp))
                McPrimaryButton(
                    text = "Criar story",
                    onClick = onCreateStory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("story-create"),
                    minHeight = 48.dp,
                )
            }

            is StoryExportUiState.Preview -> {
                StoryPreview(
                    plan = story.plan,
                    mapBackground = story.mapBackground,
                    style = storyStyle,
                    avatar = state.avatar,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    McPrimaryButton(
                        text = "Gerar vídeo",
                        onClick = onExportStory,
                        modifier = Modifier.weight(1f),
                    )
                    McSecondaryButton(
                        text = "Cancelar",
                        onClick = onCancelStory,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            StoryExportUiState.PreparingMap -> Text(
                text = "Baixando o mapa do OpenStreetMap…",
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )

            is StoryExportUiState.Generating -> {
                Text(
                    text = if (story.progress <= 0f) {
                        "Preparando o vídeo…"
                    } else {
                        "Gerando vídeo… ${(story.progress * 100).roundToInt()}%"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                if (story.etaSec != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cerca de ${story.etaSec} s restantes",
                        style = McTextStyles.caption,
                        color = colors.textSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Pode sair da tela: avisamos quando ficar pronto.",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                McSecondaryButton(
                    text = "Cancelar",
                    onClick = onCancelStory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("story-cancel"),
                )
            }

            is StoryExportUiState.Done -> {
                Text(
                    text = when {
                        story.savedToGallery && story.coverUri != null -> "Vídeo e capa salvos na galeria."
                        story.savedToGallery -> "Vídeo salvo na galeria."
                        else -> "Vídeo pronto para compartilhar."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.success,
                )
                if (story.coverUri != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "A capa é o frame final, pronta para o feed.",
                        style = McTextStyles.caption,
                        color = colors.textSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    McPrimaryButton(
                        text = "Compartilhar",
                        onClick = { onShareStory(story.uri) },
                        modifier = Modifier.weight(1f),
                    )
                    McSecondaryButton(
                        text = "Gerar outro",
                        onClick = onCreateStory,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            is StoryExportUiState.Error -> {
                Text(
                    text = story.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.warning,
                )
                Spacer(modifier = Modifier.height(10.dp))
                McSecondaryButton(
                    text = "Tentar novamente",
                    onClick = onCreateStory,
                )
            }
        }
    }
}

@Composable
private fun StoryStyleButton(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (selected) {
        McPrimaryButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            minHeight = 44.dp,
            horizontalPadding = 10.dp,
        )
    } else {
        McSecondaryButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            minHeight = 44.dp,
            horizontalPadding = 10.dp,
        )
    }
}

@Composable
private fun StoryPreview(plan: StoryPlan, mapBackground: Bitmap?, style: StoryMapStyle, avatar: Bitmap?) {
    val context = LocalContext.current
    val colors = McTheme.colors
    val bitmap = remember(plan, mapBackground, colors, style, avatar) {
        val typeface = ResourcesCompat.getFont(context, R.font.inter_variable) ?: Typeface.DEFAULT
        val previewTime = plan.segments
            .firstOrNull { it.type == StorySegmentType.CARD }
            ?.let { it.startSec + 1f }
            ?: (plan.durationSec / 2f)
        StoryFrameRenderer(
            colors.toStoryPalette(style),
            typeface,
            StoryBranding.logo(context),
            mapBackground,
            avatar,
        )
            .render(plan, timeSec = previewTime, width = 360, height = 640)
    }
    Text(
        text = "Prévia do story",
        style = McTextStyles.caption,
        color = colors.textTertiary,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Prévia do story",
            modifier = Modifier
                .height(260.dp)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun CoachReadingCard(verdict: StoryVerdict) {
    val colors = McTheme.colors
    McCard(variant = McCardVariant.INSET) {
        Text(
            text = "LEITURA DO COACH",
            style = McTextStyles.overline,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = verdict.headline,
            style = MaterialTheme.typography.titleMedium,
            color = colors.accentText,
        )
        verdict.proof?.let { proof ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = proof,
                style = McTextStyles.caption,
                color = colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = verdict.coachLine,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun SampleWarningCard(warning: String) {
    val colors = McTheme.colors
    McCard(variant = McCardVariant.INSET) {
        Text(
            text = "Gráficos indisponíveis",
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

private fun shareStory(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = android.content.ClipData.newRawUri("story", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartilhar story"))
}
