package com.meucoach.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import java.util.Locale

@Composable
fun McStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    hint: String? = null,
    valueColor: Color = McTheme.colors.textPrimary,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    val colors = McTheme.colors
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(
            text = label.uppercase(Locale.ROOT),
            style = McTextStyles.overline,
            color = colors.textTertiary,
            textAlign = if (horizontalAlignment == Alignment.CenterHorizontally) TextAlign.Center else TextAlign.Start,
        )
        Spacer(modifier = Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, style = McTextStyles.metric, color = valueColor)
            if (unit != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = McTextStyles.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        if (hint != null) {
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = hint,
                style = McTextStyles.caption,
                color = colors.textTertiary,
                textAlign = if (horizontalAlignment ==
                    Alignment.CenterHorizontally
                ) {
                    TextAlign.Center
                } else {
                    TextAlign.Start
                },
            )
        }
    }
}
