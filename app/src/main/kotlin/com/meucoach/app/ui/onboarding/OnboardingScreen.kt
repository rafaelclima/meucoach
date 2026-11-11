package com.meucoach.app.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.app.ui.common.AVAILABLE_CITIES
import com.meucoach.app.ui.common.AVAILABLE_TIMEZONES
import com.meucoach.app.ui.common.DateMask
import com.meucoach.app.ui.common.DateVisualTransformation
import com.meucoach.app.ui.common.ProfileAvatar
import com.meucoach.app.ui.common.TargetTimeField
import com.meucoach.app.ui.common.convert
import com.meucoach.app.ui.common.matchesSearch
import com.meucoach.app.ui.common.timezoneLabel
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
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setAvatar(uri)
    }

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
        onPickAvatar = {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        },
        onClearAvatar = viewModel::clearAvatar,
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
    onPickAvatar: () -> Unit = {},
    onClearAvatar: () -> Unit = {},
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
                            0 -> aboutStep(state, onUpdate, onPickAvatar, onClearAvatar)
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
private fun aboutStep(
    state: OnboardingUiState,
    onUpdate: ((OnboardingForm) -> OnboardingForm) -> Unit,
    onPickAvatar: () -> Unit,
    onClearAvatar: () -> Unit,
) {
    val form = state.form
    val colors = McTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(
            name = form.name,
            avatarPath = form.avatarPath.ifBlank { null },
            size = 72.dp,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Foto de perfil (opcional)",
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                McSecondaryButton(
                    text = if (form.avatarPath.isBlank()) "Escolher foto" else "Trocar foto",
                    onClick = onPickAvatar,
                    minHeight = 44.dp,
                    horizontalPadding = 12.dp,
                    modifier = Modifier.testTag("profile-photo-pick"),
                )
                if (form.avatarPath.isNotBlank()) {
                    McTextButton(
                        text = "Remover",
                        onClick = onClearAvatar,
                        trailingArrow = false,
                    )
                }
            }
        }
    }
    FormTextField(
        label = "Seu nome",
        value = form.name,
        onValueChange = { value -> onUpdate { it.copy(name = value) } },
        placeholder = "Como o coach deve te chamar",
        error = state.errors[OnboardingField.NAME],
        modifier = Modifier.testTag("profile-name-field"),
    )
    FormTextField(
        label = "Data de nascimento",
        value = form.birthDate,
        onValueChange = { value ->
            onUpdate { it.copy(birthDate = DateMask.digits(value)) }
        },
        placeholder = "dd/mm/aaaa",
        keyboardType = KeyboardType.Number,
        error = state.errors[OnboardingField.BIRTH_DATE],
        visualTransformation = DateVisualTransformation,
        modifier = Modifier.testTag("birth-date-field"),
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
                onUpdate { it.copy(targetDate = DateMask.digits(value)) }
            },
            placeholder = "dd/mm/aaaa",
            keyboardType = KeyboardType.Number,
            error = state.errors[OnboardingField.TARGET_DATE],
            visualTransformation = DateVisualTransformation,
            modifier = Modifier.testTag("target-date-field"),
        )
        TargetTimeField(
            label = "Tempo alvo (opcional)",
            parts = form.targetTimeParts,
            unit = form.targetTimeUnit,
            onPartsChange = { parts -> onUpdate { it.copy(targetTimeParts = parts) } },
            onUnitChange = { unit ->
                onUpdate {
                    it.copy(
                        targetTimeParts = it.targetTimeParts.convert(it.targetTimeUnit, unit),
                        targetTimeUnit = unit,
                    )
                }
            },
            tagPrefix = "target-time",
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
    FormTextField(
        label = "FC no limite (opcional)",
        value = form.lactateThresholdHeartRate,
        onValueChange = { value -> onUpdate { it.copy(lactateThresholdHeartRate = value) } },
        suffix = "bpm",
        keyboardType = KeyboardType.Number,
        error = state.errors[OnboardingField.LACTATE_THRESHOLD_HEART_RATE],
        modifier = Modifier.testTag("profile-lthr-field"),
    )
    Text(
        text = "Se você já fez um teste de 30 min, informe a FC média do esforço. " +
            "Sem isso, calibre por uma prova ou teste recente.",
        style = McTextStyles.caption,
        color = McTheme.colors.textTertiary,
    )
    var timezoneOpen by rememberSaveable { mutableStateOf(false) }
    var cityOpen by rememberSaveable { mutableStateOf(false) }
    SelectField(
        label = "Fuso horário",
        value = timezoneLabel(form.timezone),
        hint = "Define quando o seu dia começa e termina.",
        error = state.errors[OnboardingField.TIMEZONE],
        onClick = { timezoneOpen = true },
        modifier = Modifier.testTag("timezone-field"),
    )
    SelectField(
        label = "Cidade (opcional)",
        value = form.city.ifBlank { "Não informar" },
        hint = "Usada para clima local; pode ficar em branco.",
        error = state.errors[OnboardingField.CITY],
        onClick = { cityOpen = true },
        modifier = Modifier.testTag("city-field"),
    )
    if (timezoneOpen) {
        SelectionSheet(
            title = "Fuso horário",
            options = AVAILABLE_TIMEZONES.map { it.zoneId to it.label },
            selected = form.timezone,
            onSelect = { value ->
                if (value != null) onUpdate { it.copy(timezone = value) }
            },
            onDismiss = { timezoneOpen = false },
        )
    }
    if (cityOpen) {
        SelectionSheet(
            title = "Cidade",
            options = AVAILABLE_CITIES.map { it to it },
            selected = form.city.ifBlank { null },
            onSelect = { value -> onUpdate { it.copy(city = value.orEmpty()) } },
            onDismiss = { cityOpen = false },
            clearLabel = "Não informar",
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Tenho acesso a esteira",
                style = MaterialTheme.typography.titleSmall,
                color = McTheme.colors.textPrimary,
            )
            Text(
                text = "Vale como alternativa de treino forte no calor.",
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
private fun SelectField(
    label: String,
    value: String,
    hint: String?,
    error: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = McTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, if (error != null) colors.danger else colors.borderStrong),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = colors.textSecondary,
                )
            }
        }
        when {
            error != null -> Text(text = error, style = McTextStyles.caption, color = colors.danger)
            hint != null -> Text(text = hint, style = McTextStyles.caption, color = colors.textTertiary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SelectionSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T?) -> Unit,
    onDismiss: () -> Unit,
    clearLabel: String? = null,
) {
    val colors = McTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(options, query) { options.filter { matchesSearch(it.second, query) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("selection-search"),
                placeholder = { Text(text = "Buscar") },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.borderStrong,
                    focusedLabelColor = colors.accentText,
                    unfocusedLabelColor = colors.textTertiary,
                    cursorColor = colors.accent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                ),
            )
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                if (clearLabel != null) {
                    item {
                        SelectionRow(
                            label = clearLabel,
                            selected = selected == null,
                            onClick = {
                                onSelect(null)
                                onDismiss()
                            },
                        )
                    }
                }
                items(filtered) { (value, label) ->
                    SelectionRow(
                        label = label,
                        selected = value == selected,
                        onClick = {
                            onSelect(value)
                            onDismiss()
                        },
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            text = "Nenhuma opção encontrada.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textTertiary,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = McTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) colors.accentSoft else Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) colors.accentText else colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = colors.accentText,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
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
    visualTransformation: VisualTransformation = VisualTransformation.None,
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
        visualTransformation = visualTransformation,
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
