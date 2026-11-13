package com.meucoach.core.domain.story

import com.meucoach.core.model.Pace
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test

class StoryTimelineTest {
    @Test
    fun `keeps the total duration exact and the closing frame at the end`() {
        val splits = splits(8)

        val timeline = StoryTimelinePlanner.plan(splits, targetDurationSec = 30)

        assertThat(timeline.durationSec).isEqualTo(30f)
        assertThat(timeline.segments.sumOf { (it.endSec - it.startSec).toDouble() })
            .isCloseTo(30.0, within(0.001))
        assertThat(timeline.segments.last().type).isEqualTo(StorySegmentType.FINAL)
        assertThat(timeline.segments.last().startSec).isEqualTo(27f)
        assertThat(timeline.segments.last().endSec).isEqualTo(30f)
    }

    @Test
    fun `supports every explicit duration`() {
        listOf(15, 30, 45, 60).forEach { target ->
            val timeline = StoryTimelinePlanner.plan(splits(8), targetDurationSec = target)

            assertThat(timeline.durationSec).isEqualTo(target.toFloat())
            assertThat(timeline.segments.sumOf { (it.endSec - it.startSec).toDouble() })
                .isCloseTo(target.toDouble(), within(0.001))
            assertThat(timeline.segments.last().startSec).isEqualTo((target - 3).toFloat())
        }
    }

    @Test
    fun `centers cards on their kms and never overlaps`() {
        val timeline = StoryTimelinePlanner.plan(splits(8), targetDurationSec = 30)

        timeline.segments.zipWithNext { previous, next ->
            assertThat(next.startSec).isGreaterThanOrEqualTo(previous.endSec - 0.0001f)
        }
        timeline.segments.zipWithNext { previous, next ->
            assertThat(next.progressStart).isGreaterThanOrEqualTo(previous.progressStart)
        }
    }

    @Test
    fun `gives the fastest km a longer card and the slowest a shorter one`() {
        val splits = listOf(
            split(1, pace = 420, heartRate = 140),
            split(2, pace = 360, heartRate = 150),
            split(3, pace = 440, heartRate = 145),
        )

        val timeline = StoryTimelinePlanner.plan(splits, targetDurationSec = 30)
        val durations = timeline.segments
            .filter { it.type == StorySegmentType.CARD }
            .associate { it.split!!.km to (it.endSec - it.startSec) }

        assertThat(durations.getValue(2)).isGreaterThan(durations.getValue(1))
        assertThat(durations.getValue(2)).isGreaterThan(durations.getValue(3))
        assertThat(durations.getValue(3)).isLessThan(durations.getValue(1))
    }

    @Test
    fun `honors the highest heart-rate bonus`() {
        val splits = listOf(
            split(1, pace = 360, heartRate = 140),
            split(2, pace = 360, heartRate = 178),
            split(3, pace = 360, heartRate = 150),
        )

        val timeline = StoryTimelinePlanner.plan(splits, targetDurationSec = 30)
        val durations = timeline.segments
            .filter { it.type == StorySegmentType.CARD }
            .associate { it.split!!.km to (it.endSec - it.startSec) }

        assertThat(durations.getValue(2)).isGreaterThan(durations.getValue(1))
        assertThat(durations.getValue(2)).isGreaterThan(durations.getValue(3))
    }

    @Test
    fun `clamps card duration between twelve hundred and three seconds`() {
        val timeline = StoryTimelinePlanner.plan(splits(8), targetDurationSec = 15)

        timeline.segments
            .filter { it.type == StorySegmentType.CARD }
            .forEach { segment ->
                assertThat(segment.endSec - segment.startSec)
                    .isBetween(1.2f - 0.0001f, 3.0f + 0.0001f)
            }
        assertThat(timeline.segments.last().startSec).isEqualTo(12f)
    }

    @Test
    fun `drops interior cards before shrinking below the clamp`() {
        val timeline = StoryTimelinePlanner.plan(
            splits = splits(8),
            targetDurationSec = 10,
            requiredKms = setOf(1, 8),
        )

        assertThat(timeline.cards.size).isLessThan(8)
        assertThat(timeline.cards.map { it.km }).contains(1, 8)
        timeline.segments
            .filter { it.type == StorySegmentType.CARD }
            .forEach { segment ->
                assertThat(segment.endSec - segment.startSec).isGreaterThanOrEqualTo(1.2f - 0.0001f)
            }
        assertThat(timeline.segments.last().startSec).isEqualTo(7f)
        assertThat(timeline.segments.last().endSec).isEqualTo(10f)
    }

    @Test
    fun `supports a run without completed kilometres`() {
        val timeline = StoryTimelinePlanner.plan(emptyList(), targetDurationSec = 30)

        assertThat(timeline.cards).isEmpty()
        assertThat(timeline.segments).hasSize(2)
        assertThat(timeline.segments.first().type).isEqualTo(StorySegmentType.MOVE)
        assertThat(timeline.segments.first().startSec).isEqualTo(0f)
        assertThat(timeline.segments.first().endSec).isEqualTo(27f)
        assertThat(timeline.segments.last().type).isEqualTo(StorySegmentType.FINAL)
        assertThat(timeline.segments.last().startSec).isEqualTo(27f)
        assertThat(timeline.segments.last().endSec).isEqualTo(30f)
    }

    @Test
    fun `keeps a three second video when the target is shorter than the closing frame`() {
        val timeline = StoryTimelinePlanner.plan(splits(3), targetDurationSec = 2)

        assertThat(timeline.durationSec).isEqualTo(3f)
        assertThat(timeline.segments).hasSize(1)
        assertThat(timeline.segments.single().type).isEqualTo(StorySegmentType.FINAL)
    }

    private fun splits(count: Int): List<StorySplit> = (1..count).map { km ->
        split(km, pace = 330 + km, heartRate = 150 + km)
    }

    private fun split(km: Int, pace: Int, heartRate: Int?): StorySplit = StorySplit(
        km = km,
        pace = Pace.fromSecondsPerKm(pace),
        heartRate = heartRate,
        progress = km / 10f,
    )
}
