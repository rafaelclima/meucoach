package com.meucoach.core.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RaceEventTest {
    private val at = Instant.parse("2026-11-10T12:00:00Z")
    private val date = LocalDate.of(2026, 12, 6)

    @Test
    fun `planned event requires no resolution timestamp or activity`() {
        val event = event()

        assertThat(event.status).isEqualTo(RaceStatus.PLANNED)
        assertThat(event.resolvedAt).isNull()
        assertThat(event.activityId).isNull()
        assertThat(event.distance.meters).isEqualTo(10_000.0)
    }

    @Test
    fun `completed event can exist without an activity`() {
        val completed = event(status = RaceStatus.COMPLETED, resolvedAt = at)

        assertThat(completed.status).isEqualTo(RaceStatus.COMPLETED)
        assertThat(completed.activityId).isNull()
    }

    @Test
    fun `completed event accepts an optional activity`() {
        val completed = event(
            status = RaceStatus.COMPLETED,
            resolvedAt = at,
            activityId = "activity-1",
        )

        assertThat(completed.activityId).isEqualTo("activity-1")
    }

    @Test
    fun `terminal states require a resolution timestamp`() {
        assertThatThrownBy {
            event(status = RaceStatus.CANCELLED)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("resolved")
    }

    @Test
    fun `planned event rejects a resolution timestamp`() {
        assertThatThrownBy {
            event(status = RaceStatus.PLANNED, resolvedAt = at)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("resolved")
    }

    @Test
    fun `activity is only allowed on a completed event`() {
        assertThatThrownBy {
            event(status = RaceStatus.PLANNED, activityId = "activity-1")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("activity")
    }

    @Test
    fun `completed event can carry the source of its completion`() {
        for (source in RaceCompletionSource.entries) {
            val completed = event(
                status = RaceStatus.COMPLETED,
                resolvedAt = at,
                completionSource = source,
            )

            assertThat(completed.completionSource).isEqualTo(source)
        }
    }

    @Test
    fun `a legacy completed event may have no completion source`() {
        val completed = event(status = RaceStatus.COMPLETED, resolvedAt = at)

        assertThat(completed.completionSource).isNull()
    }

    @Test
    fun `completion source is only allowed on a completed event`() {
        assertThatThrownBy {
            event(completionSource = RaceCompletionSource.AUTO_IMPORT)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("completion source")

        assertThatThrownBy {
            event(
                status = RaceStatus.CANCELLED,
                resolvedAt = at,
                completionSource = RaceCompletionSource.USER,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("completion source")
    }

    @Test
    fun `target time must be positive`() {
        assertThatThrownBy { event(targetTimeSec = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("time")
    }

    @Test
    fun `a past target date remains a valid planned event`() {
        val past = event(targetDate = LocalDate.of(2026, 1, 1))

        assertThat(past.status).isEqualTo(RaceStatus.PLANNED)
        assertThat(past.targetDate).isBefore(LocalDate.of(2026, 11, 10))
    }

    @Test
    fun `supported distances expose canonical meters`() {
        assertThat(RaceDistance.entries.map { it.meters })
            .containsExactly(5_000.0, 10_000.0, 21_097.5, 42_195.0)
    }

    private fun event(
        status: RaceStatus = RaceStatus.PLANNED,
        resolvedAt: Instant? = null,
        activityId: String? = null,
        completionSource: RaceCompletionSource? = null,
        targetTimeSec: Int? = 3_600,
        targetDate: LocalDate = date,
    ) = RaceEvent(
        id = "race-1",
        distance = RaceDistance.TEN_K,
        targetDate = targetDate,
        targetTimeSec = targetTimeSec,
        status = status,
        activityId = activityId,
        completionSource = completionSource,
        createdAt = at,
        updatedAt = at,
        resolvedAt = resolvedAt,
    )
}
