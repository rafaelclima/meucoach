package com.meucoach.app.ui.activitydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McConfidenceBadge
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.feedback.SessionComparison
import com.meucoach.core.domain.feedback.SessionComparisonKind

/**
 * Planned × executed card (FR-010).
 *
 * Shows the day's target next to what the run delivered. It is descriptive:
 * no quality judgement, no next prescription and no rule IDs on the common
 * surface. A run without a linked plan is labelled as a free run.
 */
@Composable
internal fun ActivityComparisonCard(comparison: SessionComparison, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    McCard(modifier = modifier.testTag("activity-comparison")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "PLANEJADO × EXECUTADO",
                style = McTextStyles.overline,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            if (comparison.kind == SessionComparisonKind.LINKED) {
                McConfidenceBadge(confidence = comparison.confidence)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        when (comparison.kind) {
            SessionComparisonKind.FREE_RUN -> FreeRunBody(comparison)
            SessionComparisonKind.LINKED -> LinkedBody(comparison)
        }
    }
}

@Composable
private fun FreeRunBody(comparison: SessionComparison) {
    val colors = McTheme.colors
    Text(
        text = "Treino livre",
        style = MaterialTheme.typography.titleMedium,
        color = colors.textPrimary,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Sem plano vinculado a esta corrida.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = freeRunSummary(comparison),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textPrimary,
    )
}

@Composable
private fun LinkedBody(comparison: SessionComparison) {
    val colors = McTheme.colors
    val plan = comparison.plan
    if (plan != null) {
        Text(
            text = plan.title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "O alvo do dia e o que a corrida entregou.",
        style = McTextStyles.caption,
        color = colors.textSecondary,
    )

    val rows = comparisonRows(comparison)
    if (rows.isNotEmpty()) {
        Spacer(modifier = Modifier.height(14.dp))
        rows.forEachIndexed { index, row ->
            if (index > 0) Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = colors.textSecondary, fontFeatureSettings = "tnum")) {
                            append(row.planned)
                        }
                        withStyle(SpanStyle(color = colors.textTertiary, fontFeatureSettings = "tnum")) {
                            append("  →  ")
                        }
                        withStyle(
                            SpanStyle(
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum",
                            ),
                        ) {
                            append(row.actual)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1.9f),
                )
            }
        }
    }

    val notes = comparisonNotes(comparison)
    if (notes.isNotEmpty()) {
        Spacer(modifier = Modifier.height(14.dp))
        notes.forEach { note ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(color = colors.textTertiary, shape = CircleShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = note,
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    comparisonSummary(comparison)?.let { summary ->
        Spacer(modifier = Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                modifier = Modifier
                    .size(6.dp)
                    .background(color = colors.success, shape = CircleShape),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = summary,
                style = McTextStyles.caption,
                color = colors.textSecondary,
            )
        }
    }
}
