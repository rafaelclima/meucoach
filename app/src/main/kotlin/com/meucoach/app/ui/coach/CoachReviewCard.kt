package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meucoach.app.ui.review.WeeklyReviewFormat
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewStatus

/**
 * The closed week on the Coach tab (PRD 3.2 / PRD_COACH_CHAT F3-bolhas).
 *
 * The AI text becomes short chat bubbles; adherence, volume, readiness, sleep
 * and observations move behind one "Ver números" toggle. The deterministic
 * facts never disappear: they are the annex that keeps the coach honest.
 */
@Composable
internal fun CoachReviewCard(review: WeeklyReview, state: CoachReviewState, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier.testTag("coach-review-card"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = WeeklyReviewFormat.dateRange(review),
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )

        when (state) {
            CoachReviewState.Loading -> {
                Text(
                    text = "Revisando a semana…",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.testTag("coach-review-loading"),
                )
            }

            is CoachReviewState.Ready -> {
                CoachBubbleSequence(
                    texts = CoachBubbles.split(state.title) + CoachBubbles.split(state.body),
                    testTag = "coach-review-bubble",
                )
                state.bullets.forEach { bullet ->
                    CoachBubble(
                        text = "• $bullet",
                        testTag = "coach-review-bullet",
                        showAvatar = false,
                    )
                }
            }

            CoachReviewState.Unavailable,
            is CoachReviewState.Fallback,
            -> Unit
        }

        if (review.status == WeeklyReviewStatus.EMPTY_NO_DATA) {
            Text(
                text = "Não há plano, corridas ou check-ins registrados nessa semana.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@Column
        }

        if (review.hasNumbers()) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.testTag("coach-review-numbers-toggle"),
            ) {
                Text(
                    text = if (expanded) "Ocultar números" else "Ver números",
                    color = colors.accentText,
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.testTag("coach-review-numbers"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ReviewNumbers(review)
                }
            }
        }
    }
}

@Composable
private fun ReviewNumbers(review: WeeklyReview) {
    val colors = McTheme.colors
    review.adherence?.takeIf { it.planned > 0 }?.let { adherence ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("coach-review-adherence"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = WeeklyReviewFormat.adherenceHeadline(adherence),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Text(
                    text = WeeklyReviewFormat.adherenceDetail(adherence),
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
            }
            WeeklyReviewFormat.adherencePercent(adherence)?.let { percent ->
                Text(
                    text = percent,
                    style = McTextStyles.metricSmall,
                    color = colors.textPrimary,
                )
            }
        }
    }

    review.volume?.let { volume ->
        val planned = volume.plannedMin
        val executed = volume.executedMin
        if (planned != null && executed != null) {
            Text(
                text = "Volume: $planned min planejados · $executed min feitos " +
                    "(${WeeklyReviewFormat.volumePercent(volume)})",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.testTag("coach-review-volume"),
            )
        }
        WeeklyReviewFormat.volumeExtra(volume.extraMin)?.let { extra ->
            Text(
                text = extra,
                style = McTextStyles.caption,
                color = colors.textTertiary,
            )
        }
    }

    review.quality?.takeIf { it.planned > 0 && it.hasDecision }?.let { quality ->
        Text(
            text = WeeklyReviewFormat.qualityLine(quality),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.testTag("coach-review-quality"),
        )
    }

    review.readiness?.let { readiness ->
        Text(
            text = "Prontidão: ${WeeklyReviewFormat.readinessValue(readiness)} " +
                "(${WeeklyReviewFormat.readinessHint(readiness)})",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
    }
    review.sleep?.let { sleep ->
        Text(
            text = "Sono: ${WeeklyReviewFormat.sleepValue(sleep)} " +
                "(${WeeklyReviewFormat.sleepHint(sleep)})",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
    }

    if (review.observations.isNotEmpty()) {
        McDivider(modifier = Modifier.padding(vertical = 6.dp))
        Text(
            text = "O que a semana mostrou",
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
        )
        review.observations.forEachIndexed { index, observation ->
            Text(
                text = "• ${WeeklyReviewFormat.observationText(review, observation)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.testTag("coach-review-observation-$index"),
            )
        }
    }
}

private fun WeeklyReview.hasNumbers(): Boolean = adherence?.planned?.let { it > 0 } == true ||
    volume != null ||
    quality?.takeIf { it.planned > 0 && it.hasDecision } != null ||
    readiness != null ||
    sleep != null ||
    observations.isNotEmpty()
