package com.meucoach.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme

/**
 * Declared time with an explicit unit (`mm:ss` × `h:mm:ss`).
 *
 * The state lives in [TargetTimeParts]; the mask and the colons are visual only.
 * Switching the unit must convert the current value through
 * [TargetTimeParts.convert] before calling [onPartsChange].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TargetTimeField(
    label: String,
    parts: TargetTimeParts,
    unit: TargetTimeUnit,
    onPartsChange: (TargetTimeParts) -> Unit,
    onUnitChange: (TargetTimeUnit) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    val colors = McTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TargetTimeUnit.entries.forEach { option ->
                FilterChip(
                    selected = option == unit,
                    onClick = { onUnitChange(option) },
                    label = { Text(text = option.unitLabel()) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("$tagPrefix-unit-${option.name.lowercase()}"),
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = colors.surfaceInset,
                        labelColor = colors.textSecondary,
                        selectedContainerColor = colors.accentSoft,
                        selectedLabelColor = colors.accentText,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = option == unit,
                        borderColor = colors.borderSubtle,
                        selectedBorderColor = colors.accent.copy(alpha = 0.5f),
                    ),
                )
            }
        }
        Text(
            text = "Máscara: ${unit.maskLabel()}",
            style = McTextStyles.caption,
            color = colors.textTertiary,
            modifier = Modifier.testTag("$tagPrefix-mask"),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tagPrefix),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (unit == TargetTimeUnit.HOURS) {
                TimePartField(
                    label = "Horas",
                    value = parts.hours,
                    maxDigits = 2,
                    onValueChange = { value -> onPartsChange(parts.copy(hours = value)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("$tagPrefix-hours"),
                )
                Text(text = ":", style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
            }
            TimePartField(
                label = "Min",
                value = parts.minutes,
                maxDigits = if (unit == TargetTimeUnit.MINUTES) 3 else 2,
                onValueChange = { value -> onPartsChange(parts.copy(minutes = value)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("$tagPrefix-minutes"),
            )
            Text(text = ":", style = MaterialTheme.typography.titleMedium, color = colors.textSecondary)
            TimePartField(
                label = "Seg",
                value = parts.seconds,
                maxDigits = 2,
                onValueChange = { value -> onPartsChange(parts.copy(seconds = value)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("$tagPrefix-seconds"),
            )
        }
        error?.let { text ->
            Text(
                text = text,
                style = McTextStyles.caption,
                color = colors.danger,
                modifier = Modifier.testTag("$tagPrefix-error"),
            )
        }
    }
}

@Composable
private fun TimePartField(
    label: String,
    value: String,
    maxDigits: Int,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = McTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter(Char::isDigit).take(maxDigits)) },
        label = { Text(text = label) },
        placeholder = { Text(text = "00") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
        ),
    )
}
