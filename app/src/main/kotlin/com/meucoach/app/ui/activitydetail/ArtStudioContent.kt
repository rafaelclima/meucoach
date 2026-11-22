package com.meucoach.app.ui.activitydetail

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meucoach.app.story.ArtContrast
import com.meucoach.app.story.StoryArtRenderer
import com.meucoach.app.story.StoryBranding
import com.meucoach.app.story.family
import com.meucoach.app.story.typeface
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McSecondaryButton
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.story.ArtColor
import com.meucoach.core.domain.story.ArtFont
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.StoryExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/** Preview bitmap size of the art tab: half of the 1080 export width. */
private const val ART_PREVIEW_WIDTH = 540

/** Debounce of the art preview render: one render in flight, at most 540 px. */
private const val ART_PREVIEW_DEBOUNCE_MS = 120L

/**
 * Art tab of the Story Studio (ADR 0058 §2.8): preview over a checkerboard,
 * format, extras, text/route swatches, font and the share/save CTAs. The
 * recipe lives in the view model; persistence lands with F7 and the export
 * pipeline with F6, so both CTAs stay wired but inert until then.
 */
@Composable
internal fun ArtStudioContent(state: StoryStudioUiState, actions: ArtStudioActions) {
    val colors = McTheme.colors
    val hasRoute = state.plan != null && state.plan.route.points.size >= 2
    val artOptions = state.extras.filter { it.extra in ArtRecipe.EXTRA_CHOICES }

    ArtPreviewCard(state = state)
    Spacer(modifier = Modifier.height(16.dp))
    McCard(variant = McCardVariant.DEFAULT) {
        StudioCardHeader(
            overline = "FORMATO",
            title = "Formato do card",
            subtitle = "4:5 é o padrão do feed; 1:1 é mais compacto.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ArtFormat.entries.forEach { format ->
                StudioPill(
                    text = formatLabel(format),
                    selected = state.artRecipe.format == format,
                    onClick = { actions.onFormat(format) },
                    modifier = Modifier.testTag("art-format-${format.name.lowercase(Locale.ROOT)}"),
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    McCard(variant = McCardVariant.DEFAULT) {
        StudioCardHeader(
            overline = "EXTRAS (${state.artRecipe.extras.size}/${ArtRecipe.MAX_EXTRAS})",
            title = "Destaques da arte",
            subtitle = "Até 2 métricas extras além de distância, ritmo e tempo.",
            trailing = {
                ExtrasCounter(
                    selected = state.artRecipe.extras.size,
                    max = ArtRecipe.MAX_EXTRAS,
                )
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            artOptions.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { option ->
                        val selected = option.extra in state.artRecipe.extras
                        val blocked = !selected && state.artRecipe.extras.size >= ArtRecipe.MAX_EXTRAS
                        ExtraTile(
                            option = option,
                            selected = selected,
                            enabled = (option.isAvailable && !blocked) || selected,
                            onClick = { actions.onToggleExtra(option.extra) },
                            modifier = Modifier.weight(1f),
                            tagPrefix = "art-extra",
                            selectionHint = "Na arte",
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
            text = "Sem dado, a métrica fica de fora — nunca vira zero.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    McCard(variant = McCardVariant.DEFAULT) {
        StudioCardHeader(
            overline = "CORES",
            title = "Texto e traçado",
            subtitle = "Escolhidos de forma independente; a arte é transparente.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        SwatchRow(
            title = "Cor do texto",
            selected = state.artRecipe.textColor,
            onSelected = actions.onTextColor,
            tagPrefix = "art-text-color",
        )
        Spacer(modifier = Modifier.height(16.dp))
        SwatchRow(
            title = "Cor do traçado",
            selected = state.artRecipe.routeColor,
            onSelected = actions.onRouteColor,
            tagPrefix = "art-route-color",
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    McCard(variant = McCardVariant.DEFAULT) {
        StudioCardHeader(
            overline = "FONTE",
            title = "Família da arte",
            subtitle = "Os nomes seguem em Inter; a prévia usa a selecionada.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ArtFont.entries.forEach { font ->
                StudioPill(
                    text = fontLabel(font),
                    selected = state.artRecipe.font == font,
                    onClick = { actions.onFont(font) },
                    modifier = Modifier.testTag("art-font-${font.name.lowercase(Locale.ROOT)}"),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceInset,
            border = BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("art-font-preview"),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = fontLabel(state.artRecipe.font),
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "5,12 km · 30:00 · çãá",
                    fontFamily = state.artRecipe.font.family,
                    fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                    color = colors.textPrimary,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    StudioSaveDefaultCard(
        save = state.artSaveAsDefault,
        onSave = actions.onSaveAsDefault,
        onReset = actions.onReset,
        saveTag = "art-save-default",
        resetTag = "art-reset-recipe",
        caption = "A próxima arte já abre com estas escolhas.",
    )
    Spacer(modifier = Modifier.height(16.dp))
    val export = state.artExport
    val exporting = export is ArtExportUiState.Exporting
    LaunchedEffect(export) {
        val done = export as? ArtExportUiState.Done
        if (done != null && done.forShare) actions.onShare(done.uri)
    }
    McPrimaryButton(
        text = if (exporting && (export as ArtExportUiState.Exporting).forShare) "Compartilhando…" else "Compartilhar",
        onClick = { actions.onShareClick() },
        enabled = hasRoute && !exporting,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("art-share"),
        minHeight = 56.dp,
    )
    Spacer(modifier = Modifier.height(10.dp))
    McSecondaryButton(
        text = if (exporting && !(export as ArtExportUiState.Exporting).forShare) "Salvando…" else "Salvar PNG",
        onClick = { actions.onSave() },
        enabled = hasRoute && !exporting,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("art-save"),
        minHeight = 48.dp,
    )
    when (export) {
        is ArtExportUiState.Done -> {
            if (!export.forShare) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "PNG salvo em Pictures/MeuCoach.",
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("art-export-done"),
                )
            }
        }

        is ArtExportUiState.Error -> {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = export.message,
                style = McTextStyles.caption,
                color = colors.danger,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("art-export-error"),
            )
            McTextButton(
                text = "Tentar novamente",
                onClick = { actions.onRetry() },
                modifier = Modifier.testTag("art-export-retry"),
            )
        }

        else -> Unit
    }
    if (!hasRoute) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Sem rota suficiente para a arte.",
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
    }
}

@Suppress("SwallowedException")
@Composable
private fun ArtPreviewCard(state: StoryStudioUiState) {
    val colors = McTheme.colors
    var darkPreview by rememberSaveable { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val typeface = remember(context) { state.artRecipe.font.typeface(context) }
    val logo = remember(context) { StoryBranding.logo(context) }
    val recipe = state.artRecipe
    val plan = state.plan
    val format = recipe.format
    val previewHeight = (ART_PREVIEW_WIDTH * format.height / format.width)
    val warning = ArtContrast.warning(recipe.textColor, recipe.routeColor, darkPreview)
    val previewDescription = buildString {
        append("Prévia da arte ${formatLabel(format)}")
        append(", texto ${swatchLabel(recipe.textColor)}")
        append(", traçado ${swatchLabel(recipe.routeColor)}")
        append(", fonte ${fontLabel(recipe.font)}")
        append(", fundo transparente")
    }
    val preview by produceState<ArtPreviewState>(
        initialValue = ArtPreviewState.Loading,
        plan,
        recipe,
        darkPreview,
        retry,
    ) {
        when {
            plan == null || plan.route.points.size < 2 -> value = ArtPreviewState.Empty

            else -> {
                // Debounce: rapid taps render once; the previous preview stays.
                delay(ART_PREVIEW_DEBOUNCE_MS)
                value = withContext(Dispatchers.Default) {
                    try {
                        val bitmap = StoryArtRenderer(recipe, typeface, logo)
                            .render(plan, plan.localDate, ART_PREVIEW_WIDTH, previewHeight)
                        if (bitmap != null) ArtPreviewState.Ready(bitmap) else ArtPreviewState.Empty
                    } catch (error: Throwable) {
                        // The preview degrades to a retry affordance instead of
                        // crashing the Studio; the export path is the one that
                        // must report render failures loudly (F6).
                        ArtPreviewState.Error
                    }
                }
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
                    text = "Arte transparente",
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
                    text = "${formatLabel(format)} · 1080 px",
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
                .testTag("art-preview"),
            contentAlignment = Alignment.Center,
        ) {
            val checker = if (darkPreview) {
                ArtContrast.DARK_CHECKER to ArtContrast.DARK_CHECKER_WORST
            } else {
                ArtContrast.LIGHT_CHECKER to ArtContrast.LIGHT_CHECKER_WORST
            }
            val previewWidth = 300.dp * format.width.toFloat() / format.height.toFloat()
            Box(
                modifier = Modifier
                    .height(300.dp)
                    .width(previewWidth)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(checker.first))
                    .semantics { contentDescription = previewDescription },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val cell = 20.dp.toPx()
                    val columns = ceilOf(size.width / cell)
                    val rows = ceilOf(size.height / cell)
                    for (row in 0 until rows) {
                        for (column in 0 until columns) {
                            drawRect(
                                color = Color(if ((row + column) % 2 == 0) checker.first else checker.second),
                                topLeft = Offset(column * cell, row * cell),
                                size = Size(cell, cell),
                            )
                        }
                    }
                }
                when (val preview = preview) {
                    is ArtPreviewState.Ready -> Image(
                        bitmap = preview.bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .matchParentSize()
                            .testTag("art-preview-ready"),
                        contentScale = ContentScale.Fit,
                    )

                    ArtPreviewState.Empty -> Text(
                        text = "Sem rota suficiente para a arte",
                        style = McTextStyles.caption,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .padding(16.dp)
                            .testTag("art-preview-empty"),
                    )

                    ArtPreviewState.Error -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .padding(16.dp)
                            .testTag("art-preview-error"),
                    ) {
                        Text(
                            text = "A prévia falhou.",
                            style = McTextStyles.caption,
                            color = colors.textSecondary,
                        )
                        McTextButton(
                            text = "Tentar novamente",
                            onClick = { retry++ },
                            modifier = Modifier.testTag("art-retry"),
                        )
                    }

                    ArtPreviewState.Loading -> Unit
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Fundo transparente",
            style = McTextStyles.caption,
            color = colors.textTertiary,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("art-transparent-label"),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioPill(
                text = "Fundo escuro",
                selected = darkPreview,
                onClick = { darkPreview = true },
                modifier = Modifier.testTag("art-checker-dark"),
            )
            StudioPill(
                text = "Fundo claro",
                selected = !darkPreview,
                onClick = { darkPreview = false },
                modifier = Modifier.testTag("art-checker-light"),
            )
        }
        warning?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = McTextStyles.caption,
                color = colors.warning,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("art-contrast-warning"),
            )
        }
    }
}

@Composable
private fun SwatchRow(title: String, selected: ArtColor, onSelected: (ArtColor) -> Unit, tagPrefix: String) {
    val colors = McTheme.colors
    Text(
        text = title,
        style = McTextStyles.caption,
        color = colors.textTertiary,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    )
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArtColor.entries.forEach { color ->
            val isSelected = color == selected
            Surface(
                shape = CircleShape,
                color = Color(color.argb),
                border = if (isSelected) {
                    BorderStroke(2.dp, colors.textPrimary)
                } else {
                    BorderStroke(1.dp, colors.borderSubtle)
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onSelected(color) }
                    .semantics {
                        this.selected = isSelected
                        contentDescription = if (isSelected) {
                            "$title: ${swatchLabel(color)}, selecionada"
                        } else {
                            "$title: ${swatchLabel(color)}"
                        }
                    }
                    .testTag("$tagPrefix-${color.name.lowercase(Locale.ROOT)}"),
            ) {
                if (isSelected) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = if (isDarkSwatch(color)) Color.White else Color.Black,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

private sealed interface ArtPreviewState {
    data object Loading : ArtPreviewState

    data object Empty : ArtPreviewState

    data object Error : ArtPreviewState

    data class Ready(val bitmap: Bitmap) : ArtPreviewState
}

private fun ceilOf(value: Float): Int = Math.ceil(value.toDouble()).toInt().coerceAtLeast(1)

internal fun formatLabel(format: ArtFormat): String = when (format) {
    ArtFormat.PORTRAIT_4_5 -> "4:5"
    ArtFormat.SQUARE -> "1:1"
}

internal fun fontLabel(font: ArtFont): String = when (font) {
    ArtFont.INTER -> "Inter"
    ArtFont.GEIST -> "Geist"
    ArtFont.BEBAS_NEUE -> "Bebas Neue"
    ArtFont.ANTON -> "Anton"
    ArtFont.OSWALD -> "Oswald"
    ArtFont.ARCHIVO_BLACK -> "Archivo Black"
}

internal fun swatchLabel(color: ArtColor): String = when (color) {
    ArtColor.LIME -> "lima"
    ArtColor.CORAL -> "coral"
    ArtColor.WHITE -> "branco"
    ArtColor.BLACK -> "preto"
    ArtColor.GRAY_LIGHT -> "cinza claro"
    ArtColor.GRAY_DARK -> "cinza escuro"
    ArtColor.CYAN -> "ciano"
    ArtColor.BLUE -> "azul"
}

internal fun isDarkSwatch(color: ArtColor): Boolean = when (color) {
    ArtColor.BLACK, ArtColor.GRAY_DARK, ArtColor.CYAN, ArtColor.BLUE -> true
    else -> false
}

internal fun ArtRecipe.summaryLine(): String = "${formatLabel(
    format,
)} · ${fontLabel(font)} · texto ${swatchLabel(textColor)} · traçado ${swatchLabel(routeColor)}."
