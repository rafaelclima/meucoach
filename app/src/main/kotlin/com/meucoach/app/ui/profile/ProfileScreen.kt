package com.meucoach.app.ui.profile

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.app.ui.common.ProfileAvatar
import com.meucoach.app.ui.common.healthConnectLastSyncLabel
import com.meucoach.app.ui.common.healthConnectStatusLabel
import com.meucoach.app.ui.common.isHealthConnectConnected
import com.meucoach.app.ui.common.timezoneLabel
import com.meucoach.app.ui.replan.ReplanChangesSheet
import com.meucoach.app.ui.replan.ReplanMessageCard
import com.meucoach.app.ui.replan.ReplanSuggestionCard
import com.meucoach.app.ui.replan.ReplanUiState
import com.meucoach.app.ui.replan.ReplanViewModel
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McEmptyState
import com.meucoach.core.designsystem.component.McPill
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.component.McSectionHeader
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Composable
fun ProfileScreen(
    onEdit: () -> Unit,
    onCalibrate: () -> Unit,
    onOpenHealth: () -> Unit,
    onManageData: () -> Unit,
    onShowTutorial: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
    replanViewModel: ReplanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val replan by replanViewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshHealth()
        replanViewModel.refreshForProfile()
        onPauseOrDispose { }
    }

    ProfileContent(
        state = state,
        replan = replan,
        onEdit = onEdit,
        onCalibrate = onCalibrate,
        onOpenHealth = onOpenHealth,
        onManageData = onManageData,
        onShowTutorial = onShowTutorial,
        onReplanAccept = replanViewModel::accept,
        onReplanDecline = replanViewModel::decline,
        onReplanSuppress = replanViewModel::suppress,
        onReplanConsumeMessage = replanViewModel::consumeMessage,
        onRaceSave = viewModel::saveRaceEvent,
        onRaceComplete = viewModel::completeRaceEvent,
        onRaceCancel = viewModel::cancelRaceEvent,
        onRaceNoShow = viewModel::noShowRaceEvent,
        onRaceUndoAutoCompletion = viewModel::undoAutoCompletedRace,
        onRaceConsumeMessage = viewModel::consumeRaceMessage,
        modifier = modifier,
    )
}

@Composable
internal fun ProfileContent(
    state: ProfileUiState,
    onEdit: () -> Unit,
    onCalibrate: () -> Unit,
    onOpenHealth: () -> Unit,
    onManageData: () -> Unit,
    onShowTutorial: () -> Unit,
    replan: ReplanUiState = ReplanUiState(),
    onReplanAccept: () -> Unit = {},
    onReplanDecline: () -> Unit = {},
    onReplanSuppress: () -> Unit = {},
    onReplanConsumeMessage: () -> Unit = {},
    onRaceSave: (RaceDistance, LocalDate, Int?) -> Unit = { _, _, _ -> },
    onRaceComplete: (String?) -> Unit = {},
    onRaceCancel: () -> Unit = {},
    onRaceNoShow: () -> Unit = {},
    onRaceUndoAutoCompletion: (String) -> Unit = {},
    onRaceConsumeMessage: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = McTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        when {
            state.isLoading -> CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
                    .testTag("profile-loading"),
                color = colors.accent,
                strokeWidth = 2.dp,
            )

            state.profile == null -> McEmptyState(
                icon = Icons.Outlined.Person,
                title = "Perfil ainda não configurado",
                description = "Preencha seus dados para que o app ajuste seu treino " +
                    "(ritmo, carga e metas). Nada sai do aparelho.",
                actionLabel = "Configurar perfil",
                onAction = onEdit,
                modifier = Modifier.align(Alignment.Center),
            )

            else -> ProfileDetails(
                profile = state.profile,
                health = state.health,
                today = state.today,
                now = state.now,
                zone = state.zone,
                replan = replan,
                raceEvents = state.raceEvents,
                raceActivities = state.raceActivities,
                raceProjection = state.raceProjection,
                racePrediction = state.racePrediction,
                raceMessage = state.raceMessage,
                onEdit = onEdit,
                onCalibrate = onCalibrate,
                onOpenHealth = onOpenHealth,
                onManageData = onManageData,
                onShowTutorial = onShowTutorial,
                onReplanAccept = onReplanAccept,
                onReplanDecline = onReplanDecline,
                onReplanSuppress = onReplanSuppress,
                onReplanConsumeMessage = onReplanConsumeMessage,
                onRaceSave = onRaceSave,
                onRaceComplete = onRaceComplete,
                onRaceCancel = onRaceCancel,
                onRaceNoShow = onRaceNoShow,
                onRaceUndoAutoCompletion = onRaceUndoAutoCompletion,
                onRaceConsumeMessage = onRaceConsumeMessage,
            )
        }
    }
}

@Composable
private fun ProfileDetails(
    profile: AthleteProfile,
    health: ProfileHealthSummary,
    today: LocalDate?,
    now: Instant?,
    zone: ZoneId,
    replan: ReplanUiState,
    raceEvents: List<com.meucoach.core.model.RaceEvent>,
    raceActivities: List<RaceActivityOption>,
    raceProjection: com.meucoach.core.domain.race.RaceTargetProjection?,
    racePrediction: com.meucoach.core.domain.race.RacePredictionResult?,
    raceMessage: String?,
    onEdit: () -> Unit,
    onCalibrate: () -> Unit,
    onOpenHealth: () -> Unit,
    onManageData: () -> Unit,
    onShowTutorial: () -> Unit,
    onReplanAccept: () -> Unit,
    onReplanDecline: () -> Unit,
    onReplanSuppress: () -> Unit,
    onReplanConsumeMessage: () -> Unit,
    onRaceSave: (RaceDistance, LocalDate, Int?) -> Unit,
    onRaceComplete: (String?) -> Unit,
    onRaceCancel: () -> Unit,
    onRaceNoShow: () -> Unit,
    onRaceUndoAutoCompletion: (String) -> Unit,
    onRaceConsumeMessage: () -> Unit,
) {
    val colors = McTheme.colors
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("profile-list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(text = "PERFIL", style = McTextStyles.overline, color = colors.textTertiary)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileAvatar(
                        name = profile.name,
                        avatarPath = profile.avatarPath,
                        size = 72.dp,
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = profile.name?.takeIf { it.isNotBlank() } ?: "Seu perfil",
                            style = MaterialTheme.typography.headlineLarge,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Dados usados para individualizar zonas, carga e metas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
        if (replan.suggestion != null) {
            item {
                ReplanSuggestionCard(
                    state = replan,
                    onAccept = onReplanAccept,
                    onDecline = onReplanDecline,
                    onSuppress = onReplanSuppress,
                )
            }
        } else {
            replan.message?.let { message ->
                item {
                    ReplanMessageCard(
                        message = message,
                        onDismiss = onReplanConsumeMessage,
                    )
                }
            }
        }
        item {
            McCard {
                SummaryRow("Idade", today?.let { ageLabel(profile, it) } ?: "—")
                SummaryRow("Sexo", sexLabel(profile.sex))
                SummaryRow("Experiência", experienceLabel(profile.experience))
                profile.heightCm?.let { SummaryRow("Altura", formatMeasure(it, "cm")) }
                profile.weightKg?.let { SummaryRow("Peso", formatMeasure(it, "kg")) }
            }
        }
        item { McSectionHeader(title = "Objetivo") }
        item {
            McCard(variant = McCardVariant.INSET) {
                SummaryRow("Meta", goalLabel(profile))
                if (profile.goal != null) {
                    SummaryRow("Data", targetDateLabel(profile))
                    SummaryRow("Tempo alvo", targetTimeLabel(profile.goal?.targetTimeSec))
                }
                SummaryRow("Disponibilidade", availabilityLabel(profile))
                if (raceEvents.any { it.status == RaceStatus.PLANNED }) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "A prova planejada abaixo é o objetivo ativo do plano. " +
                            "Esta meta legada não gera taper nem dia de prova.",
                        style = McTextStyles.caption,
                        color = colors.textTertiary,
                        modifier = Modifier.testTag("legacy-goal-note"),
                    )
                }
            }
        }
        item {
            RaceEventCard(
                events = raceEvents,
                activities = raceActivities,
                projection = raceProjection,
                prediction = racePrediction,
                message = raceMessage,
                onSave = onRaceSave,
                onComplete = onRaceComplete,
                onCancel = onRaceCancel,
                onNoShow = onRaceNoShow,
                onUndoAutoCompletion = onRaceUndoAutoCompletion,
                onConsumeMessage = onRaceConsumeMessage,
            )
        }
        item { McSectionHeader(title = "Seu limite de esforço") }
        item {
            val hasCalibration =
                profile.vdot != null ||
                    profile.lactateThresholdHeartRate != null ||
                    profile.thresholdPace != null
            McCard(variant = McCardVariant.INSET) {
                if (hasCalibration) {
                    profile.vdot?.let {
                        SummaryRow(
                            "Índice de aptidão",
                            String.format(Locale.forLanguageTag("pt-BR"), "%.1f", it),
                        )
                    }
                    profile.lactateThresholdHeartRate?.let { SummaryRow("FC no limite", "$it bpm") }
                    profile.thresholdPace?.let { SummaryRow("Ritmo no limite", "${it.format()} /km") }
                } else {
                    Text(
                        text = "Se você já sabe sua FC de limite, informe em Editar perfil. " +
                            "Ou calibre com uma prova recente ou um treino de 30 min.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                McSecondaryButton(
                    text = if (hasCalibration) "Recalibrar" else "Calibrar agora",
                    onClick = onCalibrate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile-calibrate-button"),
                )
            }
        }
        item { McSectionHeader(title = "Rotina") }
        item {
            McCard(variant = McCardVariant.INSET) {
                profile.restingHeartRate?.let { SummaryRow("FC de repouso", "$it bpm") }
                profile.maxHeartRateObserved?.let { SummaryRow("FC máxima vista", "$it bpm") }
                SummaryRow("Fuso horário", timezoneLabel(profile.timezone))
                profile.city?.let { SummaryRow("Cidade", it) }
            }
        }
        item { McSectionHeader(title = "Saúde e dados") }
        item {
            val connected = isHealthConnectConnected(health.status, health.hasPermissions)
            McCard(variant = McCardVariant.INSET) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Sono, recuperação e oxigenação",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Seu painel de recuperação, com o que o coach usa na prontidão.",
                            style = McTextStyles.caption,
                            color = colors.textSecondary,
                        )
                    }
                    McTextButton(
                        text = "Abrir",
                        onClick = onOpenHealth,
                        modifier = Modifier.testTag("profile-open-health"),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                McPill(
                    text = healthConnectStatusLabel(
                        status = health.status,
                        hasPermissions = health.hasPermissions,
                        storedDays = health.storedDays,
                    ),
                    containerColor = if (connected) colors.accentSoft else colors.surface,
                    contentColor = if (connected) colors.accentText else colors.textSecondary,
                    modifier = Modifier.testTag("profile-health-status"),
                )
                now?.let { current ->
                    healthConnectLastSyncLabel(health.lastSyncedAt, current, zone)?.let { label ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label,
                            style = McTextStyles.caption,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }
        item {
            McCard(variant = McCardVariant.INSET) {
                Text(
                    text = "Seus dados, sob seu controle",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Baixe uma cópia completa em JSON ou apague tudo. Nada sai deste " +
                        "aparelho sem você.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                McSecondaryButton(
                    text = "Exportar ou apagar dados",
                    onClick = onManageData,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile-manage-data"),
                )
            }
        }
        item { McSectionHeader(title = "Ajuda") }
        item {
            McCard(variant = McCardVariant.INSET) {
                Text(
                    text = "Rever o tour de boas-vindas",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Uma passada rápida pelas telas e cards do app e pelo que você " +
                        "mantém em dia para o coach acertar sempre.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                McSecondaryButton(
                    text = "Rever tutorial",
                    onClick = onShowTutorial,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile-show-tutorial"),
                )
            }
        }
        item { NotificationReminderCard() }
        item {
            McPrimaryButton(
                text = "Editar perfil",
                onClick = onEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile-edit-button"),
            )
        }
    }
}

@Composable
internal fun SummaryRow(label: String, value: String) {
    val colors = McTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Text(text = value, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
    }
}

private fun formatMeasure(value: Double, unit: String): String {
    val text = if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.forLanguageTag("pt-BR"), "%.1f", value)
    }
    return "$text $unit"
}
