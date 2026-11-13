package com.meucoach.core.domain.story

import kotlin.math.max
import kotlin.math.min

/** Result of fitting km cards and route movement into the target duration. */
data class StoryTimeline(val cards: List<StorySplit>, val segments: List<StorySegment>, val durationSec: Float)

/**
 * Timeline by effort, not by km count (ADR 0040 §2.5):
 *
 * - base card 1.8 s;
 * - fastest km +0.6 s;
 * - highest-HR km +0.3 s;
 * - slowest km −0.3 s;
 * - card clamp 1.2–3.0 s;
 * - closing frame always 3.0 s.
 *
 * The closing frame is reserved inside the target duration, never appended.
 * When the target cannot fit every card inside the minimum clamp, the planner
 * drops interior cards (minimum four, keeping the required ones) before
 * shrinking a card below 1.2 s.
 */
object StoryTimelinePlanner {
    const val FINAL_SECONDS = 3.0
    const val BASE_CARD_SECONDS = 1.8
    const val FASTEST_BONUS_SECONDS = 0.6
    const val HIGHEST_HR_BONUS_SECONDS = 0.3
    const val SLOWEST_PENALTY_SECONDS = 0.3
    const val MIN_CARD_SECONDS = 1.2
    const val MAX_CARD_SECONDS = 3.0
    const val MIN_CARDS = 4

    fun plan(splits: List<StorySplit>, targetDurationSec: Int, requiredKms: Set<Int> = emptySet()): StoryTimeline {
        require(targetDurationSec > 0) { "Story duration must be positive" }
        val total = max(targetDurationSec.toDouble(), FINAL_SECONDS)
        val cardBudget = total - FINAL_SECONDS
        val finalSegment = StorySegment(
            startSec = (total - FINAL_SECONDS).toFloat(),
            endSec = total.toFloat(),
            type = StorySegmentType.FINAL,
            progressStart = 1f,
            progressEnd = 1f,
        )
        if (splits.isEmpty() || cardBudget <= 0.0) {
            val segments = buildList {
                if (cardBudget > 0.0) {
                    add(
                        StorySegment(
                            startSec = 0f,
                            endSec = (total - FINAL_SECONDS).toFloat(),
                            type = StorySegmentType.MOVE,
                            progressStart = 0f,
                            progressEnd = 1f,
                        ),
                    )
                }
                add(finalSegment)
            }
            return StoryTimeline(
                cards = emptyList(),
                segments = segments,
                durationSec = total.toFloat(),
            )
        }

        val cards = fitCards(splits.sortedBy { it.km }, cardBudget, requiredKms)
        val cardDurations = cardDurations(cards, cardBudget)
        val segments = buildSegments(cards, cardDurations, total, finalSegment)
        return StoryTimeline(cards = cards, segments = segments, durationSec = total.toFloat())
    }

    /** Drops the middle-most non-required cards while the minimum clamp does not fit. */
    private fun fitCards(splits: List<StorySplit>, cardBudget: Double, requiredKms: Set<Int>): List<StorySplit> {
        if (splits.isEmpty()) return emptyList()
        val cards = splits.toMutableList()
        // Four cards is the product floor; only an impossible target (below
        // 7.8 s, unreachable through StoryDuration) may go lower.
        val floor = min(MIN_CARDS, max(1, (cardBudget / MIN_CARD_SECONDS).toInt()))
        while (cards.size > floor && cardBudget < cards.size * MIN_CARD_SECONDS) {
            val removable = cards.indices.filter { index ->
                val km = cards[index].km
                km !in requiredKms &&
                    index != 0 &&
                    index != cards.lastIndex
            }
            if (removable.isEmpty()) break
            cards.removeAt(removable[removable.size / 2])
        }
        return cards
    }

    private fun cardDurations(cards: List<StorySplit>, cardBudget: Double): List<Double> {
        if (cards.isEmpty()) return emptyList()
        val desired = desiredDurations(cards)
        val minimum = cards.size * MIN_CARD_SECONDS
        val maximum = cards.size * MAX_CARD_SECONDS
        if (cardBudget < minimum) {
            // Only reachable for impossible targets below the product minimum.
            return List(cards.size) { cardBudget / cards.size }
        }
        val cardTotal = cardBudget.coerceAtMost(maximum)
        val desiredSum = desired.sum()
        // Leftover time belongs to the route movement, not to the cards: the
        // effort bonuses must survive a generous target (ADR 0040 §2.5).
        if (desiredSum <= cardTotal) return desired
        val durations = desired.toMutableList()
        val reducible = desiredSum - minimum
        if (reducible <= 0.0) return List(cards.size) { MIN_CARD_SECONDS }
        val ratio = (cardTotal - minimum) / reducible
        durations.indices.forEach { index ->
            durations[index] = MIN_CARD_SECONDS + (desired[index] - MIN_CARD_SECONDS) * ratio
        }
        return durations
    }

    private fun desiredDurations(cards: List<StorySplit>): List<Double> {
        val durations = MutableList(cards.size) { BASE_CARD_SECONDS }
        if (cards.size < 2) return durations
        val fastest = cards.minByOrNull { it.pace.secondsPerKm } ?: return durations
        val slowest = cards.maxByOrNull { it.pace.secondsPerKm } ?: return durations
        if (fastest.km != slowest.km) {
            durations[cards.indexOf(fastest)] += FASTEST_BONUS_SECONDS
            durations[cards.indexOf(slowest)] -= SLOWEST_PENALTY_SECONDS
        }
        val highestHr = cards
            .filter { it.heartRate != null }
            .maxByOrNull { it.heartRate ?: 0 }
        if (highestHr != null && highestHr.km != fastest.km) {
            durations[cards.indexOf(highestHr)] += HIGHEST_HR_BONUS_SECONDS
        }
        return durations.map { it.coerceIn(MIN_CARD_SECONDS, MAX_CARD_SECONDS) }
    }

    private fun buildSegments(
        cards: List<StorySplit>,
        cardDurations: List<Double>,
        total: Double,
        finalSegment: StorySegment,
    ): List<StorySegment> {
        val finalStart = total - FINAL_SECONDS
        val moveBudget = (finalStart - cardDurations.sum()).coerceAtLeast(0.0)
        val totalDelta = routeDelta(cards)
        val segments = mutableListOf<StorySegment>()
        var cursor = 0.0
        var previousProgress = 0f
        cards.forEachIndexed { index, split ->
            val delta = (split.progress - previousProgress).coerceAtLeast(0f)
            if (delta > 0f && totalDelta > 0f) {
                val duration = moveBudget * delta / totalDelta
                segments += StorySegment(
                    startSec = cursor.toFloat(),
                    endSec = (cursor + duration).toFloat(),
                    type = StorySegmentType.MOVE,
                    progressStart = previousProgress,
                    progressEnd = split.progress,
                )
                cursor += duration
            }
            segments += StorySegment(
                startSec = cursor.toFloat(),
                endSec = (cursor + cardDurations[index]).toFloat(),
                type = StorySegmentType.CARD,
                progressStart = split.progress,
                progressEnd = split.progress,
                split = split,
            )
            cursor += cardDurations[index]
            previousProgress = split.progress
        }
        val tail = (1f - previousProgress).coerceAtLeast(0f)
        if (tail > 0f && totalDelta > 0f) {
            val duration = moveBudget * tail / totalDelta
            segments += StorySegment(
                startSec = cursor.toFloat(),
                endSec = (cursor + duration).toFloat(),
                type = StorySegmentType.MOVE,
                progressStart = previousProgress,
                progressEnd = 1f,
            )
            cursor += duration
        }
        if (cursor < finalStart) {
            // Either the cards already froze at their progress or rounding left
            // a few milliseconds: keep the timeline contiguous.
            segments += StorySegment(
                startSec = cursor.toFloat(),
                endSec = finalStart.toFloat(),
                type = StorySegmentType.MOVE,
                progressStart = previousProgress,
                progressEnd = previousProgress,
            )
        }
        segments += finalSegment
        return segments
    }

    private fun routeDelta(cards: List<StorySplit>): Double {
        if (cards.isEmpty()) return 0.0
        var total = cards.first().progress.toDouble()
        cards.zipWithNext { previous, next ->
            total += (next.progress - previous.progress).coerceAtLeast(0f)
        }
        total += (1f - cards.last().progress).coerceAtLeast(0f)
        return total
    }
}
