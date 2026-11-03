package com.meucoach.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.app.ui.profile.availabilityDayLabel
import com.meucoach.app.ui.profile.experienceLabel
import com.meucoach.app.ui.profile.goalLabel
import com.meucoach.app.ui.profile.sexLabel
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.Experience
import com.meucoach.core.model.GoalType
import com.meucoach.core.model.Sex
import java.time.DayOfWeek

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            viewModel.consumeSaved()
            onDone()
        }
    }

    OnboardingContent(
        state = state,
        onUpdate = viewModel::update,
        onNext = viewModel::next,
        onBack = viewModel::back,
        onSave = viewModel::save,
        onToggleDay = viewModel::toggleDay,
        onDayMinutes = viewModel::updateDayMinutes,
        onCancel = onCancel,
        modifier = modifier,
    )
}

@Composable
internal fun OnboardingContent(
    state: OnboardingUiState,
    onUpdate: ((OnboardingForm) -> OnboardingForm) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onDayMinutes: (DayOfWeek, String) -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    val colors = McTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp),
                color = colors.accent,
                strokeWidth = 2.dp,
            )
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.isEditing) "EDITAR PERFIL" else "PRIMEIROS PASSOS",
                    style = McTextStyles.overline,
                    color = colors.textTertiary,
                )
                if (onCancel != null) {
                    McTextButton(text = "Cancelar", onClick = onCancel, trailingArrow = false)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("onboarding-list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    StepProgress(step = state.step)
                }
                item {
                    StepHeader(step = state.step, isEditing = state.isEditing)
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (state.step) {
                            0 -> aboutStep(state, onUpdate)
                            1 -> goalStep(state, onUpdate, onToggleDay, onDayMinutes)
                            else -> routineStep(state, onUpdate)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.step > 0) {
                    McSecondaryButton(
                        text = "Voltar",
                        onClick = onBack,
                        enabled = !state.isSaving,
                        modifier = Modifier.weight(1f),
                    )
                }
                McPrimaryButton(
                    text = when {
                        state.isSaving -> "Salvando..."
                        state.step == OnboardingViewModel.LAST_STEP -> "Salvar perfil"
                        else -> "Continuar"
                    },
                    onClick = if (state.step == OnboardingViewModel.LAST_STEP) onSave else onNext,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StepProgress(step: Int) {
    val colors = McTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(OnboardingViewModel.LAST_STEP + 1) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        color = if (index <= step) colors.accent else colors.surfaceInset,
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

@Composable
private fun StepHeader(step: Int, isEditing: Boolean) {
    val colors = McTheme.colors
    val title = when (step) {
        0 -> if (isEditing) "Seus dados" else "Vamos nos conhecer"
        1 -> "Qual é o seu objetivo?"
        else -> "Sua rotina"
    }
    val description = when (step) {
        0 -> "Esses dados ajudam a ajustar seu treino. Nada sai do aparelho."
        1 -> "Escolha um objetivo (ex.: prova) ou apenas foque no hábito. Os dias são opcionais se não for prova."
        else -> "Seu fuso e cidade ajudam a interpretar datas e clima local."
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "PASSO ${step + 1} DE ${OnboardingViewModel.LAST_STEP + 1}",
            style = McTextStyles.overline,
            color = colors.textTertiary,
        )
        Text(text = title, style = MaterialTheme.typography.headlineLarge, color = colors.textPrimary)
        Text(text = description, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}

@Composable
private fun aboutStep(state: OnboardingUiState, onUpdate: ((OnboardingForm) -> OnboardingForm) -> Unit) {
    val form = state.form
    FormTextField(
        label = "Data de nascimento",
        value = form.birthDate,
        onValueChange = { value ->
            val masked = applyDateMask(value)
            onUpdate { it.copy(birthDate = masked) }
        },
        placeholder = "dd/mm/aaaa",
        keyboardType = KeyboardType.Number,
        error = state.errors[OnboardingField.BIRTH_DATE],
    )
    ChoiceChips(
        label = "Sexo",
        options = Sex.entries.map { it to sexLabel(it) },
        selected = form.sex,
        onSelect = { value -> onUpdate { it.copy(sex = value) } },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FormTextField(
            label = "Altura",
            value = form.heightCm,
            onValueChange = { value -> onUpdate { it.copy(heightCm = value) } },
            suffix = "cm",
            keyboardType = KeyboardType.Number,
            error = state.errors[OnboardingField.HEIGHT],
            modifier = Modifier.weight(1f),
        )
        FormTextField(
            label = "Peso",
            value = form.weightKg,
            onValueChange = { value -> onUpdate { it.copy(weightKg = value) } },
            suffix = "kg",
            keyboardType = KeyboardType.Number,
            error = state.errors[OnboardingField.WEIGHT],
            modifier = Modifier.weight(1f),
        )
    }
    ChoiceChips(
        label = "Experiência",
        options = Experience.entries.map { it to experienceLabel(it) },
        selected = form.experience,
        onSelect = { value -> onUpdate { it.copy(experience = value) } },
    )
}

@Composable
private fun goalStep(
    state: OnboardingUiState,
    onUpdate: ((OnboardingForm) -> OnboardingForm) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onDayMinutes: (DayOfWeek, String) -> Unit,
) {
    val form = state.form
    ChoiceChips(
        label = "Objetivo",
        options = GoalType.entries.map { it to goalLabel(it) },
        selected = form.goalType,
        onSelect = { value -> onUpdate { it.copy(goalType = value) } },
    )
    if (form.isRaceGoal) {
        FormTextField(
            label = "Data da prova (opcional)",
            value = form.targetDate,
            onValueChange = { value ->
                val masked = applyDateMask(value)
                onUpdate { it.copy(targetDate = masked) }
            },
            placeholder = "dd/mm/aaaa",
            keyboardType = KeyboardType.Number,
            error = state.errors[OnboardingField.TARGET_DATE],
        )
        FormTextField(
            label = "Tempo alvo (opcional)",
            value = form.targetTime,
            onValueChange = { value -> onUpdate { it.copy(targetTime = value) } },
            placeholder = "mm:ss ou h:mm:ss",
            error = state.errors[OnboardingField.TARGET_TIME],
        )
    }
    Text(
        text = "Disponibilidade",
        style = MaterialTheme.typography.titleMedium,
        color = McTheme.colors.textPrimary,
    )
    Text(
        text = "Marque os dias e quantos minutos você costuma ter.",
        style = MaterialTheme.typography.bodyMedium,
        color = McTheme.colors.textSecondary,
    )
    DayOfWeek.entries.forEach { day ->
        DayAvailabilityRow(
            day = day,
            minutes = form.availabilityMinutes[day],
            onToggle = { onToggleDay(day) },
            onMinutesChange = { value -> onDayMinutes(day, value) },
        )
    }
    state.errors[OnboardingField.AVAILABILITY]?.let { error ->
        Text(text = error, style = McTextStyles.caption, color = McTheme.colors.danger)
    }
}

@Composable
private fun routineStep(state: OnboardingUiState, onUpdate: ((OnboardingForm) -> OnboardingForm) -> Unit) {
    val form = state.form
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FormTextField(
            label = "FC de repouso",
            value = form.restingHeartRate,
            onValueChange = { value -> onUpdate { it.copy(restingHeartRate = value) } },
            suffix = "bpm",
            keyboardType = KeyboardType.Number,
            error = state.errors[OnboardingField.RESTING_HEART_RATE],
            modifier = Modifier.weight(1f),
        )
        FormTextField(
            label = "FC máxima vista",
            value = form.maxHeartRateObserved,
            onValueChange = { value -> onUpdate { it.copy(maxHeartRateObserved = value) } },
            suffix = "bpm",
            keyboardType = KeyboardType.Number,
            error = state.errors[OnboardingField.MAX_HEART_RATE],
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text = "FC máxima é a maior observada em treino ou prova, nunca 220 − idade.",
        style = McTextStyles.caption,
        color = McTheme.colors.textTertiary,
    )
    ChoiceChips(
        label = "Fuso horário",
        options = AVAILABLE_TIMEZONES.map { it.zoneId to it.label },
        selected = form.timezone,
        onSelect = { value -> onUpdate { it.copy(timezone = value) } },
    )
    Text(
        text = "Define quando o seu dia começa e termina.",
        style = McTextStyles.caption,
        color = McTheme.colors.textTertiary,
    )
    state.errors[OnboardingField.TIMEZONE]?.let { error ->
        Text(text = error, style = McTextStyles.caption, color = McTheme.colors.danger)
    }
    ChoiceChips(
        label = "Cidade (opcional)",
        options = AVAILABLE_CITIES.map { it to it },
        selected = form.city,
        onSelect = { value -> onUpdate { it.copy(city = value) } },
    )
    state.errors[OnboardingField.CITY]?.let { error ->
        Text(text = error, style = McTextStyles.caption, color = McTheme.colors.danger)
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Tenho acesso a esteira",
                style = MaterialTheme.typography.titleSmall,
                color = McTheme.colors.textPrimary,
            )
            Text(
                text = "Vale como opção de qualidade no calor.",
                style = McTextStyles.caption,
                color = McTheme.colors.textTertiary,
            )
        }
        Switch(
            checked = form.treadmillAccess,
            onCheckedChange = { value -> onUpdate { it.copy(treadmillAccess = value) } },
            colors = SwitchDefaults.colors(
                checkedThumbColor = McTheme.colors.onAccent,
                checkedTrackColor = McTheme.colors.accent,
                uncheckedThumbColor = McTheme.colors.textTertiary,
                uncheckedTrackColor = McTheme.colors.surfaceInset,
            ),
        )
    }
    Text(
        text = "As zonas de treino serão calibradas depois, com um teste ou prova.",
        style = McTextStyles.caption,
        color = McTheme.colors.textTertiary,
    )
}

@Composable
private fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Ascii,
    error: String? = null,
) {
    val colors = McTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label) },
        placeholder = if (placeholder != null) {
            { Text(text = placeholder) }
        } else {
            null
        },
        suffix = if (suffix != null) {
            { Text(text = suffix) }
        } else {
            null
        },
        singleLine = true,
        isError = error != null,
        supportingText = if (error != null) {
            { Text(text = error) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.borderStrong,
            focusedLabelColor = colors.accentText,
            unfocusedLabelColor = colors.textTertiary,
            cursorColor = colors.accent,
            errorBorderColor = colors.danger,
            errorLabelColor = colors.danger,
            errorSupportingTextColor = colors.danger,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val colors = McTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(text = text) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = colors.surfaceInset,
                        labelColor = colors.textSecondary,
                        selectedContainerColor = colors.accentSoft,
                        selectedLabelColor = colors.accentText,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = value == selected,
                        borderColor = colors.borderSubtle,
                        selectedBorderColor = colors.accent.copy(alpha = 0.5f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun DayAvailabilityRow(
    day: DayOfWeek,
    minutes: String?,
    onToggle: () -> Unit,
    onMinutesChange: (String) -> Unit,
) {
    val colors = McTheme.colors
    val enabled = minutes != null
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Switch(
            checked = enabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textTertiary,
                uncheckedTrackColor = colors.surfaceInset,
            ),
        )
        Text(
            text = availabilityDayLabel(day),
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) colors.textPrimary else colors.textTertiary,
            modifier = Modifier.weight(1f),
        )
        if (enabled) {
            OutlinedTextField(
                value = minutes.orEmpty(),
                onValueChange = onMinutesChange,
                modifier = Modifier.width(104.dp),
                suffix = { Text(text = "min") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.borderStrong,
                    cursorColor = colors.accent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                ),
            )
        } else {
            Spacer(modifier = Modifier.width(104.dp))
        }
    }
}
