package com.meucoach.app.ui.firstrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.meucoach.app.firstrun.FirstRunTipStore
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McTextButton
import com.meucoach.core.designsystem.theme.McTheme

/**
 * One-time contextual card. It hides itself as soon as the user acts or dismisses it, and the
 * preference survives restarts, so the same tip is never shown twice.
 */
@Composable
fun FirstRunTipCard(
    tipId: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val store = remember(context) { FirstRunTipStore(context) }
    var visible by remember(tipId) { mutableStateOf(!store.isShown(tipId)) }
    if (!visible) return

    val colors = McTheme.colors
    val dismiss: () -> Unit = {
        store.markShown(tipId)
        visible = false
    }

    McCard(
        variant = McCardVariant.ACCENT,
        modifier = modifier
            .fillMaxWidth()
            .testTag("first-run-tip-$tipId"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = colors.accentText,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.size(3.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (actionLabel != null && onAction != null) {
                        McTextButton(
                            text = actionLabel,
                            onClick = {
                                dismiss()
                                onAction()
                            },
                            trailingArrow = false,
                            contentColor = colors.accentText,
                            modifier = Modifier.testTag("first-run-tip-action-$tipId"),
                        )
                    }
                    McTextButton(
                        text = "Agora não",
                        onClick = dismiss,
                        trailingArrow = false,
                        contentColor = colors.textSecondary,
                        modifier = Modifier.testTag("first-run-tip-dismiss-$tipId"),
                    )
                }
            }
        }
    }
}
