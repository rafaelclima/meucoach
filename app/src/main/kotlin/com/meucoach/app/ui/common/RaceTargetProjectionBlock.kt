package com.meucoach.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.race.RaceTargetProjection

/**
 * Arithmetic reference of the declared race goal (ADR 0032).
 *
 * This is not a training target: it only derives pace, a ±3% reference band and
 * even splits from `targetTimeSec`. It is rendered in the race card only and is
 * never read by the prescription or weekly-plan engines.
 */
@Composable
internal fun RaceTargetProjectionBlock(projection: RaceTargetProjection, modifier: Modifier = Modifier) {
    val colors = McTheme.colors
    var splitsOpen by remember { mutableStateOf(false) }
    Column(modifier = modifier.testTag("race-projection")) {
        Text(
            text = "REFERÊNCIA DA META",
            style = McTextStyles.overline,
            color = colors.textTertiary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Ritmo médio necessário",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Text(
                text = racePaceLabel(projection.targetPaceSecPerKm),
                style = McTextStyles.metricSmall,
                color = colors.textPrimary,
                modifier = Modifier.testTag("race-projection-pace"),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Faixa de referência",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Text(
                text = racePaceRangeLabel(projection.paceRangeSecPerKm),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                color = colors.textPrimary,
                modifier = Modifier.testTag("race-projection-range"),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Referência da meta, não prescrição nem garantia.",
            style = McTextStyles.caption,
            color = colors.textTertiary,
        )
        McTextButton(
            text = "Ver passagens por km",
            onClick = { splitsOpen = true },
            trailingArrow = false,
            modifier = Modifier.testTag("race-splits-open"),
        )
    }

    if (splitsOpen) {
        RaceSplitsDialog(projection = projection, onDismiss = { splitsOpen = false })
    }
}

@Composable
private fun RaceSplitsDialog(projection: RaceTargetProjection, onDismiss: () -> Unit) {
    val colors = McTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("race-splits-dialog"),
        title = { Text(text = "Passagens por km") },
        text = {
            Column {
                Text(
                    text = "No ritmo médio da sua meta. É referência, não prescrição.",
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .testTag("race-splits-list"),
                ) {
                    itemsIndexed(projection.splits) { index, split ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .testTag("race-split-$index"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = raceProjectionSplitLabel(split),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                color = colors.textPrimary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            McTextButton(
                text = "Fechar",
                onClick = onDismiss,
                trailingArrow = false,
                modifier = Modifier.testTag("race-splits-close"),
            )
        },
    )
}
