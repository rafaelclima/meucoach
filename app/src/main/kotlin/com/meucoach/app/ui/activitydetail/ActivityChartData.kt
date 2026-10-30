package com.meucoach.app.ui.activitydetail

import com.meucoach.core.model.ActivitySample
import kotlin.math.ceil
import kotlin.math.roundToInt

data class ActivityChartPoint(val offsetSec: Int, val paceSecPerKm: Int, val heartRate: Int?)

data class ActivityChartData(
    val points: List<ActivityChartPoint>,
    val paceRange: IntRange,
    val heartRateRange: IntRange?,
    val durationSec: Int,
)

/**
 * Builds the pace/heart-rate timeline from samples with a valid speed.
 *
 * Pace is derived from speed because samples do not carry cumulative distance.
 * Samples are downsampled evenly for drawing; the last sample is always kept so
 * the chart ends at the real end of the run.
 */
fun chartData(samples: List<ActivitySample>, maxPoints: Int = 240): ActivityChartData? {
    require(maxPoints > 1) { "maxPoints must be greater than one" }
    val usable = samples
        .mapNotNull { sample ->
            val speed = sample.speedMps ?: return@mapNotNull null
            if (speed <= MIN_SPEED_MPS) return@mapNotNull null
            val pace = (1_000.0 / speed).roundToInt().coerceIn(MIN_PACE_SEC, MAX_PACE_SEC)
            ActivityChartPoint(
                offsetSec = sample.offsetSec,
                paceSecPerKm = pace,
                heartRate = sample.heartRate,
            )
        }
    if (usable.size < 2) return null

    val step = ceil(usable.size.toDouble() / maxPoints).toInt().coerceAtLeast(1)
    val points = buildList {
        usable.forEachIndexed { index, point ->
            if (index % step == 0) add(point)
        }
        if (lastOrNull() != usable.last()) add(usable.last())
    }

    val paceRange = usable.minOf { it.paceSecPerKm }..usable.maxOf { it.paceSecPerKm }
    val heartRates = usable.mapNotNull { it.heartRate }
    val heartRateRange = if (heartRates.size >= 2) {
        heartRates.min()..heartRates.max()
    } else {
        null
    }
    return ActivityChartData(
        points = points,
        paceRange = paceRange,
        heartRateRange = heartRateRange,
        durationSec = usable.last().offsetSec,
    )
}

private const val MIN_SPEED_MPS = 0.5
private const val MIN_PACE_SEC = 120
private const val MAX_PACE_SEC = 1_800
