package com.meucoach.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.theme.McTheme

data class McBottomBarItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun McBottomBar(
    items: List<McBottomBarItem>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = McTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.borderSubtle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(68.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = item.route == selectedRoute
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = selected,
                            onClick = { onSelect(item.route) },
                            role = Role.Tab,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(30.dp)
                            .background(
                                color = if (selected) colors.accent else Color.Transparent,
                                shape = RoundedCornerShape(15.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (selected) colors.onAccent else colors.textTertiary,
                        )
                    }
                    Text(
                        text = item.label,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = if (selected) colors.textPrimary else colors.textTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
