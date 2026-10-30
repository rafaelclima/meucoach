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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McEmptyState
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSectionHeader
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.AthleteProfile
import java.util.Locale

@Composable
fun ProfileScreen(onEdit: () -> Unit, modifier: Modifier = Modifier, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProfileContent(state = state, onEdit = onEdit, modifier = modifier)
}

@Composable
internal fun ProfileContent(state: ProfileUiState, onEdit: () -> Unit, modifier: Modifier = Modifier) {
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
                description = "Preencha seus dados para o coach ajustar zonas, carga e metas.",
                actionLabel = "Configurar perfil",
                onAction = onEdit,
                modifier = Modifier.align(Alignment.Center),
            )

            else -> ProfileDetails(profile = state.profile, onEdit = onEdit)
        }
    }
}

@Composable
private fun ProfileDetails(profile: AthleteProfile, onEdit: () -> Unit) {
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
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Seu perfil",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Dados usados para individualizar zonas, carga e metas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
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
        item { McSectionHeader(title = "Rotina") }
        item {
            McCard(variant = McCardVariant.INSET) {
                profile.restingHeartRate?.let { SummaryRow("FC de repouso", "$it bpm") }
                profile.maxHeartRateObserved?.let { SummaryRow("FC máxima vista", "$it bpm") }
                SummaryRow("Fuso horário", profile.timezone)
                profile.city?.let { SummaryRow("Cidade", it) }
            }
        }
        item {
            Text(
                text = "Seus dados ficam só neste aparelho. Nada é enviado para a nuvem.",
                style = McTextStyles.caption,
                color = colors.textTertiary,
            )
        }
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
