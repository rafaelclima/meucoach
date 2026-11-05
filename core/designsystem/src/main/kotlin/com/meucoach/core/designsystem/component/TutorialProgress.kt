package com.meucoach.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.theme.McTheme

/**
 * Segmented progress for short guided flows.
 *
 * The whole row is one semantics node: TalkBack reads "Passo 1 de 4" and the progress
 * range, instead of announcing four decorative boxes.
 */
@Composable
fun TutorialProgress(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    require(totalSteps > 0) { "totalSteps must be positive" }
    val step = currentStep.coerceIn(0, totalSteps - 1)
    val colors = McTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = step + 1f,
                    range = 1f..totalSteps.toFloat(),
                    steps = totalSteps - 1,
                )
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        color = if (index <= step) colors.accent else colors.surfaceInset,
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}
