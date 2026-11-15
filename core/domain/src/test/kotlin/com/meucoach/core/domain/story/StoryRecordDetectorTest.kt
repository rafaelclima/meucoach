package com.meucoach.core.domain.story

import com.meucoach.core.model.Pace
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Sprint 3, fatia 1 (DOM-09): the detector is pure and fully tested; the
 * spec/renderer wiring with the badge golden comes in the next slice.
 */
class StoryRecordDetectorTest {
    @Test
    fun `needs at least three runs before any record`() {
        val history = listOf(
            run("a", distanceM = 5_000.0, paceSec = 360),
            run("b", distanceM = 6_000.0, paceSec = 370),
        )

        assertThat(StoryRecordDetector.detect(history, "b")).isEmpty()
    }

    @Test
    fun `flags a strictly longer distance`() {
        val history = listOf(
            run("a", distanceM = 5_000.0, paceSec = 360),
            run("b", distanceM = 6_000.0, paceSec = 370),
            run("c", distanceM = 10_000.0, paceSec = 380),
        )

        assertThat(StoryRecordDetector.detect(history, "c"))
            .containsExactly(StoryRecordKind.LONGEST_DISTANCE)
    }

    @Test
    fun `a tied distance is not a record`() {
        val history = listOf(
            run("a", distanceM = 10_000.0, paceSec = 360),
            run("b", distanceM = 5_000.0, paceSec = 370),
            run("c", distanceM = 10_000.0, paceSec = 380),
        )

        assertThat(StoryRecordDetector.detect(history, "c")).isEmpty()
    }

    @Test
    fun `missing current distance invents no record`() {
        val history = listOf(
            run("a", distanceM = 5_000.0, paceSec = 360),
            run("b", distanceM = 6_000.0, paceSec = 370),
            run("c", distanceM = null, paceSec = 380),
        )

        assertThat(StoryRecordDetector.detect(history, "c"))
            .doesNotContain(StoryRecordKind.LONGEST_DISTANCE)
    }

    @Test
    fun `no comparable past distance means no record`() {
        val history = listOf(
            run("a", distanceM = null, paceSec = 360),
            run("b", distanceM = null, paceSec = 370),
            run("c", distanceM = 5_000.0, paceSec = 380),
        )

        assertThat(StoryRecordDetector.detect(history, "c"))
            .doesNotContain(StoryRecordKind.LONGEST_DISTANCE)
    }

    @Test
    fun `flags a strictly faster pace from three km up`() {
        val history = listOf(
            run("a", distanceM = 5_000.0, paceSec = 360),
            run("b", distanceM = 5_000.0, paceSec = 370),
            run("c", distanceM = 5_000.0, paceSec = 350),
        )

        assertThat(StoryRecordDetector.detect(history, "c"))
            .containsExactly(StoryRecordKind.FASTEST_PACE)
    }

    @Test
    fun `short runs never compete for the fastest pace`() {
        val history = listOf(
            run("a", distanceM = 5_000.0, paceSec = 400),
            run("b", distanceM = 5_000.0, paceSec = 410),
            run("c", distanceM = 2_000.0, paceSec = 300),
        )

        assertThat(StoryRecordDetector.detect(history, "c")).isEmpty()
    }

    @Test
    fun `short past runs do not set the pace bar`() {
        val history = listOf(
            run("a", distanceM = 2_000.0, paceSec = 300),
            run("b", distanceM = 2_000.0, paceSec = 310),
            run("c", distanceM = 5_000.0, paceSec = 350),
        )

        // No past run at 3 km or more with a measured pace: nothing to beat,
        // so a first longer run is not called a pace record. The distance
        // record still holds on its own rule.
        assertThat(StoryRecordDetector.detect(history, "c"))
            .containsExactly(StoryRecordKind.LONGEST_DISTANCE)
    }

    @Test
    fun `a tied pace is not a record`() {
        val history = listOf(
            run("a", distanceM = 5_000.0, paceSec = 350),
            run("b", distanceM = 5_000.0, paceSec = 370),
            run("c", distanceM = 5_000.0, paceSec = 350),
        )

        assertThat(StoryRecordDetector.detect(history, "c")).isEmpty()
    }

    @Test
    fun `missing current pace invents no record`() {
        val history = listOf(
            run("a", distanceM = 5_000.0, paceSec = 360),
            run("b", distanceM = 5_000.0, paceSec = 370),
            run("c", distanceM = 5_000.0, paceSec = null),
        )

        assertThat(StoryRecordDetector.detect(history, "c")).isEmpty()
    }

    @Test
    fun `an unknown activity never holds a record`() {
        val history = listOf(
            run("a", distanceM = 5_000.0, paceSec = 360),
            run("b", distanceM = 5_000.0, paceSec = 370),
            run("c", distanceM = 10_000.0, paceSec = 350),
        )

        assertThat(StoryRecordDetector.detect(history, "gone")).isEmpty()
    }

    @Test
    fun `flags distance and pace together in enum order`() {
        val history = listOf(
            run("a", distanceM = 5_000.0, paceSec = 360),
            run("b", distanceM = 5_000.0, paceSec = 370),
            run("c", distanceM = 10_000.0, paceSec = 350),
        )

        assertThat(StoryRecordDetector.detect(history, "c")).containsExactly(
            StoryRecordKind.LONGEST_DISTANCE,
            StoryRecordKind.FASTEST_PACE,
        )
    }

    private fun run(id: String, distanceM: Double?, paceSec: Int?): StoryRecordCandidate = StoryRecordCandidate(
        activityId = id,
        distanceM = distanceM,
        averagePace = paceSec?.let { Pace.fromSecondsPerKm(it) },
    )
}
