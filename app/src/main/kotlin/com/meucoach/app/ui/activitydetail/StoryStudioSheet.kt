package com.meucoach.app.ui.activitydetail

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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

/** Tabs of the Studio chrome; both share header, close and the segment. */
internal enum class StoryStudioTab(val label: String) {
    VIDEO("Vídeo"),
    ART("Arte PNG"),
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
    artActions: ArtStudioActions = ArtStudioActions(),
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
            artActions = artActions,
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
    artActions: ArtStudioActions = ArtStudioActions(),
) {
    val colors = McTheme.colors
    var tab by rememberSaveable { mutableStateOf(StoryStudioTab.VIDEO) }
    val videoScroll = rememberScrollState()
    // The video tab scrolls under the fixed chrome; the art tab has its own
    // fixed preview and an internal scroll for the controls (ADR 0060).
    LaunchedEffect(tab) { videoScroll.scrollTo(0) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
            .testTag("story-studio-content"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "STORY DO TREINO",
                    style = McTextStyles.overline,
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (tab == StoryStudioTab.VIDEO) "Personalize seu vídeo" else "Personalize sua arte",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                )
                if (tab == StoryStudioTab.VIDEO) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.recipe.summaryLine(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("story-studio-close"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Fechar Story Studio",
                    tint = colors.textSecondary,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        StudioTabSegment(selected = tab, onSelect = { tab = it })
        Spacer(modifier = Modifier.height(16.dp))
        if (tab == StoryStudioTab.VIDEO) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(videoScroll),
            ) {
                StoryVideoContent(
                    state = state,
                    avatar = avatar,
                    introSeen = introSeen,
                    onDismissIntro = onDismissIntro,
                    onPreset = onPreset,
                    onToggleExtra = onToggleExtra,
                    onDuration = onDuration,
                    onMapStyle = onMapStyle,
                    onSaveAsDefault = onSaveAsDefault,
                    onReset = onReset,
                    onGenerate = onGenerate,
                )
            }
        } else {
            ArtStudioContent(
                state = state,
                actions = artActions,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The video tab: preview, recipe cards and the generate CTA. */
@Composable
private fun StoryVideoContent(
    state: StoryStudioUiState,
    avatar: Bitmap?,
    introSeen: Boolean,
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
    StoryPreviewCard(
        plan = state.plan,
        recipe = state.recipe,
        avatar = avatar,
    )
    Spacer(modifier = Modifier.height(16.dp))
    McCard(variant = McCardVariant.DEFAULT) {
        StudioCardHeader(
            overline = "ESTILO",
            title = "Como começar?",
            subtitle = "Modelos prontos. Toque para trocar tudo de uma vez.",
            trailing = if (state.recipe.preset == StoryPreset.CUSTOM) {
                {
                    CustomPill()
                }
            } else {
                null
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        val presets = StoryPreset.entries.filter { it != StoryPreset.CUSTOM }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            presets.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { preset ->
                        PresetCard(
                            preset = preset,
                            selected = state.recipe.preset == preset,
                            onClick = { onPreset(preset) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        EssentialsRow()
    }
    Spacer(modifier = Modifier.height(16.dp))
    McCard(variant = McCardVariant.DEFAULT) {
        StudioCardHeader(
            overline = "EXTRAS (${state.recipe.extras.size}/${StoryRecipe.MAX_EXTRAS})",
            title = "Destaques do vídeo",
            subtitle = "Escolha até 2 métricas para o fechamento e os cards por km.",
            trailing = {
                ExtrasCounter(
                    selected = state.recipe.extras.size,
                    max = StoryRecipe.MAX_EXTRAS,
                )
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.extras.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { option ->
                        val selected = option.extra in state.recipe.extras
                        val blocked = !selected && state.recipe.extras.size >= StoryRecipe.MAX_EXTRAS
                        ExtraTile(
                            option = option,
                            selected = selected,
                            enabled = (option.isAvailable && !blocked) || selected,
                            onClick = { onToggleExtra(option.extra) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Até 2 extras. Sem dado, a métrica fica de fora — nunca vira zero.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    McCard(variant = McCardVariant.DEFAULT) {
        StudioCardHeader(
            overline = "VÍDEO",
            title = "Formato de saída",
            subtitle = "Duração e fundo do mapa. Vale para a prévia e o export.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        SectionLabel("DURAÇÃO")
        Spacer(modifier = Modifier.height(2.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StoryDuration.entries.forEach { duration ->
                StudioPill(
                    text = durationLabel(duration),
                    selected = state.recipe.duration == duration,
                    onClick = { onDuration(duration) },
                    modifier = Modifier.testTag("story-duration-${duration.name.lowercase(Locale.ROOT)}"),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Automática acompanha a distância. Padrão 1080p30 · pronto para Reels e TikTok.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = colors.borderSubtle)
        Spacer(modifier = Modifier.height(16.dp))
        SectionLabel("MAPA")
        Spacer(modifier = Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            mapStyles.forEach { (style, label) ->
                MapStyleCard(
                    label = label,
                    description = mapDescription(style),
                    icon = mapIcon(style),
                    selected = state.recipe.mapStyle == style,
                    onClick = { onMapStyle(style) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("story-map-${style.name.lowercase(Locale.ROOT)}"),
                )
            }
        }
        if (state.recipe.mapStyle == StoryMapStyle.TERRAIN) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Relevo do terreno via Mapzen · é baixado na primeira vez.",
                style = McTextStyles.caption,
                color = colors.textTertiary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "A prévia desenha a rota; o mapa escolhido entra no vídeo.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
    }
    if (!introSeen) {
        Spacer(modifier = Modifier.height(16.dp))
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
    Spacer(modifier = Modifier.height(16.dp))
    StudioSaveDefaultCard(
        save = state.saveAsDefault,
        onSave = onSaveAsDefault,
        onReset = onReset,
        saveTag = "story-save-default",
        resetTag = "story-reset-recipe",
        caption = "O próximo story já abre com estas escolhas.",
    )
    Spacer(modifier = Modifier.height(16.dp))
    McPrimaryButton(
        text = "Gerar vídeo",
        onClick = onGenerate,
        enabled = state.plan != null,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("story-generate"),
        minHeight = 56.dp,
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

/** Two-option segment of the chrome: Vídeo | Arte PNG. */
@Composable
private fun StudioTabSegment(selected: StoryStudioTab, onSelect: (StoryStudioTab) -> Unit) {
    val colors = McTheme.colors
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = colors.surfaceInset,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StoryStudioTab.entries.forEach { option ->
                val isSelected = option == selected
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isSelected) colors.accent else colors.surfaceInset,
                    contentColor = if (isSelected) colors.onAccent else colors.textSecondary,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onSelect(option) }
                        .semantics {
                            this.selected = isSelected
                            contentDescription = if (isSelected) {
                                "${option.label}, selecionado"
                            } else {
                                option.label
                            }
                        }
                        .testTag("story-tab-${option.name.lowercase(Locale.ROOT)}"),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryPreviewCard(plan: StoryPlan?, recipe: StoryRecipe, avatar: Bitmap?) {
    val colors = McTheme.colors
    var frame by rememberSaveable { mutableStateOf(StoryPreviewFrame.OPENING) }
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
    McCard(variant = McCardVariant.ELEVATED) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PRÉVIA",
                    style = McTextStyles.overline,
                    color = colors.textTertiary,
                )
                Text(
                    text = "Como vai ficar",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = colors.surfaceInset,
                border = BorderStroke(1.dp, colors.borderSubtle),
            ) {
                Text(
                    text = "9:16 · 1080p",
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
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
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surfaceInset)
                        .testTag("story-preview-loading"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Prévia indisponível sem rota GPS.",
                        style = McTextStyles.caption,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, colors.borderStrong),
                    color = colors.surfaceInset,
                ) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "Prévia do story — ${frame.label}",
                        modifier = Modifier
                            .height(PREVIEW_HEIGHT_DP)
                            .width(PREVIEW_HEIGHT_DP * 9f / 16f)
                            .clip(RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        FrameSegmentedControl(
            selected = frame,
            enabled = plan != null,
            onSelect = { frame = it },
        )
    }
}

@Composable
private fun FrameSegmentedControl(
    selected: StoryPreviewFrame,
    enabled: Boolean,
    onSelect: (StoryPreviewFrame) -> Unit,
) {
    val colors = McTheme.colors
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = colors.surfaceInset,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StoryPreviewFrame.entries.forEach { option ->
                val isSelected = option == selected
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isSelected) colors.accent else colors.surfaceInset,
                    contentColor = if (isSelected) colors.onAccent else colors.textSecondary,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(enabled = enabled) { onSelect(option) }
                        .semantics {
                            this.selected = isSelected
                            contentDescription = if (isSelected) {
                                "${option.label}, selecionado"
                            } else {
                                option.label
                            }
                        }
                        .testTag("story-frame-${option.name.lowercase(Locale.ROOT)}"),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomPill() {
    val colors = McTheme.colors
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = colors.accentSoft,
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.4f)),
    ) {
        Text(
            text = "Personalizado",
            style = McTextStyles.caption,
            color = colors.accentText,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun EssentialsRow() {
    val colors = McTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceInset,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = colors.accentSoft) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = colors.accentText,
                    modifier = Modifier.padding(6.dp).size(16.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "OBRIGATÓRIAS",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "Distância · Ritmo médio · Tempo total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun PresetCard(preset: StoryPreset, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    val border = if (selected) {
        BorderStroke(1.5.dp, colors.accent)
    } else {
        BorderStroke(1.dp, colors.borderSubtle)
    }
    val container = if (selected) colors.accentSoft else colors.surfaceInset
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = container,
        border = border,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = if (selected) {
                    "${presetLabel(preset)}, selecionado"
                } else {
                    presetLabel(preset)
                }
            }
            .testTag("story-preset-${preset.name.lowercase(Locale.ROOT)}"),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (selected) colors.accent else colors.surfaceElevated,
                    border = if (selected) null else BorderStroke(1.dp, colors.borderSubtle),
                ) {
                    Icon(
                        imageVector = presetIcon(preset),
                        contentDescription = null,
                        tint = if (selected) colors.onAccent else colors.textSecondary,
                        modifier = Modifier.padding(7.dp).size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = colors.accentText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = presetLabel(preset),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Text(
                text = presetDescription(preset),
                style = McTextStyles.caption,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun MapStyleCard(
    label: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = McTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.accentSoft else colors.surfaceInset,
        border = if (selected) {
            BorderStroke(1.5.dp, colors.accent)
        } else {
            BorderStroke(1.dp, colors.borderSubtle)
        },
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = if (selected) "$label, selecionado" else label
            },
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Surface(
                shape = CircleShape,
                color = if (selected) colors.accent else colors.surfaceElevated,
                border = if (selected) null else BorderStroke(1.dp, colors.borderSubtle),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) colors.onAccent else colors.textSecondary,
                    modifier = Modifier.padding(7.dp).size(16.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Text(
                text = description,
                style = McTextStyles.caption,
                color = colors.textSecondary,
            )
        }
    }
}

private fun presetLabel(preset: StoryPreset): String = when (preset) {
    StoryPreset.ESSENTIAL -> "Essencial"
    StoryPreset.PERFORMANCE -> "Performance"
    StoryPreset.PHYSIOLOGICAL -> "Fisiológico"
    StoryPreset.COMPLETE -> "Completo"
    StoryPreset.CUSTOM -> "Personalizado"
}

private fun presetDescription(preset: StoryPreset): String = when (preset) {
    StoryPreset.ESSENTIAL -> "Só o básico do treino"
    StoryPreset.PERFORMANCE -> "Ritmo e elevação"
    StoryPreset.PHYSIOLOGICAL -> "Coração e cadência"
    StoryPreset.COMPLETE -> "Resumo com calorias"
    StoryPreset.CUSTOM -> "Ajustado por você"
}

private fun presetIcon(preset: StoryPreset): ImageVector = when (preset) {
    StoryPreset.ESSENTIAL -> Icons.Outlined.CheckCircle
    StoryPreset.PERFORMANCE -> Icons.Outlined.Bolt
    StoryPreset.PHYSIOLOGICAL -> Icons.Outlined.FavoriteBorder
    StoryPreset.COMPLETE -> Icons.Outlined.Layers
    StoryPreset.CUSTOM -> Icons.Outlined.Check
}

private fun mapIcon(style: StoryMapStyle): ImageVector = when (style) {
    @Suppress("DEPRECATION")
    StoryMapStyle



        .
        SATELLITE,
    -> Icons.Outlined.DarkMode

    StoryMapStyle.CLASSIC -> Icons.Outlined.DarkMode

    StoryMapStyle.TERRAIN -> Icons.Outlined.Terrain

    StoryMapStyle.MEUCOACH_DARK -> Icons.Outlined.DarkMode

    StoryMapStyle.MEUCOACH_LIGHT -> Icons.Outlined.LightMode
}

private fun mapDescription(style: StoryMapStyle): String = when (style) {
    @Suppress("DEPRECATION")
    StoryMapStyle



        .
        SATELLITE,
    -> "Alto contraste"

    StoryMapStyle.CLASSIC -> "Alto contraste"

    StoryMapStyle.TERRAIN -> "Com relevo"

    StoryMapStyle.MEUCOACH_DARK -> "Alto contraste"

    StoryMapStyle.MEUCOACH_LIGHT -> "Claro e limpo"
}

private fun durationLabel(duration: StoryDuration): String = when (duration) {
    StoryDuration.AUTO -> "Auto"
    StoryDuration.SEC_15 -> "15 s"
    StoryDuration.SEC_30 -> "30 s"
    StoryDuration.SEC_45 -> "45 s"
    StoryDuration.SEC_60 -> "60 s"
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

    @Suppress("DEPRECATION")
    val map = when (mapStyle) {
        StoryMapStyle.CLASSIC -> "mapa clássico"

        // Satellite left the Studio (ADR 0040 §2.1); a saved legacy recipe
        // renders the dark style, so the summary names it.
        StoryMapStyle.SATELLITE -> "mapa escuro"

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
private val PREVIEW_HEIGHT_DP = 340.dp
