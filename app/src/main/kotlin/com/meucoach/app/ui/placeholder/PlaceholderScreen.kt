package com.meucoach.app.ui.placeholder

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.component.McCardVariant
import com.meucoach.core.designsystem.component.McEmptyState
import com.meucoach.core.designsystem.component.McPill
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme

@Composable
fun PlaceholderScreen(
    eyebrow: String,
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    emptyTitle: String = "Ainda não há dados",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = McTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        McPill(text = "FASE 0", containerColor = colors.accentSoft, contentColor = colors.accentText)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = eyebrow, style = McTextStyles.overline, color = colors.textTertiary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = title, style = androidx.compose.material3.MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(20.dp))
        McCard(
            modifier = Modifier.fillMaxWidth(),
            variant = McCardVariant.INSET,
        ) {
            McEmptyState(
                icon = icon,
                title = emptyTitle,
                description = description,
                actionLabel = actionLabel,
                onAction = onAction,
            )
        }
    }
}
