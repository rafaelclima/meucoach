package com.meucoach.app.ui.activitydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meucoach.app.ui.activities.formatDuration
import com.meucoach.core.designsystem.component.McCard
import com.meucoach.core.designsystem.theme.McTextStyles
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.zones.HrZoneDistribution
import com.meucoach.core.model.HeartRateZone
import java.util.Locale

@Composable
internal fun HrZonesCard(distribution: HrZoneDistribution, lthr: Int) {
    val colors = McTheme.colors
    McCard {
        Text(
            text = "Zonas de FC",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tempo em cada intensidade · Baseado no seu limiar de esforço (LTHR: $lthr bpm)",
            style = McTextStyles.caption,
            color = colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp)),
        ) {
            distribution.zones
                .filter { it.durationSec > 0 }
                .forEach { zone ->
                    Box(
                        modifier = Modifier
                            .weight(zone.durationSec.toFloat())
                            .fillMaxHeight()
                            .background(zoneColor(zone.zone)),
                    )
                }
        }
        Spacer(modifier = Modifier.height(14.dp))
        distribution.zones.forEach { zone ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(zoneColor(zone.zone)),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Z${zone.zone.number}",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.width(32.dp),
                )
                Text(
                    text = formatDuration(zone.durationSec),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = String.format(PT_BR, "%.0f%%", distribution.percentOf(zone.zone)),
                    style = McTextStyles.caption,
                    color = colors.textTertiary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(48.dp),
                )
            }
        }
    }
}

@Composable
private fun zoneColor(zone: HeartRateZone): Color {
    val colors = McTheme.colors
    return when (zone) {
        HeartRateZone.Z1 -> colors.zone1
        HeartRateZone.Z2 -> colors.zone2
        HeartRateZone.Z3 -> colors.zone3
        HeartRateZone.Z4 -> colors.zone4
        HeartRateZone.Z5 -> colors.zone5
    }
}

private val PT_BR = Locale.forLanguageTag("pt-BR")
