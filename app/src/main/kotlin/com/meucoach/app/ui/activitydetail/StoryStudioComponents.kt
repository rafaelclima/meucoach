package com.meucoach.app.ui.activitydetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.story.StoryExtra
import java.util.Locale

/** Cards and chips shared by the video and the art tab of the Story Studio. */

@Composable
internal fun StudioCardHeader(
    overline: String,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = McTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = overline,
                style = McTextStyles.overline,
                color = colors.textTertiary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
internal fun ExtrasCounter(selected: Int, max: Int) {
    val colors = McTheme.colors
    val complete = selected >= max
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (complete) colors.accentSoft else colors.surfaceInset,
        border = BorderStroke(1.dp, if (complete) colors.accent.copy(alpha = 0.4f) else colors.borderSubtle),
    ) {
        Text(
            text = "$selected/$max",
            style = MaterialTheme.typography.labelLarge,
            color = if (complete) colors.accentText else colors.textSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun StudioPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = McTheme.colors
    val container = when {
        !enabled -> colors.surfaceInset.copy(alpha = 0.6f)
        selected -> colors.accent
        else -> colors.surfaceInset
    }
    val content = when {
        !enabled -> colors.textTertiary
        selected -> colors.onAccent
        else -> colors.textPrimary
    }
    val border = when {
        selected -> null
        else -> BorderStroke(1.dp, colors.borderSubtle)
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = container,
        contentColor = content,
        border = border,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = if (selected) "$text, selecionado" else text
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        style = McTextStyles.overline,
        color = McTheme.colors.textTertiary,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
internal fun ExtraTile(
    option: StoryExtraOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "story-extra",
    selectionHint: String = "No vídeo",
) {
    val colors = McTheme.colors
    val border = when {
        selected -> BorderStroke(1.5.dp, colors.accent)
        else -> BorderStroke(1.dp, colors.borderSubtle)
    }
    val container = when {
        selected -> colors.accentSoft
        !enabled -> colors.surfaceInset.copy(alpha = 0.6f)
        else -> colors.surfaceInset
    }
    val status = when {
        !option.isAvailable -> "${option.extra.uiLabel()} · ${option.reason}"
        else -> option.extra.uiLabel()
    }
    val hint = when {
        !option.isAvailable -> option.reason
        selected -> selectionHint
        else -> extraHint(option.extra)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = border,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled || selected, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = if (selected) "$status, selecionado" else status
            }
            .testTag("$tagPrefix-${option.extra.name.lowercase(Locale.ROOT)}"),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (selected) colors.accent else colors.surfaceElevated,
                    border = if (selected) null else BorderStroke(1.dp, colors.borderSubtle),
                ) {
                    Icon(
                        imageVector = extraIcon(option.extra),
                        contentDescription = null,
                        tint = if (!enabled && !selected) {
                            colors.textTertiary
                        } else if (selected) {
                            colors.onAccent
                        } else {
                            colors.textSecondary
                        },
                        modifier = Modifier.padding(7.dp).size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = colors.accentText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.titleSmall,
                color = if (!enabled && !selected) colors.textTertiary else colors.textPrimary,
            )
            Text(
                text = hint ?: "",
                style = McTextStyles.caption,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
internal fun StudioSaveDefaultCard(
    save: Boolean,
    onSave: (Boolean) -> Unit,
    onReset: () -> Unit,
    saveTag: String,
    resetTag: String,
    caption: String,
) {
    val colors = McTheme.colors
    McCard(variant = McCardVariant.INSET) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .toggleable(
                    value = save,
                    role = Role.Checkbox,
                    onValueChange = onSave,
                )
                .testTag(saveTag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Salvar como padrão",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = caption,
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = save,
                onCheckedChange = null,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            McTextButton(
                text = "Restaurar padrão",
                onClick = onReset,
                modifier = Modifier.testTag(resetTag),
            )
        }
    }
}

internal fun extraIcon(extra: StoryExtra): ImageVector = when (extra) {
    StoryExtra.CALORIES_TOTAL -> Icons.Outlined.LocalFireDepartment
    StoryExtra.ELEVATION_GAIN -> Icons.Outlined.Terrain
    StoryExtra.AVG_HR -> Icons.Outlined.FavoriteBorder
    StoryExtra.MAX_HR -> Icons.Outlined.Favorite
    StoryExtra.CADENCE -> Icons.AutoMirrored.Outlined.DirectionsRun
    StoryExtra.FASTEST_KM -> Icons.Outlined.Bolt
    StoryExtra.DATE -> Icons.Outlined.CalendarToday
}

internal fun extraHint(extra: StoryExtra): String? = when (extra) {
    StoryExtra.CALORIES_TOTAL -> "Total do treino"
    StoryExtra.ELEVATION_GAIN -> "Ganho acumulado"
    StoryExtra.AVG_HR -> "Média do treino"
    StoryExtra.MAX_HR -> "Pico do treino"
    StoryExtra.CADENCE -> "Passos por minuto"
    StoryExtra.FASTEST_KM -> "Selo no card"
    StoryExtra.DATE -> "Dia da corrida"
}

internal fun StoryExtra.uiLabel(): String = when (this) {
    StoryExtra.CALORIES_TOTAL -> "Calorias"
    StoryExtra.ELEVATION_GAIN -> "Elevação"
    StoryExtra.AVG_HR -> "FC média"
    StoryExtra.MAX_HR -> "FC máxima"
    StoryExtra.CADENCE -> "Cadência"
    StoryExtra.FASTEST_KM -> "Km mais rápido"
    StoryExtra.DATE -> "Data"
}
