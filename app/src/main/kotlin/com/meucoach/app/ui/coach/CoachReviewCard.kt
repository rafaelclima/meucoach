package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meucoach.app.ui.review.WeeklyReviewFormat
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewStatus

/**
 * The closed week on the Coach tab (PRD 3.2). The deterministic review is
 * always visible; the AI only rewrites it when the response passes validation,
 * so a timeout or a rejected claim never hides the engine's facts.
 */
@Composable
internal fun CoachReviewCard(review: WeeklyReview, state: CoachReviewState, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    McCard(modifier = modifier.testTag("coach-review-card")) {
        Text(
            text = "Sobre a semana passada",
            style = McTextStyles.overline,
            color = colors.textTertiary,
        )
        Text(
            text = WeeklyReviewFormat.dateRange(review),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
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
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = state.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                state.bullets.forEach { bullet ->
                    Text(
                        text = "• $bullet",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            CoachReviewState.Unavailable,
            is CoachReviewState.Fallback,
            -> Unit
        }

        if (review.status == WeeklyReviewStatus.EMPTY_NO_DATA) {
            McDivider(modifier = Modifier.padding(vertical = 10.dp))
            Text(
                text = "Não há plano, corridas ou check-ins registrados nessa semana.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            return@McCard
        }

        McDivider(modifier = Modifier.padding(vertical = 10.dp))
        Text(
            text = "O que o motor registrou",
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
        )

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
            val planned = WeeklyReviewFormat.volume(volume.plannedMin)
            val executed = WeeklyReviewFormat.volume(volume.executedMin)
            Text(
                text = "Volume: $planned planejados · $executed feitos " +
                    "(${WeeklyReviewFormat.volumePercent(volume)})",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.testTag("coach-review-volume"),
            )
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

        if (state !is CoachReviewState.Ready && review.observations.isNotEmpty()) {
            McDivider(modifier = Modifier.padding(vertical = 10.dp))
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
}
