package com.meucoach.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.meucoach.app.R
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme

@Composable
internal fun FirstRunChecklistCard(
    state: FirstRunChecklistState,
    onCheckIn: () -> Unit,
    onImport: () -> Unit,
    onHealth: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.shouldShow) return
    val colors = McTheme.colors

    McCard(
        variant = McCardVariant.DEFAULT,
        modifier = modifier.testTag("first-run-checklist"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.checklist_title),
                style = McTextStyles.overline,
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.checklist_progress, state.completed, state.total),
                style = McTextStyles.caption,
                color = colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        ChecklistProgress(completed = state.completed, total = state.total)
        Spacer(modifier = Modifier.height(6.dp))
        ChecklistRow(
            title = stringResource(R.string.checklist_checkin),
            done = state.hasCheckedIn,
            onClick = onCheckIn,
            testTag = "checklist-checkin",
        )
        ChecklistRow(
            title = stringResource(R.string.checklist_import),
            done = state.hasActivities,
            onClick = onImport,
            testTag = "checklist-import",
        )
        ChecklistRow(
            title = stringResource(R.string.checklist_health),
            done = state.healthConnected,
            onClick = onHealth,
            testTag = "checklist-health",
        )
        McTextButton(
            text = stringResource(R.string.checklist_dismiss),
            onClick = onDismiss,
            trailingArrow = false,
            contentColor = colors.textSecondary,
            modifier = Modifier.testTag("checklist-dismiss"),
        )
    }
}

@Composable
private fun ChecklistProgress(completed: Int, total: Int) {
    val colors = McTheme.colors
    val description = stringResource(R.string.checklist_a11y, completed, total)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = completed.toFloat(),
                    range = 0f..total.toFloat(),
                    steps = total,
                )
                contentDescription = description
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        color = if (index < completed) colors.accent else colors.surfaceInset,
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

@Composable
private fun ChecklistRow(title: String, done: Boolean, onClick: () -> Unit, testTag: String) {
    val colors = McTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        color = Color.Transparent,
        contentColor = colors.textPrimary,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (done) {
                    Icons.Outlined.CheckCircle
                } else {
                    Icons.Outlined.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (done) colors.success else colors.textTertiary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (done) colors.textSecondary else colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (done) {
                Text(
                    text = stringResource(R.string.checklist_done),
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
