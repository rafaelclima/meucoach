package com.meucoach.app.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.meucoach.app.R
import com.meucoach.core.designsystem.component.McPill
import com.meucoach.core.designsystem.component.McPrimaryButton
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.component.TutorialProgress
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import kotlinx.coroutines.launch

/** Runs the intro walkthrough and reports completion (or dismissal) to the caller. */
@Composable
fun TutorialScreen(onDone: () -> Unit, modifier: Modifier = Modifier, viewModel: TutorialViewModel = hiltViewModel()) {
    TutorialContent(
        onFinish = {
            viewModel.markSeen()
            onDone()
        },
        onDismiss = {
            viewModel.markSeen()
            onDone()
        },
        modifier = modifier,
    )
}

private data class TutorialPage(
    val icon: ImageVector,
    @StringRes val visualLabel: Int,
    @StringRes val overline: Int,
    @StringRes val title: Int,
    @StringRes val description: Int,
    val bullets: List<Int> = emptyList(),
)

private val tutorialPages = listOf(
    TutorialPage(
        icon = Icons.AutoMirrored.Outlined.DirectionsRun,
        visualLabel = R.string.tutorial_page_1_visual,
        overline = R.string.tutorial_page_1_overline,
        title = R.string.tutorial_page_1_title,
        description = R.string.tutorial_page_1_description,
    ),
    TutorialPage(
        icon = Icons.Outlined.SpaceDashboard,
        visualLabel = R.string.tutorial_page_2_visual,
        overline = R.string.tutorial_page_2_overline,
        title = R.string.tutorial_page_2_title,
        description = R.string.tutorial_page_2_description,
        bullets = listOf(
            R.string.tutorial_page_2_bullet_1,
            R.string.tutorial_page_2_bullet_2,
            R.string.tutorial_page_2_bullet_3,
            R.string.tutorial_page_2_bullet_4,
        ),
    ),
    TutorialPage(
        icon = Icons.Outlined.Favorite,
        visualLabel = R.string.tutorial_page_3_visual,
        overline = R.string.tutorial_page_3_overline,
        title = R.string.tutorial_page_3_title,
        description = R.string.tutorial_page_3_description,
        bullets = listOf(
            R.string.tutorial_page_3_bullet_1,
            R.string.tutorial_page_3_bullet_2,
            R.string.tutorial_page_3_bullet_3,
            R.string.tutorial_page_3_bullet_4,
        ),
    ),
    TutorialPage(
        icon = Icons.Outlined.Lock,
        visualLabel = R.string.tutorial_page_4_visual,
        overline = R.string.tutorial_page_4_overline,
        title = R.string.tutorial_page_4_title,
        description = R.string.tutorial_page_4_description,
        bullets = listOf(
            R.string.tutorial_page_4_bullet_1,
            R.string.tutorial_page_4_bullet_2,
            R.string.tutorial_page_4_bullet_3,
        ),
    ),
)

@Composable
internal fun TutorialContent(
    onFinish: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    val colors = McTheme.colors
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, tutorialPages.lastIndex),
        pageCount = { tutorialPages.size },
    )
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == tutorialPages.lastIndex

    BackHandler {
        if (currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
        } else {
            onDismiss()
        }
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
                totalPages = tutorialPages.size,
                onBack = {
                    scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
                },
                onSkip = onDismiss,
            )
            TutorialProgress(
                currentStep = currentPage,
                totalSteps = tutorialPages.size,
                contentDescription = stringResource(
                    R.string.tutorial_step_a11y,
                    currentPage + 1,
                    tutorialPages.size,
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
                TutorialPageView(page = tutorialPages[page])
            }

            McPrimaryButton(
                text = if (isLastPage) {
                    stringResource(R.string.tutorial_finish)
                } else {
                    stringResource(R.string.tutorial_continue)
                },
                onClick = {
                    if (isLastPage) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                    }
                },
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
private fun TutorialPageView(page: TutorialPage) {
    val colors = McTheme.colors
    val title = stringResource(page.title)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .semantics { paneTitle = title }
            .testTag("tutorial-page"),
    ) {
        TutorialVisual(
            page = page,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.9f)
                .padding(top = 16.dp),
        )

        LazyColumn(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxWidth()
                .testTag("tutorial-page-list"),
            contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.Top,
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(page.overline),
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
                        text = stringResource(page.description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                    )
                }
            }
            items(page.bullets) { bullet ->
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 7.dp)
                            .size(6.dp)
                            .background(color = colors.accent, shape = CircleShape),
                    )
                    Text(
                        text = stringResource(bullet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorialVisual(page: TutorialPage, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(colors.surfaceElevated, colors.surfaceInset),
                ),
            )
            .border(1.dp, colors.borderSubtle, shape)
            .testTag("tutorial-visual"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        listOf(colors.accent.copy(alpha = 0.12f), Color.Transparent),
                    ),
                ),
        )
        Canvas(modifier = Modifier.matchParentSize()) {
            val route = Path().apply {
                moveTo(size.width * 0.10f, size.height * 0.82f)
                cubicTo(
                    size.width * 0.24f,
                    size.height * 0.56f,
                    size.width * 0.34f,
                    size.height * 0.88f,
                    size.width * 0.46f,
                    size.height * 0.62f,
                )
                cubicTo(
                    size.width * 0.58f,
                    size.height * 0.36f,
                    size.width * 0.66f,
                    size.height * 0.54f,
                    size.width * 0.80f,
                    size.height * 0.26f,
                )
            }
            drawPath(
                path = route,
                color = colors.borderStrong.copy(alpha = 0.8f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(
                color = colors.accent,
                radius = 4.dp.toPx(),
                center = Offset(size.width * 0.10f, size.height * 0.82f),
            )
            drawCircle(
                color = colors.textTertiary,
                radius = 3.dp.toPx(),
                center = Offset(size.width * 0.80f, size.height * 0.26f),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(color = colors.accentSoft, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = colors.accentText,
                    modifier = Modifier.size(40.dp),
                )
            }
            McPill(
                text = stringResource(page.visualLabel),
                containerColor = colors.accentSoft,
                contentColor = colors.accentText,
            )
        }
    }
}
