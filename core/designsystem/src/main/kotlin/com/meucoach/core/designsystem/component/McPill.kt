package com.meucoach.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.theme.McPillShape
import com.meucoach.core.designsystem.theme.McTheme

@Composable
fun McPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = McTheme.colors.surfaceInset,
    contentColor: Color = McTheme.colors.textSecondary,
    leadingIcon: ImageVector? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Surface(
            shape = McPillShape,
            color = containerColor,
            contentColor = contentColor,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Text(text = text)
            }
        }
    }
}

@Composable
fun McSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = McTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(text = actionLabel, color = colors.accentText)
            }
        }
    }
}

@Composable
fun McDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, color = McTheme.colors.borderSubtle)
}

@Composable
fun McHorizontalGap(width: Int) {
    Spacer(modifier = Modifier.width(width.dp))
}
