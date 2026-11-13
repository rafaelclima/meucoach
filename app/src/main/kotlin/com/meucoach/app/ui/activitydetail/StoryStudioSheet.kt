package com.meucoach.app.ui.activitydetail

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.meucoach.app.story.StoryBranding
import com.meucoach.app.story.StoryFrameRenderer
import com.meucoach.app.story.toStoryPalette
import com.meucoach.core.designsystem.R
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.story.StoryDuration
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryMapStyle
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryPreset
import com.meucoach.core.domain.story.StoryRecipe
import com.meucoach.core.domain.story.StorySegmentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Frames shown by the Studio scrubber, in narrative order. */
internal enum class StoryPreviewFrame(val label: String) {
    OPENING("Abertura"),
    KM("Km"),
    FINAL("Final"),
    ;

    /**
     * Opening sits just after the first fade, the km frame lands one second
     * inside the first card and the final frame stays before the fade out.
     */
    fun timeSec(plan: StoryPlan): Float = when (this) {
        OPENING -> 0.9f

        KM ->
            plan.segments
                .firstOrNull { it.type == StorySegmentType.CARD }
                ?.let { it.startSec + 1f }
                ?: (plan.durationSec / 2f)

        FINAL -> (plan.durationSec - 0.6f).coerceAtLeast(0f)
    }
}

/**
 * Modal Story Studio: recipe controls on top of the exact frames the export
 * will draw. The content composable is public to tests so the sheet chrome
 * (window, drag handle) is not part of every assertion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoryStudioSheet(
    state: StoryStudioUiState,
    avatar: Bitmap?,
    introSeen: Boolean,
    onDismiss: () -> Unit,
    onDismissIntro: () -> Unit,
    onPreset: (StoryPreset) -> Unit,
    onToggleExtra: (StoryExtra) -> Unit,
    onDuration: (StoryDuration) -> Unit,
    onMapStyle: (StoryMapStyle) -> Unit,
    onSaveAsDefault: (Boolean) -> Unit,
    onReset: () -> Unit,
    onGenerate: () -> Unit,
) {
    val colors = McTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        modifier = Modifier.testTag("story-studio-sheet"),
    ) {
        StoryStudioContent(
            state = state,
            avatar = avatar,
            introSeen = introSeen,
            onClose = onDismiss,
            onDismissIntro = onDismissIntro,
            onPreset = onPreset,
            onToggleExtra = onToggleExtra,
            onDuration = onDuration,
            onMapStyle = onMapStyle,
            onSaveAsDefault = onSaveAsDefault,
            onReset = onReset,
            onGenerate = onGenerate,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        )
    }
}

@Composable
internal fun StoryStudioContent(
    state: StoryStudioUiState,
    avatar: Bitmap?,
    introSeen: Boolean,
    onClose: () -> Unit,
    onDismissIntro: () -> Unit,
    onPreset: (StoryPreset) -> Unit,
    onToggleExtra: (StoryExtra) -> Unit,
    onDuration: (StoryDuration) -> Unit,
    onMapStyle: (StoryMapStyle) -> Unit,
    onSaveAsDefault: (Boolean) -> Unit,
    onReset: () -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = McTheme.colors
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            .testTag("story-studio-content"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "STORY DO TREINO",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
                Text(
                    text = "Escolha o que aparece",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("story-studio-close"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Fechar Story Studio",
                    tint = colors.textSecondary,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        StoryPreviewBlock(
            plan = state.plan,
            recipe = state.recipe,
            avatar = avatar,
        )
        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel("ESTILO")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StoryPreset.entries
                .filter { it != StoryPreset.CUSTOM }
                .forEach { preset ->
                    RecipeChip(
                        text = presetLabel(preset),
                        selected = state.recipe.preset == preset,
                        onClick = { onPreset(preset) },
                        modifier = Modifier.testTag("story-preset-${preset.name.lowercase(Locale.ROOT)}"),
                    )
                }
        }
        Spacer(modifier = Modifier.height(18.dp))
        SectionLabel("OBRIGATÓRIAS")
        Text(
            text = "Distância · Ritmo médio · Tempo total",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(18.dp))
        SectionLabel("EXTRAS (${state.recipe.extras.size}/${StoryRecipe.MAX_EXTRAS})")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.extras.forEach { option ->
                val selected = option.extra in state.recipe.extras
                val blockedByLimit = !selected && state.recipe.extras.size >= StoryRecipe.MAX_EXTRAS
                RecipeChip(
                    text = if (option.isAvailable) {
                        option.extra.uiLabel()
                    } else {
                        "${option.extra.uiLabel()} · ${option.reason}"
                    },
                    selected = selected,
                    enabled = (option.isAvailable && !blockedByLimit) || selected,
                    onClick = { onToggleExtra(option.extra) },
                    modifier = Modifier.testTag("story-extra-${option.extra.name.lowercase(Locale.ROOT)}"),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Até 2 extras. Sem dado, a métrica fica de fora — nunca vira zero.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(18.dp))
        SectionLabel("DURAÇÃO")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StoryDuration.entries.forEach { duration ->
                RecipeChip(
                    text = durationLabel(duration),
                    selected = state.recipe.duration == duration,
                    onClick = { onDuration(duration) },
                    modifier = Modifier.testTag("story-duration-${duration.name.lowercase(Locale.ROOT)}"),
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        SectionLabel("MAPA")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            mapStyles.forEach { (style, label) ->
                RecipeChip(
                    text = label,
                    selected = state.recipe.mapStyle == style,
                    onClick = { onMapStyle(style) },
                    modifier = Modifier.testTag("story-map-${style.name.lowercase(Locale.ROOT)}"),
                )
            }
        }
        if (state.recipe.mapStyle == StoryMapStyle.TERRAIN) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Relevo do terreno via Mapzen · baixa na primeira vez.",
                style = McTextStyles.caption,
                color = colors.textTertiary,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "A prévia desenha a rota; o mapa escolhido entra no vídeo.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Padrão 1080p30 · pronto para Reels e TikTok.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        if (!introSeen) {
            Spacer(modifier = Modifier.height(18.dp))
            McCard(variant = McCardVariant.INSET) {
                Text(
                    text = "Antes de gerar",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Na primeira vez, baixamos os tiles do mapa e, no estilo Terreno, " +
                        "o relevo. Depois o app usa o cache. " +
                        "Nenhuma coordenada exata sai do aparelho.",
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                McSecondaryButton(
                    text = "Entendi",
                    onClick = onDismissIntro,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("story-intro-dismiss"),
                    minHeight = 48.dp,
                )
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .toggleable(
                    value = state.saveAsDefault,
                    role = Role.Checkbox,
                    onValueChange = onSaveAsDefault,
                )
                .testTag("story-save-default"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = state.saveAsDefault,
                onCheckedChange = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Salvar como padrão",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = "O próximo story já abre com estas escolhas.",
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
            }
        }
        McTextButton(
            text = "Restaurar padrão",
            onClick = onReset,
            modifier = Modifier.testTag("story-reset-recipe"),
        )
        Spacer(modifier = Modifier.height(8.dp))
        McPrimaryButton(
            text = "Gerar vídeo",
            onClick = onGenerate,
            enabled = state.plan != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("story-generate"),
            minHeight = 48.dp,
        )
        if (state.plan == null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Esta atividade não tem rota GPS suficiente para um story.",
                style = McTextStyles.caption,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun StoryPreviewBlock(plan: StoryPlan?, recipe: StoryRecipe, avatar: Bitmap?) {
    var frame by rememberSaveable { mutableStateOf(StoryPreviewFrame.OPENING) }
    val colors = McTheme.colors
    val context = LocalContext.current
    val typeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.inter_variable) ?: Typeface.DEFAULT
    }
    val logo = remember(context) { StoryBranding.logo(context) }
    val storyPalette = McTheme.colors.toStoryPalette(recipe.mapStyle)
    val timeSec = plan?.let(frame::timeSec)
    val bitmap by produceState<Bitmap?>(initialValue = null, plan, recipe, storyPalette, timeSec, avatar) {
        value = if (plan == null || timeSec == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                StoryFrameRenderer(
                    palette = storyPalette,
                    typeface = typeface,
                    logoBitmap = logo,
                    mapBackground = null,
                    avatarBitmap = avatar,
                    recipe = recipe,
                ).render(
                    plan = plan,
                    timeSec = timeSec,
                    width = PREVIEW_WIDTH,
                    height = PREVIEW_HEIGHT,
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("story-preview"),
        contentAlignment = Alignment.Center,
    ) {
        val preview = bitmap
        if (preview == null) {
            Box(
                modifier = Modifier
                    .height(PREVIEW_HEIGHT_DP)
                    .width(PREVIEW_HEIGHT_DP * 9f / 16f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceInset)
                    .testTag("story-preview-loading"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Prévia indisponível sem rota GPS.",
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
            }
        } else {
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = "Prévia do story — ${frame.label}",
                modifier = Modifier
                    .height(PREVIEW_HEIGHT_DP)
                    .width(PREVIEW_HEIGHT_DP * 9f / 16f)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StoryPreviewFrame.entries.forEach { option ->
            RecipeChip(
                text = option.label,
                selected = frame == option,
                enabled = plan != null,
                onClick = { frame = option },
                modifier = Modifier
                    .weight(1f)
                    .testTag("story-frame-${option.name.lowercase(Locale.ROOT)}"),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = McTextStyles.caption,
        color = McTheme.colors.textTertiary,
    )
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun RecipeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val semantics = Modifier.semantics {
        this.selected = selected
        contentDescription = if (selected) "$text, selecionado" else text
    }
    if (selected) {
        McPrimaryButton(
            text = text,
            onClick = onClick,
            modifier = modifier.then(semantics),
            enabled = enabled,
            minHeight = 48.dp,
            horizontalPadding = 12.dp,
        )
    } else {
        McSecondaryButton(
            text = text,
            onClick = onClick,
            modifier = modifier.then(semantics),
            enabled = enabled,
            minHeight = 48.dp,
            horizontalPadding = 12.dp,
        )
    }
}

private fun presetLabel(preset: StoryPreset): String = when (preset) {
    StoryPreset.ESSENTIAL -> "Essencial"
    StoryPreset.PERFORMANCE -> "Performance"
    StoryPreset.PHYSIOLOGICAL -> "Fisiológico"
    StoryPreset.COMPLETE -> "Completo"
    StoryPreset.CUSTOM -> "Personalizado"
}

private fun durationLabel(duration: StoryDuration): String = when (duration) {
    StoryDuration.AUTO -> "Auto"
    StoryDuration.SEC_15 -> "15 s"
    StoryDuration.SEC_30 -> "30 s"
    StoryDuration.SEC_45 -> "45 s"
    StoryDuration.SEC_60 -> "60 s"
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

internal fun StoryRecipe.summaryLine(): String {
    val extrasPart = if (extras.isEmpty()) {
        "Três essenciais"
    } else {
        "Três essenciais + " + extras.map { it.uiLabel() }.joinToString(" e ")
    }
    val duration = when (this.duration) {
        StoryDuration.AUTO -> "duração automática"
        StoryDuration.SEC_15 -> "15 s"
        StoryDuration.SEC_30 -> "30 s"
        StoryDuration.SEC_45 -> "45 s"
        StoryDuration.SEC_60 -> "60 s"
    }
    val map = when (mapStyle) {
        StoryMapStyle.CLASSIC -> "mapa clássico"
        StoryMapStyle.SATELLITE -> "satélite"
        StoryMapStyle.TERRAIN -> "mapa terreno"
        StoryMapStyle.MEUCOACH_DARK -> "mapa escuro"
        StoryMapStyle.MEUCOACH_LIGHT -> "mapa claro"
    }
    return "$extrasPart · $duration · $map."
}

private val mapStyles = listOf(
    StoryMapStyle.MEUCOACH_DARK to "Escuro",
    StoryMapStyle.MEUCOACH_LIGHT to "Claro",
    StoryMapStyle.TERRAIN to "Terreno",
)

private const val PREVIEW_WIDTH = 540
private const val PREVIEW_HEIGHT = 960
private val PREVIEW_HEIGHT_DP = 300.dp
