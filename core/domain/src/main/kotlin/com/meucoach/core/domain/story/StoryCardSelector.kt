package com.meucoach.core.domain.story

/**
 * Picks the kms that receive a card. The old `index % step` rule produced five
 * cards for a 10 km run and hid the fastest/slowest kms; this selector always
 * keeps the first km, the last complete km, the fastest and the slowest, then
 * spreads the remaining slots evenly.
 */
object StoryCardSelector {
    const val MAX_CARDS = 8

    fun select(splits: List<StorySplit>): List<StorySplit> {
        if (splits.isEmpty()) return emptyList()
        val ordered = splits.sortedBy { it.km }
        if (ordered.size <= MAX_CARDS) return ordered
        val required = linkedSetOf(ordered.first(), ordered.last())
        ordered.minByOrNull { it.pace.secondsPerKm }?.let { required += it }
        ordered.maxByOrNull { it.pace.secondsPerKm }?.let { required += it }
        val requiredKms = required.map { it.km }.toSet()
        val free = ordered.filterNot { it.km in requiredKms }
        val remaining = (MAX_CARDS - required.size).coerceAtLeast(0)
        val extra = pickEvenly(free, remaining)
        return (required + extra).sortedBy { it.km }
    }

    private fun pickEvenly(splits: List<StorySplit>, count: Int): List<StorySplit> {
        if (count <= 0 || splits.isEmpty()) return emptyList()
        if (splits.size <= count) return splits
        val chosen = linkedSetOf<StorySplit>()
        for (index in 0 until count) {
            val position = ((index + 0.5) * splits.size / count).toInt()
                .coerceIn(0, splits.lastIndex)
            chosen += splits[position]
        }
        if (chosen.size < count) {
            splits.forEach { split ->
                if (chosen.size < count) chosen += split
            }
        }
        return chosen.toList()
    }
}

/**
 * Builds the split bar from **all** completed kms, not only the ones with a
 * card. A 10 km run therefore shows ten segments even when only eight get a
 * card. Runs above 20 km are grouped into blocks of 2–5 km with a label.
 */
object StorySplitBarCalculator {
    const val SINGLE_KM_LIMIT = 20

    fun calculate(allSplits: List<StorySplit>, cardKms: Set<Int>): StorySplitBar {
        if (allSplits.isEmpty()) return StorySplitBar.Empty
        val maxKm = allSplits.maxOf { it.km }
        val block = blockSize(maxKm)
        val segments = buildList {
            var start = 1
            while (start <= maxKm) {
                val end = minOf(start + block - 1, maxKm)
                add(
                    StorySplitBarSegment(
                        startKm = start,
                        endKm = end,
                        hasCard = (start..end).any { it in cardKms },
                    ),
                )
                start = end + 1
            }
        }
        return StorySplitBar(segments)
    }

    fun blockSize(totalKm: Int): Int = when {
        totalKm <= SINGLE_KM_LIMIT -> 1
        totalKm <= 30 -> 2
        totalKm <= 40 -> 3
        totalKm <= 60 -> 4
        else -> 5
    }
}

/**
 * Hysteresis elevation gain, shared by the total and the per-km values.
 * Missing samples stay missing: fewer than [MIN_SAMPLES] altitudes return null
 * instead of turning into 0 m.
 */
object StoryElevation {
    const val MIN_SAMPLES = 10
    const val HYSTERESIS_M = 1.0

    fun gain(altitudes: List<Double>): Double? {
        val values = altitudes.filter { it.isFinite() }
        if (values.size < MIN_SAMPLES) return null
        var gain = 0.0
        var reference = values.first()
        for (index in 1 until values.size) {
            val altitude = values[index]
            val delta = altitude - reference
            if (delta >= HYSTERESIS_M) {
                gain += delta
                reference = altitude
            } else if (delta <= -HYSTERESIS_M) {
                reference = altitude
            }
        }
        return gain
    }
}
