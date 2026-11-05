package com.meucoach.app.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meucoach.app.R
import com.meucoach.app.firstrun.FirstActionType
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McPill
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.component.TutorialActionCard
import com.meucoach.core.designsystem.component.TutorialProgress
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import kotlinx.coroutines.launch

private const val TUTORIAL_STEPS = 3

/** Runs the intro walkthrough and reports completion (or dismissal) to the caller. */
@Composable
fun TutorialScreen(
    onDone: () -> Unit,
    onCheckIn: () -> Unit,
    onImport: () -> Unit,
    onHealth: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TutorialViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TutorialContent(
        state = state,
        onFinish = {
            viewModel.markSeen()
            onDone()
        },
        onDismiss = {
            viewModel.markSkipped()
            onDone()
        },
        onCheckIn = {
            viewModel.markFirstAction(FirstActionType.CHECK_IN)
            viewModel.markSeen()
            onDone()
            onCheckIn()
        },
        onImport = {
            viewModel.markFirstAction(FirstActionType.IMPORT)
            viewModel.markSeen()
            onDone()
            onImport()
        },
        onHealth = {
            viewModel.markFirstAction(FirstActionType.HEALTH)
            viewModel.markSeen()
            onDone()
            onHealth()
        },
        onRetry = viewModel::retry,
        onStepChanged = viewModel::onStepChanged,
        modifier = modifier,
    )
}

@Composable
internal fun TutorialContent(
    state: TutorialUiState,
    onFinish: () -> Unit,
    onDismiss: () -> Unit,
    onCheckIn: () -> Unit,
    onImport: () -> Unit,
    onHealth: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    onStepChanged: (Int) -> Unit = {},
) {
    val colors = McTheme.colors
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, TUTORIAL_STEPS - 1),
        pageCount = { TUTORIAL_STEPS },
    )
    val currentPage = pagerState.currentPage
    val goNext: () -> Unit = {
        scope.launch {
            pagerState.animateScrollToPage((currentPage + 1).coerceAtMost(TUTORIAL_STEPS - 1))
        }
    }
    val goBack: () -> Unit = {
        scope.launch {
            pagerState.animateScrollToPage((currentPage - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(currentPage) {
        onStepChanged(currentPage + 1)
        if (currentPage > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    BackHandler {
        if (currentPage > 0) goBack() else onDismiss()
    }

    val cta = when (currentPage) {
        0 -> TutorialCta(stringResource(R.string.tutorial_scene_1_cta), onFinish)

        1 -> TutorialCta(stringResource(R.string.tutorial_scene_2_cta), goNext)

        else -> firstActionCta(
            state = state,
            onCheckIn = onCheckIn,
            onImport = onImport,
            onHealth = onHealth,
            onFinish = onFinish,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            TutorialTopBar(
                currentPage = currentPage,
                totalPages = TUTORIAL_STEPS,
                onBack = goBack,
                onSkip = onDismiss,
            )
            TutorialProgress(
                currentStep = currentPage,
                totalSteps = TUTORIAL_STEPS,
                contentDescription = stringResource(
                    R.string.tutorial_step_a11y,
                    currentPage + 1,
                    TUTORIAL_STEPS,
                ),
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .testTag("tutorial-progress"),
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("tutorial-pager"),
            ) { page ->
                when (page) {
                    0 -> PlanScene(
                        state = state,
                        onNext = goNext,
                        onRetry = onRetry,
                    )

                    1 -> CoachScene(state = state, onBack = goBack)

                    else -> FirstActionScene(
                        state = state,
                        onCheckIn = onCheckIn,
                        onImport = onImport,
                        onHealth = onHealth,
                        onFinish = onFinish,
                        onDismiss = onDismiss,
                    )
                }
            }

            McPrimaryButton(
                text = cta.label,
                onClick = cta.onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)
                    .testTag("tutorial-next"),
            )
        }
    }
}

@Composable
private fun TutorialTopBar(currentPage: Int, totalPages: Int, onBack: () -> Unit, onSkip: () -> Unit) {
    val colors = McTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (currentPage > 0) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("tutorial-back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.tutorial_back),
                    tint = colors.textSecondary,
                )
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }

        Text(
            text = stringResource(R.string.tutorial_step, currentPage + 1, totalPages),
            style = McTextStyles.overline,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .testTag("tutorial-step-label"),
        )

        McTextButton(
            text = stringResource(R.string.tutorial_skip),
            onClick = onSkip,
            trailingArrow = false,
            contentColor = colors.textSecondary,
            modifier = Modifier.testTag("tutorial-skip"),
        )
    }
}

@Composable
private fun PlanScene(state: TutorialUiState, onNext: () -> Unit, onRetry: () -> Unit) {
    val colors = McTheme.colors
    val name = state.name?.takeIf { it.isNotBlank() }
    val title = if (name == null) {
        stringResource(R.string.tutorial_scene_1_title)
    } else {
        stringResource(R.string.tutorial_scene_1_title_name, name)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .semantics { paneTitle = title }
            .testTag("tutorial-page-1"),
    ) {
        TutorialVisualPanel(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.9f)
                .padding(top = 16.dp),
        ) {
            PlanVisual(state = state, onRetry = onRetry)
        }

        LazyColumn(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxWidth()
                .testTag("tutorial-page-list-1"),
            contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.tutorial_scene_1_overline),
                        style = McTextStyles.overline,
                        color = colors.textTertiary,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.tutorial_scene_1_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.size(10.dp))
                McTextButton(
                    text = stringResource(R.string.tutorial_scene_1_secondary),
                    onClick = onNext,
                    trailingArrow = false,
                    contentColor = colors.textSecondary,
                    modifier = Modifier.testTag("tutorial-secondary-plan"),
                )
            }
            item {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = colors.textTertiary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = stringResource(R.string.tutorial_scene_1_trust),
                        style = McTextStyles.caption,
                        color = colors.textTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanVisual(state: TutorialUiState, onRetry: () -> Unit) {
    val colors = McTheme.colors
    val plan = state.planPreview
    McCard(variant = McCardVariant.ELEVATED, modifier = Modifier.fillMaxWidth()) {
        when {
            state.isLoading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = colors.accent,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.tutorial_scene_1_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }

            state.error != null -> {
                Text(
                    text = stringResource(R.string.tutorial_scene_1_error_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = stringResource(R.string.tutorial_scene_1_error_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                McTextButton(
                    text = "Tentar de novo",
                    onClick = onRetry,
                    trailingArrow = false,
                    contentColor = colors.accentText,
                    modifier = Modifier.testTag("tutorial-retry"),
                )
            }

            plan == null || plan.sessions.isEmpty() -> {
                Text(
                    text = stringResource(R.string.tutorial_scene_1_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = stringResource(R.string.tutorial_scene_1_empty_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }

            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.tutorial_scene_1_week),
                        style = McTextStyles.overline,
                        color = colors.textTertiary,
                        modifier = Modifier.weight(1f),
                    )
                    McPill(
                        text = stringResource(
                            R.string.tutorial_scene_1_week_meta,
                            plan.sessions.size,
                            plan.weeklyMinutes,
                        ),
                        containerColor = colors.accentSoft,
                        contentColor = colors.accentText,
                    )
                }
                val goal = state.goalLabel
                val availability = state.availabilityLabel
                if (!goal.isNullOrBlank() || !availability.isNullOrBlank()) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = listOfNotNull(goal, availability).joinToString(" · "),
                        style = McTextStyles.caption,
                        color = colors.textTertiary,
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                plan.sessions.take(3).forEachIndexed { index, session ->
                    if (index > 0) McDivider(modifier = Modifier.padding(vertical = 5.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = session.dayLabel.uppercase(),
                            style = McTextStyles.overline,
                            color = colors.textTertiary,
                            modifier = Modifier.widthIn(min = 30.dp),
                        )
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = session.durationMin?.let { "$it min" } ?: "—",
                            style = McTextStyles.caption,
                            color = colors.textSecondary,
                        )
                    }
                }
                state.workoutPreview?.let { workout ->
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "PRÓXIMO TREINO",
                        style = McTextStyles.overline,
                        color = colors.textTertiary,
                    )
                    Text(
                        text = workout.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoachScene(state: TutorialUiState, onBack: () -> Unit) {
    val colors = McTheme.colors
    val title = stringResource(R.string.tutorial_scene_2_title)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .semantics { paneTitle = title }
            .testTag("tutorial-page-2"),
    ) {
        TutorialVisualPanel(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.9f)
                .padding(top = 16.dp),
        ) {
            LoopVisual(state = state)
        }

        LazyColumn(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxWidth()
                .testTag("tutorial-page-list-2"),
            contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.tutorial_scene_2_overline),
                        style = McTextStyles.overline,
                        color = colors.textTertiary,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = colors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.tutorial_scene_2_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.size(10.dp))
                McTextButton(
                    text = stringResource(R.string.tutorial_scene_2_secondary),
                    onClick = onBack,
                    trailingArrow = false,
                    contentColor = colors.textSecondary,
                    modifier = Modifier.testTag("tutorial-secondary-coach"),
                )
            }
        }
    }
}

@Composable
private fun LoopVisual(state: TutorialUiState) {
    val colors = McTheme.colors
    McCard(variant = McCardVariant.ELEVATED, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "O CICLO",
            style = McTextStyles.overline,
            color = colors.textTertiary,
        )
        Spacer(modifier = Modifier.size(8.dp))
        LoopStep(
            icon = Icons.AutoMirrored.Outlined.DirectionsRun,
            title = stringResource(R.string.tutorial_scene_2_step_1_title),
            body = stringResource(R.string.tutorial_scene_2_step_1_body),
        )
        LoopDivider()
        LoopStep(
            icon = Icons.Outlined.Favorite,
            title = stringResource(R.string.tutorial_scene_2_step_2_title),
            body = stringResource(R.string.tutorial_scene_2_step_2_body),
            pill = state.readinessPreview?.let {
                stringResource(R.string.tutorial_scene_2_readiness, it.label)
            },
        )
        LoopDivider()
        LoopStep(
            icon = Icons.Outlined.CheckCircle,
            title = stringResource(R.string.tutorial_scene_2_step_3_title),
            body = state.workoutPreview?.title ?: stringResource(R.string.tutorial_scene_2_step_3_body),
        )
    }
}

@Composable
private fun LoopStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    pill: String? = null,
) {
    val colors = McTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(color = colors.accentSoft, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accentText,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Text(
                text = body,
                style = McTextStyles.caption,
                color = colors.textSecondary,
            )
            if (pill != null) {
                Spacer(modifier = Modifier.size(4.dp))
                McPill(
                    text = pill,
                    containerColor = colors.accentSoft,
                    contentColor = colors.accentText,
                )
            }
        }
    }
}

@Composable
private fun LoopDivider() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.size(34.dp))
        Spacer(modifier = Modifier.size(10.dp))
        Box(
            modifier = Modifier
                .size(width = 1.dp, height = 10.dp)
                .background(McTheme.colors.borderSubtle),
        )
    }
}

@Composable
private fun FirstActionScene(
    state: TutorialUiState,
    onCheckIn: () -> Unit,
    onImport: () -> Unit,
    onHealth: () -> Unit,
    onFinish: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = McTheme.colors
    val title = stringResource(R.string.tutorial_scene_3_title)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .semantics { paneTitle = title }
            .testTag("tutorial-page-3"),
        contentPadding = PaddingValues(top = 20.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.tutorial_scene_3_overline),
                    style = McTextStyles.overline,
                    color = colors.textTertiary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.tutorial_scene_3_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                )
            }
        }
        item {
            TutorialActionCard(
                icon = Icons.Outlined.CheckCircle,
                title = stringResource(R.string.tutorial_scene_3_checkin_title),
                meta = if (state.hasCheckedInToday) {
                    stringResource(R.string.tutorial_scene_3_done_meta)
                } else {
                    stringResource(R.string.tutorial_scene_3_checkin_meta)
                },
                description = if (state.hasCheckedInToday) {
                    stringResource(R.string.tutorial_scene_3_checkin_done_body)
                } else {
                    stringResource(R.string.tutorial_scene_3_checkin_body)
                },
                onClick = if (state.hasCheckedInToday) onFinish else onCheckIn,
                testTag = "tutorial-action-checkin",
            )
        }
        item {
            TutorialActionCard(
                icon = Icons.AutoMirrored.Outlined.DirectionsRun,
                title = stringResource(R.string.tutorial_scene_3_import_title),
                meta = if (state.hasActivities) {
                    stringResource(R.string.tutorial_scene_3_done_meta)
                } else {
                    stringResource(R.string.tutorial_scene_3_import_meta)
                },
                description = if (state.hasActivities) {
                    stringResource(R.string.tutorial_scene_3_import_done_body)
                } else {
                    stringResource(R.string.tutorial_scene_3_import_body)
                },
                onClick = if (state.hasActivities) onFinish else onImport,
                testTag = "tutorial-action-import",
            )
        }
        item {
            TutorialActionCard(
                icon = Icons.Outlined.Favorite,
                title = stringResource(R.string.tutorial_scene_3_health_title),
                meta = if (state.healthConnected) {
                    stringResource(R.string.tutorial_scene_3_health_done_meta)
                } else {
                    stringResource(R.string.tutorial_scene_3_health_meta)
                },
                description = if (state.healthConnected) {
                    stringResource(R.string.tutorial_scene_3_health_done_body)
                } else {
                    stringResource(R.string.tutorial_scene_3_health_body)
                },
                onClick = if (state.healthConnected) onFinish else onHealth,
                testTag = "tutorial-action-health",
            )
        }
        item {
            McTextButton(
                text = stringResource(R.string.tutorial_scene_3_secondary),
                onClick = onDismiss,
                trailingArrow = false,
                contentColor = colors.textSecondary,
                modifier = Modifier.testTag("tutorial-secondary-actions"),
            )
        }
        item {
            Text(
                text = stringResource(R.string.tutorial_scene_3_footer),
                style = McTextStyles.caption,
                color = colors.textTertiary,
            )
        }
    }
}

private data class TutorialCta(val label: String, val onClick: () -> Unit)

@Composable
private fun firstActionCta(
    state: TutorialUiState,
    onCheckIn: () -> Unit,
    onImport: () -> Unit,
    onHealth: () -> Unit,
    onFinish: () -> Unit,
): TutorialCta = when {
    !state.hasCheckedInToday -> TutorialCta(
        label = stringResource(R.string.tutorial_scene_3_cta_checkin),
        onClick = onCheckIn,
    )

    !state.hasActivities -> TutorialCta(
        label = stringResource(R.string.tutorial_scene_3_cta_import),
        onClick = onImport,
    )

    !state.healthConnected -> TutorialCta(
        label = stringResource(R.string.tutorial_scene_3_cta_health),
        onClick = onHealth,
    )

    else -> TutorialCta(
        label = stringResource(R.string.tutorial_finish),
        onClick = onFinish,
    )
}

@Composable
private fun TutorialVisualPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = McTheme.colors
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.surfaceElevated, colors.surfaceInset)))
            .border(1.dp, colors.borderSubtle, shape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        listOf(colors.accent.copy(alpha = 0.10f), Color.Transparent),
                    ),
                ),
        )
        Canvas(modifier = Modifier.matchParentSize()) {
            val route = Path().apply {
                moveTo(size.width * 0.08f, size.height * 0.86f)
                cubicTo(
                    size.width * 0.22f,
                    size.height * 0.62f,
                    size.width * 0.34f,
                    size.height * 0.92f,
                    size.width * 0.48f,
                    size.height * 0.66f,
                )
                cubicTo(
                    size.width * 0.60f,
                    size.height * 0.42f,
                    size.width * 0.70f,
                    size.height * 0.56f,
                    size.width * 0.84f,
                    size.height * 0.24f,
                )
            }
            drawPath(
                path = route,
                color = colors.borderStrong.copy(alpha = 0.65f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(
                color = colors.accent,
                radius = 3.5.dp.toPx(),
                center = Offset(size.width * 0.08f, size.height * 0.86f),
            )
        }
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}
