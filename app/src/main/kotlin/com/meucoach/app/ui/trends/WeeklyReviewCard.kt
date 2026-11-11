package com.meucoach.app.ui.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McConfidenceBadge
import com.meucoach.core.designsystem.component.McDivider
import com.meucoach.core.designsystem.component.McStat
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.review.WeeklyAdherence
import com.meucoach.core.domain.review.WeeklyReadiness
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewObservationKind
import com.meucoach.core.domain.review.WeeklyReviewStatus
import com.meucoach.core.domain.review.WeeklySleep
import com.meucoach.core.domain.review.WeeklyVolume
import com.meucoach.core.domain.trends.WeeklyIntensity

/**
 * Short, deterministic reading of the last closed week (FR-011).
 *
 * The card only renders what the domain produced: no section exists without
 * input, and observations are facts with a maximum of three. There is no
 * streak, grade or call to action, and no rule/evidence ID on the surface.
 */
@Composable
internal fun WeeklyReviewCard(review: WeeklyReview, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    McCard(modifier = modifier.testTag("weekly-review-card")) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "REVISÃO DA SEMANA",
                    style = McTextStyles.overline,
                    color = colors.textTertiary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = WeeklyReviewFormat.dateRange(review),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
            }
            if (review.status != WeeklyReviewStatus.EMPTY_NO_DATA) {
                McConfidenceBadge(confidence = review.confidence)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        when (review.status) {
            WeeklyReviewStatus.EMPTY_NO_DATA -> {
                Text(
                    text = "Ainda não há dados desta semana.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = WeeklyReviewFormat.emptyDescription(),
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
            }

            else -> {
                if (review.status == WeeklyReviewStatus.EMPTY_NO_PLAN) {
                    Text(
                        text = "Sem plano registrado nesta semana.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                review.adherence?.takeIf { it.planned > 0 }?.let { AdherenceBlock(it) }
                review.volume?.let { VolumeBlock(it) }
                review.intensity?.let { IntensityBlock(review, it) }
                if (review.readiness != null || review.sleep != null) {
                    HealthBlock(readiness = review.readiness, sleep = review.sleep)
                }
                if (review.observations.isNotEmpty()) {
                    ObservationsBlock(review)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Resumo descritivo da semana fechada. Sem nota, streak ou cobrança.",
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun AdherenceBlock(adherence: WeeklyAdherence) {
    val colors = McTheme.colors
    Column(modifier = Modifier.testTag("weekly-review-adherence")) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = WeeklyReviewFormat.adherenceHeadline(adherence),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            WeeklyReviewFormat.adherencePercent(adherence)?.let { percent ->
                Text(
                    text = percent,
                    style = McTextStyles.metricSmall,
                    color = colors.textPrimary,
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = WeeklyReviewFormat.adherenceDetail(adherence),
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun VolumeBlock(volume: WeeklyVolume) {
    val colors = McTheme.colors
    McDivider(modifier = Modifier.padding(vertical = 10.dp))
    Text(
        text = "Volume",
        style = MaterialTheme.typography.titleSmall,
        color = colors.textPrimary,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        McStat(
            label = "Planejado",
            hint = "no plano",
            value = WeeklyReviewFormat.volume(volume.plannedMin),
            modifier = Modifier.weight(1f),
        )
        McStat(
            label = "Feito",
            hint = "nas corridas",
            value = WeeklyReviewFormat.volume(volume.executedMin),
            modifier = Modifier.weight(1f),
        )
        McStat(
            label = "Do plano",
            hint = "feito ÷ planejado",
            value = WeeklyReviewFormat.volumePercent(volume),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun IntensityBlock(review: WeeklyReview, intensity: WeeklyIntensity) {
    val colors = McTheme.colors
    McDivider(modifier = Modifier.padding(vertical = 10.dp))
    Text(
        text = "Tempo em zonas",
        style = MaterialTheme.typography.titleSmall,
        color = colors.textPrimary,
    )
    Spacer(modifier = Modifier.height(6.dp))
    IntensityBar(
        easySec = intensity.easySec,
        moderateSec = intensity.moderateSec,
        hardSec = intensity.hardSec,
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .semantics {
                contentDescription = "Distribuição por tempo da semana: " +
                    "${percent(intensity.easyPercent())}% fácil, " +
                    "${percent(intensity.moderatePercent())}% moderado e " +
                    "${percent(intensity.hardPercent())}% forte"
            },
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "${percent(intensity.easyPercent())}% fácil · " +
            "${percent(intensity.moderatePercent())}% moderado · " +
            "${percent(intensity.hardPercent())}% forte · ${review.intensitySessions} sessões",
        style = McTextStyles.caption,
        color = colors.textSecondary,
    )
}

@Composable
private fun HealthBlock(readiness: WeeklyReadiness?, sleep: WeeklySleep?) {
    McDivider(modifier = Modifier.padding(vertical = 10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        readiness?.let {
            McStat(
                label = "Prontidão",
                hint = WeeklyReviewFormat.readinessHint(it),
                value = WeeklyReviewFormat.readinessValue(it),
                modifier = Modifier.weight(1f),
            )
        }
        sleep?.let {
            McStat(
                label = "Sono",
                hint = WeeklyReviewFormat.sleepHint(it),
                value = WeeklyReviewFormat.sleepValue(it),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ObservationsBlock(review: WeeklyReview) {
    val colors = McTheme.colors
    McDivider(modifier = Modifier.padding(vertical = 10.dp))
    Text(
        text = "O que a semana mostrou",
        style = MaterialTheme.typography.titleSmall,
        color = colors.textPrimary,
    )
    Spacer(modifier = Modifier.height(6.dp))
    review.observations.forEachIndexed { index, observation ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .testTag("weekly-review-observation-$index"),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 7.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (observation.kind == WeeklyReviewObservationKind.ILLNESS_SIGNAL) {
                            colors.danger
                        } else {
                            colors.textTertiary
                        },
                    ),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = WeeklyReviewFormat.observationText(review, observation),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

private fun percent(value: Double): String = String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.0f", value)

private fun WeeklyIntensity.moderatePercent(): Double = moderateSec * 100.0 / totalSec

private fun WeeklyIntensity.hardPercent(): Double = hardSec * 100.0 / totalSec
