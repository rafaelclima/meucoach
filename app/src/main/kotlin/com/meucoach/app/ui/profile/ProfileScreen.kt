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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshHealth()
        onPauseOrDispose { }
    }

    ProfileContent(
        state = state,
        onEdit = onEdit,
        onCalibrate = onCalibrate,
        onOpenHealth = onOpenHealth,
        onManageData = onManageData,
        onShowTutorial = onShowTutorial,
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
                onEdit = onEdit,
                onCalibrate = onCalibrate,
                onOpenHealth = onOpenHealth,
                onManageData = onManageData,
                onShowTutorial = onShowTutorial,
            )
        }
    }
}

@Composable
private fun ProfileDetails(
    profile: AthleteProfile,
    health: ProfileHealthSummary,
    onEdit: () -> Unit,
    onCalibrate: () -> Unit,
    onOpenHealth: () -> Unit,
    onManageData: () -> Unit,
    onShowTutorial: () -> Unit,
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
        item {
            McCard {
                SummaryRow("Idade", ageLabel(profile))
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
            }
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
                healthConnectLastSyncLabel(health.lastSyncedAt)?.let { label ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = McTextStyles.caption,
                        color = colors.textTertiary,
                    )
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
private fun SummaryRow(label: String, value: String) {
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
