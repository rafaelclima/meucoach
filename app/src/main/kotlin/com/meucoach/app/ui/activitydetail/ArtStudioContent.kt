package com.meucoach.app.ui.activitydetail

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
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
import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtBlock
import com.meucoach.core.domain.story.ArtColor
import com.meucoach.core.domain.story.ArtFont
import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.ArtLayout
import com.meucoach.core.domain.story.ArtPhotoCrop
import com.meucoach.core.domain.story.ArtPhotoRef
import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.ArtTemplate
import com.meucoach.core.domain.story.StoryExtra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/** Preview bitmap size of the art tab: half of the 1080 export width. */
private const val ART_PREVIEW_WIDTH = 540

/** Confirmation of a gallery save, inline and in the snackbar. */
private const val ART_SAVED_JPG = "JPG salvo em Pictures/MeuCoach."
private const val ART_SAVED_PNG = "PNG salvo em Pictures/MeuCoach."

/** Stale-export copy, inline and in the snackbar. */
private const val ART_STALE_COPY = "Receita alterada — gere de novo."

/** Debounce of the art preview render: one render in flight, at most 540 px. */
private const val ART_PREVIEW_DEBOUNCE_MS = 120L

/** Minimum contrast of a swatch border against the Studio surface. */
private const val SWATCH_BORDER_CONTRAST = 3.0

/**
 * Art tab of the Story Studio (ADR 0058 §2.8, ADR 0060, photo studio ADR 0061):
 * the preview stays fixed at the top and the controls scroll below it. The
 * effective recipe (style + per-activity photo) lives in the view model; the
 * crop step takes over the tab full-screen while editing.
 */
@Composable
internal fun ArtStudioContent(
    state: StoryStudioUiState,
    actions: ArtStudioActions,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    if (state.artCropEditing) {
        ArtCropEditor(
            state = state,
            actions = actions,
            modifier = modifier,
        )
        return
    }

    val colors = McTheme.colors
    val hasRoute = state.plan != null && state.plan.route.points.size >= 2
    val artOptions = state.extras.filter { it.extra in ArtRecipe.EXTRA_CHOICES }
    var darkPreview by rememberSaveable { mutableStateOf(true) }
    val isPhoto = state.artRecipe.background == ArtBackgroundMode.PHOTO
    val warning = if (isPhoto) {
        ArtContrast.photoWarning(state.artRecipe.textColor, state.artRecipe.routeColor)
    } else {
        ArtContrast.warning(state.artRecipe.textColor, state.artRecipe.routeColor)
    }
    val export = state.artExport
    val inFlight = export as? ArtExportUiState.Exporting
    val exporting = inFlight != null
    val busy = state.artPhotoBusy
    val controlsEnabled = !busy && !exporting
    val photo = state.artRecipe.photo
    val canExport = hasRoute && !exporting && !busy && (!isPhoto || photo != null)
    // The save confirmation used to live only below the CTAs, off-screen on
    // small displays: the snackbar repeats it where the user is looking.
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(export) {
        when (export) {
            is ArtExportUiState.Done ->
                if (!export.forShare) {
                    snackbar.showSnackbar(if (isPhoto) ART_SAVED_JPG else ART_SAVED_PNG)
                }

            is ArtExportUiState.Error -> snackbar.showSnackbar(export.message)

            ArtExportUiState.Stale -> snackbar.showSnackbar(ART_STALE_COPY)

            else -> Unit
        }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> actions.onPickPhoto(uri) }

    // Give the controls the larger share; on 200% or short screens the
    // preview image shrinks first instead of squeezing the scroll area.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val artHeight = (maxHeight * 0.40f).coerceIn(120.dp, 300.dp)
        Column(modifier = Modifier.fillMaxSize()) {
            ArtPreviewCard(
                state = state,
                darkPreview = darkPreview,
                artHeight = artHeight,
                onLoadPhoto = actions.onLoadPhoto,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                ) {
                    Text(
                        text = state.artRecipe.summaryLine(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.testTag("art-summary"),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pôster automático: opaco, nasce com o vídeo. " +
                            "Arte: transparente (PNG) ou com sua foto (JPG).",
                        style = McTextStyles.caption,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("art-poster-vs-art"),
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Fundo: toggle Transparente | Foto + checker or photo card.
                    SectionLabel("FUNDO")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.selectableGroup(),
                    ) {
                        StudioPill(
                            text = "Transparente",
                            selected = !isPhoto,
                            enabled = controlsEnabled,
                            onClick = { actions.onBackground(ArtBackgroundMode.TRANSPARENT) },
                            modifier = Modifier.testTag("art-background-transparent"),
                        )
                        StudioPill(
                            text = "Foto",
                            selected = isPhoto,
                            enabled = controlsEnabled,
                            onClick = { actions.onBackground(ArtBackgroundMode.PHOTO) },
                            modifier = Modifier.testTag("art-background-photo"),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isPhoto) {
                        Text(
                            text = "Foto de fundo",
                            style = McTextStyles.caption,
                            color = colors.textTertiary,
                            modifier = Modifier.testTag("art-photo-label"),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ArtPhotoCard(
                            state = state,
                            actions = actions,
                            enabled = controlsEnabled,
                            onPick = {
                                pickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        )
                    } else {
                        Text(
                            text = "Fundo transparente",
                            style = McTextStyles.caption,
                            color = colors.textTertiary,
                            modifier = Modifier.testTag("art-transparent-label"),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
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
                    }
                    if (state.artMissingPhotoWarning) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = ActivityDetailViewModel.PHOTO_MISSING_WARNING,
                            style = McTextStyles.caption,
                            color = colors.warning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("art-photo-missing"),
                        )
                        McTextButton(
                            text = "Entendi",
                            onClick = { actions.onDismissMissingPhoto() },
                            modifier = Modifier.testTag("art-photo-missing-dismiss"),
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
                    Spacer(modifier = Modifier.height(16.dp))
                    McCard(variant = McCardVariant.DEFAULT) {
                        StudioCardHeader(
                            overline = "FORMATO",
                            title = "Formato do card",
                            subtitle = "4:5 é o padrão do feed; 1:1 é compacto; 9:16 é para story.",
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
                                    enabled = controlsEnabled,
                                    onClick = { actions.onFormat(format) },
                                    modifier = Modifier.testTag("art-format-${format.name.lowercase(Locale.ROOT)}"),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    ArtPositionCard(
                        state = state,
                        actions = actions,
                        enabled = controlsEnabled,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ArtTextScaleCard(
                        state = state,
                        actions = actions,
                        enabled = controlsEnabled,
                    )
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
                                            enabled = (option.isAvailable && !blocked && controlsEnabled) || selected,
                                            onClick = { actions.onToggleExtra(option.extra) },
                                            modifier = Modifier.weight(1f),
                                            tagPrefix = "art-extra",
                                            selectionHint = "Na arte",
                                            limitReached = blocked,
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
                    McCard(
                        modifier = Modifier.testTag("art-colors"),
                        variant = McCardVariant.DEFAULT,
                    ) {
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
                            enabled = controlsEnabled,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SwatchRow(
                            title = "Cor do traçado",
                            selected = state.artRecipe.routeColor,
                            onSelected = actions.onRouteColor,
                            tagPrefix = "art-route-color",
                            enabled = controlsEnabled,
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
                                    enabled = controlsEnabled,
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
                        caption = "Salva o estilo ao compartilhar ou salvar. A foto fica salva na atividade.",
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LaunchedEffect(export) {
                        val done = export as? ArtExportUiState.Done
                        if (done != null && done.forShare) {
                            actions.onShare(done.uri)
                            // P0-2: the event belongs to the view model; consuming it keeps
                            // rotation, tab switches and reopen from replaying the same sheet.
                            actions.onShareConsumed()
                        }
                    }
                    McPrimaryButton(
                        text = if (inFlight?.forShare == true) "Compartilhando…" else "Compartilhar",
                        onClick = { actions.onShareClick() },
                        enabled = canExport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("art-share"),
                        minHeight = 56.dp,
                        loading = inFlight?.forShare == true,
                        loadingTag = "art-share-progress",
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val saveLabel = if (isPhoto) "Salvar JPG" else "Salvar PNG"
                    McSecondaryButton(
                        text = if (inFlight != null && !inFlight.forShare) "Salvando…" else saveLabel,
                        onClick = { actions.onSave() },
                        enabled = canExport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("art-save"),
                        minHeight = 48.dp,
                        loading = inFlight != null && !inFlight.forShare,
                        loadingTag = "art-save-progress",
                    )
                    if (busy) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Processando foto…",
                            style = McTextStyles.caption,
                            color = colors.textSecondary,
                            modifier = Modifier.testTag("art-photo-busy"),
                        )
                    }
                    state.artPhotoError?.let { message ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = message,
                            style = McTextStyles.caption,
                            color = colors.danger,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("art-photo-error"),
                        )
                        McTextButton(
                            text = "Tentar de novo",
                            onClick = { actions.onRetryPhoto() },
                            modifier = Modifier.testTag("art-photo-retry"),
                        )
                    }
                    when (export) {
                        is ArtExportUiState.Done -> {
                            if (!export.forShare) {
                                Spacer(modifier = Modifier.height(6.dp))
                                val confirmation = if (state.artRecipe.background == ArtBackgroundMode.PHOTO) {
                                    ART_SAVED_JPG
                                } else {
                                    ART_SAVED_PNG
                                }
                                Text(
                                    text = confirmation,
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
                                text = "Tentar de novo",
                                onClick = { actions.onRetry() },
                                modifier = Modifier.testTag("art-export-retry"),
                            )
                        }

                        ArtExportUiState.Stale -> {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = ART_STALE_COPY,
                                style = McTextStyles.caption,
                                color = colors.textSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("art-export-stale"),
                            )
                        }

                        else -> Unit
                    }
                    if (!hasRoute) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Esta atividade não tem rota GPS; a arte precisa de pelo menos 2 pontos.",
                            style = McTextStyles.caption,
                            color = colors.textSecondary,
                            modifier = Modifier.testTag("art-no-route"),
                        )
                    } else if (isPhoto && photo == null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Escolha uma foto para o fundo",
                            style = McTextStyles.caption,
                            color = colors.textSecondary,
                            modifier = Modifier.testTag("art-photo-required"),
                        )
                    }
                }
                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                )
            }
        }
    }

    if (state.artShowRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { actions.onCancelRemove() },
            title = { Text(text = "Remover a foto?") },
            text = { Text(text = "Remover a foto? A arte volta ao fundo transparente.") },
            confirmButton = {
                TextButton(
                    onClick = { actions.onRemovePhoto() },
                    modifier = Modifier.testTag("art-photo-confirm-remove"),
                ) {
                    Text(text = "Remover", color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { actions.onCancelRemove() },
                    modifier = Modifier.testTag("art-photo-cancel-remove"),
                ) {
                    Text(text = "Cancelar", color = colors.textPrimary)
                }
            },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
        )
    }
}

@Suppress("SwallowedException")
@Composable
private fun ArtPreviewCard(
    state: StoryStudioUiState,
    darkPreview: Boolean,
    artHeight: Dp,
    onLoadPhoto: suspend (ArtPhotoRef, ArtFormat, Int) -> Bitmap?,
) {
    val colors = McTheme.colors
    var retry by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val logo = remember(context) { StoryBranding.logo(context) }
    val recipe = state.artRecipe
    val plan = state.plan
    val format = recipe.format
    val previewHeight = (ART_PREVIEW_WIDTH * format.height / format.width)
    val previewDescription = buildString {
        append("Prévia da arte ${formatLabel(format)}")
        append(", texto ${swatchLabel(recipe.textColor)}")
        append(", traçado ${swatchLabel(recipe.routeColor)}")
        append(", fonte ${fontLabel(recipe.font)}")
        if (recipe.extras.isNotEmpty()) {
            append(", extras ${recipe.extras.joinToString(" e ") { it.uiLabel().lowercase(Locale.ROOT) }}")
        }
        if (recipe.background == ArtBackgroundMode.PHOTO) {
            append(", fundo foto")
        } else {
            append(", fundo transparente")
        }
        append(", layout ${templateLabel(recipe.layout.template)}")
        append(", texto ${recipe.layout.textScalePercent}%")
    }
    val photoRef = recipe.photo
    val isPhoto = recipe.background == ArtBackgroundMode.PHOTO
    val preview by produceState<ArtPreviewState>(
        initialValue = ArtPreviewState.Loading,
        plan,
        recipe,
        retry,
        photoRef,
    ) {
        when {
            plan == null || plan.route.points.size < 2 -> value = ArtPreviewState.Empty

            isPhoto && photoRef == null -> value = ArtPreviewState.PhotoEmpty

            else -> {
                // Debounce: rapid taps render once; the previous preview stays.
                delay(ART_PREVIEW_DEBOUNCE_MS)
                value = withContext(Dispatchers.Default) {
                    try {
                        // P0-1: the typeface is resolved inside the producer, so
                        // it always belongs to the captured recipe.
                        val typeface = recipe.font.typeface(context)
                        val photoBitmap = if (isPhoto && photoRef != null) {
                            runCatching { onLoadPhoto(photoRef, format, ART_PREVIEW_WIDTH) }.getOrNull()
                        } else {
                            null
                        }
                        if (isPhoto && photoBitmap == null) {
                            ArtPreviewState.PhotoEmpty
                        } else {
                            val bitmap = StoryArtRenderer(recipe, typeface, logo)
                                .render(plan, plan.localDate, ART_PREVIEW_WIDTH, previewHeight, photoBitmap)
                            photoBitmap?.recycle()
                            if (bitmap != null) {
                                (value as? ArtPreviewState.Ready)?.bitmap?.recycle()
                                ArtPreviewState.Ready(bitmap, recipe.font)
                            } else {
                                ArtPreviewState.Empty
                            }
                        }
                    } catch (error: Throwable) {
                        ArtPreviewState.Error
                    }
                }
            }
        }
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.borderSubtle),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Compact chrome: the fixed preview keeps only the overline and
            // the size badge so the controls retain height at 200%.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "PRÉVIA",
                    style = McTextStyles.overline,
                    color = colors.textTertiary,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colors.surfaceInset,
                    border = BorderStroke(1.dp, colors.borderSubtle),
                ) {
                    Text(
                        text = "${format.width}×${format.height}",
                        style = McTextStyles.caption,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("art-format-badge"),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("art-preview"),
                contentAlignment = Alignment.Center,
            ) {
                val isPhotoPreview = recipe.background == ArtBackgroundMode.PHOTO
                val checker = if (darkPreview) {
                    ArtContrast.DARK_CHECKER to ArtContrast.DARK_CHECKER_WORST
                } else {
                    ArtContrast.LIGHT_CHECKER to ArtContrast.LIGHT_CHECKER_WORST
                }
                val previewWidth = artHeight * format.width.toFloat() / format.height.toFloat()
                Box(
                    modifier = Modifier
                        .height(artHeight)
                        .width(previewWidth)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isPhotoPreview) Color.Black else Color(checker.first))
                        .semantics { contentDescription = previewDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    if (!isPhotoPreview) {
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
                    }
                    when (val preview = preview) {
                        is ArtPreviewState.Ready -> Image(
                            bitmap = preview.bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .matchParentSize()
                                .testTag("art-preview-ready-${preview.font.name.lowercase(Locale.ROOT)}"),
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
                                text = "Recarregar prévia",
                                onClick = { retry++ },
                                modifier = Modifier.testTag("art-retry"),
                            )
                        }

                        ArtPreviewState.PhotoEmpty -> Text(
                            text = "Escolha uma foto para o fundo",
                            style = McTextStyles.caption,
                            color = colors.textSecondary,
                            modifier = Modifier
                                .padding(16.dp)
                                .testTag("art-preview-photo-empty"),
                        )

                        // Only the first render reaches Loading; a re-render keeps
                        // the previous bitmap until the new one is ready.
                        ArtPreviewState.Loading -> CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("art-preview-loading"),
                            strokeWidth = 2.dp,
                            color = colors.textTertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwatchRow(
    title: String,
    selected: ArtColor,
    onSelected: (ArtColor) -> Unit,
    tagPrefix: String,
    enabled: Boolean = true,
) {
    val colors = McTheme.colors
    val surfaceArgb = colors.surface.toArgb()
    Text(
        text = title,
        style = McTextStyles.caption,
        color = colors.textTertiary,
        modifier = Modifier
            .semantics(mergeDescendants = true) {
                heading()
            },
    )
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.selectableGroup(),
    ) {
        ArtColor.entries.forEach { color ->
            val isSelected = color == selected
            // A white swatch on the light surface (1.08:1) and a black one on
            // the dark surface need a border that is visible by itself; the
            // swatch fill (the art value) never changes.
            val needsContrastBorder = ArtContrast.ratio(color.argb, surfaceArgb) < SWATCH_BORDER_CONTRAST
            Surface(
                shape = CircleShape,
                color = Color(color.argb),
                border = when {
                    isSelected -> BorderStroke(2.dp, colors.textPrimary)
                    needsContrastBorder -> BorderStroke(1.5.dp, colors.textSecondary)
                    else -> BorderStroke(1.dp, colors.borderSubtle)
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelected(color) },
                    )
                    .semantics {
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
                            tint = checkTint(color),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Selecionada: ${swatchLabel(selected)}",
        style = McTextStyles.caption,
        color = colors.textTertiary,
        modifier = Modifier.testTag("$tagPrefix-selected"),
    )
}

private sealed interface ArtPreviewState {
    data object Loading : ArtPreviewState

    data object Empty : ArtPreviewState

    data object Error : ArtPreviewState

    data object PhotoEmpty : ArtPreviewState

    /** [font] is the family actually used by the renderer, not just the request. */
    data class Ready(val bitmap: Bitmap, val font: ArtFont) : ArtPreviewState
}

@Composable
private fun ArtPhotoCard(state: StoryStudioUiState, actions: ArtStudioActions, enabled: Boolean, onPick: () -> Unit) {
    val colors = McTheme.colors
    val photo = state.artRecipe.photo
    val busy = state.artPhotoBusy
    McCard(variant = McCardVariant.DEFAULT) {
        StudioCardHeader(
            overline = "FOTO DE FUNDO",
            title = "Foto de fundo",
            subtitle = "Uma foto por atividade, só no aparelho. Trocar substitui; remover volta ao transparente.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (busy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("art-photo-loading"),
                    strokeWidth = 2.dp,
                    color = colors.textTertiary,
                )
                Text(
                    text = "Processando foto…",
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
            }
        }
        if (photo == null) {
            Text(
                text = "Escolha uma foto para o fundo",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.testTag("art-photo-empty"),
            )
            Spacer(modifier = Modifier.height(12.dp))
            McSecondaryButton(
                text = "Escolher foto",
                onClick = onPick,
                enabled = enabled && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("art-photo-pick"),
                minHeight = 48.dp,
            )
        } else {
            PhotoThumbnail(
                photo = photo,
                format = state.artRecipe.format,
                onLoadPhoto = actions.onLoadPhoto,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                McSecondaryButton(
                    text = "Trocar",
                    onClick = onPick,
                    enabled = enabled && !busy,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("art-photo-change"),
                    minHeight = 48.dp,
                )
                McSecondaryButton(
                    text = "Remover",
                    onClick = { actions.onRequestRemove() },
                    enabled = enabled && !busy,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("art-photo-remove"),
                    minHeight = 48.dp,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            McSecondaryButton(
                text = "Ajustar enquadramento",
                onClick = { actions.onOpenCrop() },
                enabled = enabled && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("art-photo-crop"),
                minHeight = 48.dp,
            )
        }
        state.artPhotoQualityWarning?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = McTextStyles.caption,
                color = colors.warning,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("art-photo-quality"),
            )
        }
    }
}

@Composable
private fun PhotoThumbnail(
    photo: ArtPhotoRef,
    format: ArtFormat,
    onLoadPhoto: suspend (ArtPhotoRef, ArtFormat, Int) -> Bitmap?,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, photo, format) {
        value = withContext(Dispatchers.IO) {
            runCatching { onLoadPhoto(photo, format, 540) }.getOrNull()
        }
    }
    val colors = McTheme.colors
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = "Miniatura da foto de fundo",
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .testTag("art-photo-thumbnail"),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceInset)
                .testTag("art-photo-thumbnail-placeholder"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Carregando foto…",
                style = McTextStyles.caption,
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun ArtPositionCard(state: StoryStudioUiState, actions: ArtStudioActions, enabled: Boolean) {
    val layout = state.artRecipe.layout
    McCard(
        variant = McCardVariant.DEFAULT,
        modifier = Modifier.testTag("art-position-card"),
    ) {
        StudioCardHeader(
            overline = "POSIÇÃO",
            title = "Posição dos blocos",
            subtitle = "Presets movem tudo; o ajuste fino move o bloco selecionado em passos de 2%.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        SectionLabel("Modelo")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            ArtTemplate.entries.forEach { template ->
                val selected = layout.template == template
                StudioPill(
                    text = templateLabel(template),
                    selected = selected,
                    enabled = enabled,
                    onClick = { actions.onTemplate(template) },
                    modifier = Modifier.testTag("art-template-${template.name.lowercase(Locale.ROOT)}"),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        SectionLabel("Bloco")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            ArtBlock.entries.forEach { block ->
                val selected = state.artSelectedBlock == block
                StudioPill(
                    text = blockLabel(block),
                    selected = selected,
                    enabled = enabled,
                    onClick = { actions.onBlock(block) },
                    modifier = Modifier.testTag("art-block-${block.name.lowercase(Locale.ROOT)}"),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        SectionLabel("Ajuste fino")
        val block = state.artSelectedBlock
        val blockName = blockLabel(block).lowercase(Locale.ROOT)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NudgeButton(
                description = "Mover $blockName para a esquerda",
                tag = "art-nudge-left",
                enabled = enabled,
                onClick = { actions.onNudge(block, -ActivityDetailViewModel.NUDGE_STEP, 0f) },
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
            )
            NudgeButton(
                description = "Mover $blockName para a direita",
                tag = "art-nudge-right",
                enabled = enabled,
                onClick = { actions.onNudge(block, ActivityDetailViewModel.NUDGE_STEP, 0f) },
                icon = Icons.AutoMirrored.Outlined.ArrowForward,
            )
            NudgeButton(
                description = "Mover $blockName para cima",
                tag = "art-nudge-up",
                enabled = enabled,
                onClick = { actions.onNudge(block, 0f, -ActivityDetailViewModel.NUDGE_STEP) },
                icon = Icons.Outlined.ArrowUpward,
            )
            NudgeButton(
                description = "Mover $blockName para baixo",
                tag = "art-nudge-down",
                enabled = enabled,
                onClick = { actions.onNudge(block, 0f, ActivityDetailViewModel.NUDGE_STEP) },
                icon = Icons.Outlined.ArrowDownward,
            )
        }
    }
}

@Composable
private fun NudgeButton(
    description: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val colors = McTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceInset,
        border = BorderStroke(1.dp, colors.borderSubtle),
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .testTag(tag)
            .semantics { contentDescription = description },
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxSize()) {
            Icon(imageVector = icon, contentDescription = null, tint = colors.textPrimary)
        }
    }
}

@Composable
private fun ArtTextScaleCard(state: StoryStudioUiState, actions: ArtStudioActions, enabled: Boolean) {
    val colors = McTheme.colors
    val scale = state.artRecipe.layout.textScalePercent
    McCard(
        variant = McCardVariant.DEFAULT,
        modifier = Modifier.testTag("art-text-scale-card"),
    ) {
        StudioCardHeader(
            overline = "TAMANHO DO TEXTO",
            title = "Tamanho do texto",
            subtitle = "80% a 130%, em passos de 5%. A marca e a rota não mudam.",
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            McSecondaryButton(
                text = "−",
                onClick = { actions.onTextScale((scale - 5).coerceAtLeast(ArtLayout.MIN_TEXT_SCALE_PERCENT)) },
                enabled = enabled && scale > ArtLayout.MIN_TEXT_SCALE_PERCENT,
                modifier = Modifier.testTag("art-text-scale-minus"),
                minHeight = 48.dp,
            )
            Text(
                text = "Tamanho do texto: $scale%",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Tamanho do texto: $scale por cento" }
                    .testTag("art-text-scale-value"),
            )
            McSecondaryButton(
                text = "+",
                onClick = { actions.onTextScale((scale + 5).coerceAtMost(ArtLayout.MAX_TEXT_SCALE_PERCENT)) },
                enabled = enabled && scale < ArtLayout.MAX_TEXT_SCALE_PERCENT,
                modifier = Modifier.testTag("art-text-scale-plus"),
                minHeight = 48.dp,
            )
        }
    }
}

@Composable
private fun ArtCropEditor(state: StoryStudioUiState, actions: ArtStudioActions, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    val draft = state.artCropDraft
    val photo = state.artRecipe.photo
    val busy = state.artPhotoBusy
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(4.dp),
    ) {
        Text(
            text = "AJUSTAR FOTO",
            style = McTextStyles.overline,
            color = colors.textTertiary,
            modifier = Modifier.testTag("art-crop-title"),
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (photo != null) {
            val preview by produceState<Bitmap?>(initialValue = null, photo, draft, state.artRecipe.format) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        val withDraft = photo.copy(crop = draft)
                        actions.onLoadPhoto(withDraft, state.artRecipe.format, 540)
                    }.getOrNull()
                }
            }
            val image = preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .testTag("art-crop-preview"),
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = "Prévia do enquadramento",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = colors.textTertiary,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        state.artPhotoQualityWarning?.let { message ->
            Text(
                text = message,
                style = McTextStyles.caption,
                color = colors.warning,
                modifier = Modifier.testTag("art-crop-quality"),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Text(
            text = "Foco",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NudgeButton(
                description = "Mover foco para a esquerda",
                tag = "art-crop-left",
                enabled = !busy,
                onClick = {
                    actions.onCropChange(
                        (draft.focusX - ActivityDetailViewModel.CROP_FOCUS_STEP).coerceIn(0f, 1f),
                        draft.focusY,
                        draft.zoom,
                    )
                },
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
            )
            NudgeButton(
                description = "Mover foco para a direita",
                tag = "art-crop-right",
                enabled = !busy,
                onClick = {
                    actions.onCropChange(
                        (draft.focusX + ActivityDetailViewModel.CROP_FOCUS_STEP).coerceIn(0f, 1f),
                        draft.focusY,
                        draft.zoom,
                    )
                },
                icon = Icons.AutoMirrored.Outlined.ArrowForward,
            )
            NudgeButton(
                description = "Mover foco para cima",
                tag = "art-crop-up",
                enabled = !busy,
                onClick = {
                    actions.onCropChange(
                        draft.focusX,
                        (draft.focusY - ActivityDetailViewModel.CROP_FOCUS_STEP).coerceIn(0f, 1f),
                        draft.zoom,
                    )
                },
                icon = Icons.Outlined.ArrowUpward,
            )
            NudgeButton(
                description = "Mover foco para baixo",
                tag = "art-crop-down",
                enabled = !busy,
                onClick = {
                    actions.onCropChange(
                        draft.focusX,
                        (draft.focusY + ActivityDetailViewModel.CROP_FOCUS_STEP).coerceIn(0f, 1f),
                        draft.zoom,
                    )
                },
                icon = Icons.Outlined.ArrowDownward,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Zoom", style = McTextStyles.caption, color = colors.textTertiary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            McSecondaryButton(
                text = "−",
                onClick = {
                    actions.onCropChange(
                        draft.focusX,
                        draft.focusY,
                        (draft.zoom - ActivityDetailViewModel.CROP_ZOOM_STEP).coerceAtLeast(
                            ArtPhotoCrop.MIN_ZOOM,
                        ),
                    )
                },
                enabled = !busy && draft.zoom > ArtPhotoCrop.MIN_ZOOM,
                modifier = Modifier.testTag("art-crop-zoom-out"),
                minHeight = 48.dp,
            )
            Text(
                text = "Zoom ${((draft.zoom * 10).toInt() / 10f)}x",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("art-crop-zoom-value"),
            )
            McSecondaryButton(
                text = "+",
                onClick = {
                    actions.onCropChange(
                        draft.focusX,
                        draft.focusY,
                        (draft.zoom + ActivityDetailViewModel.CROP_ZOOM_STEP).coerceAtMost(ArtPhotoCrop.MAX_ZOOM),
                    )
                },
                enabled = !busy,
                modifier = Modifier.testTag("art-crop-zoom-in"),
                minHeight = 48.dp,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            McSecondaryButton(
                text = "Centralizar",
                onClick = { actions.onCenterCrop() },
                enabled = !busy,
                modifier = Modifier
                    .weight(1f)
                    .testTag("art-crop-center"),
                minHeight = 48.dp,
            )
            McSecondaryButton(
                text = "Enquadrar",
                onClick = { actions.onFrameCrop() },
                enabled = !busy,
                modifier = Modifier
                    .weight(1f)
                    .testTag("art-crop-frame"),
                minHeight = 48.dp,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            McSecondaryButton(
                text = "Cancelar",
                onClick = { actions.onCancelCrop() },
                enabled = !busy,
                modifier = Modifier
                    .weight(1f)
                    .testTag("art-crop-cancel"),
                minHeight = 48.dp,
            )
            McPrimaryButton(
                text = if (busy) "Aplicando…" else "Aplicar",
                onClick = { actions.onApplyCrop() },
                enabled = !busy,
                modifier = Modifier
                    .weight(1f)
                    .testTag("art-crop-apply"),
                minHeight = 48.dp,
            )
        }
        state.artPhotoError?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = McTextStyles.caption,
                color = colors.danger,
                modifier = Modifier.testTag("art-crop-error"),
            )
        }
    }
}

internal fun templateLabel(template: ArtTemplate): String = when (template) {
    ArtTemplate.TOP -> "Topo"
    ArtTemplate.CENTER -> "Centro"
    ArtTemplate.BOTTOM -> "Base"
}

internal fun blockLabel(block: ArtBlock): String = when (block) {
    ArtBlock.DISTANCE -> "Distância"
    ArtBlock.PACE_TIME -> "Ritmo/tempo"
    ArtBlock.EXTRAS -> "Extras"
    ArtBlock.ROUTE -> "Rota"
}

private fun ceilOf(value: Float): Int = Math.ceil(value.toDouble()).toInt().coerceAtLeast(1)

internal fun formatLabel(format: ArtFormat): String = when (format) {
    ArtFormat.PORTRAIT_4_5 -> "4:5"
    ArtFormat.SQUARE -> "1:1"
    ArtFormat.PORTRAIT_9_16 -> "9:16"
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

/** Check tint that wins the WCAG ratio against the swatch fill (≥3:1 for the 8 colors). */
internal fun checkTint(color: ArtColor): Color {
    val white = 0xFFFFFFFF.toInt()
    val black = 0xFF000000.toInt()
    return if (ArtContrast.ratio(white, color.argb) >= ArtContrast.ratio(black, color.argb)) {
        Color.White
    } else {
        Color.Black
    }
}

internal fun ArtRecipe.summaryLine(): String = "${formatLabel(
    format,
)} · ${fontLabel(font)} · texto ${swatchLabel(textColor)} · traçado ${swatchLabel(routeColor)}."
