package com.meucoach.core.domain.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * C4-34 — one plausible night (ADR 0049 §2.4).
 *
 * The night belongs to the local date it ends. Sessions only count when they
 * overlap [previous 18:00, current 12:00]; blocks separated by up to two hours
 * are the same night, and a fragmented night uses the largest block instead of
 * summing pieces. Anything outside 180..840 min is suspect, never a short night.
 */
class SleepNightValidatorTest {
    private val date = LocalDate.of(2026, 10, 30)
    private val zone = ZoneOffset.UTC

    @Test
    fun `validates a normal night`() {
        val night = validate(session("2026-10-29T23:00:00Z", "2026-10-30T06:00:00Z"))

        assertThat(night.date).isEqualTo(date)
        assertThat(night.minutes).isEqualTo(420)
        assertThat(night.suspect).isFalse()
        assertThat(night.fragmented).isFalse()
    }

    @Test
    fun `a partial sync session under three hours is suspect`() {
        val night = validate(session("2026-10-30T02:00:00Z", "2026-10-30T02:36:00Z"))

        assertThat(night.minutes).isNull()
        assertThat(night.suspect).isTrue()
    }

    @Test
    fun `five and a half hours is valid for the short sleep layer`() {
        val night = validate(session("2026-10-30T00:00:00Z", "2026-10-30T05:30:00Z"))

        assertThat(night.minutes).isEqualTo(330)
        assertThat(night.suspect).isFalse()
    }

    @Test
    fun `exactly three hours is the inclusive floor`() {
        val night = validate(session("2026-10-30T00:00:00Z", "2026-10-30T03:00:00Z"))

        assertThat(night.minutes).isEqualTo(180)
        assertThat(night.suspect).isFalse()
    }

    @Test
    fun `under three hours is suspect even when it is a real short night`() {
        val night = validate(session("2026-10-30T00:00:00Z", "2026-10-30T02:59:00Z"))

        assertThat(night.minutes).isNull()
        assertThat(night.suspect).isTrue()
    }

    @Test
    fun `over fourteen hours is suspect`() {
        val night = validate(session("2026-10-29T18:00:00Z", "2026-10-30T10:00:00Z"))

        assertThat(night.minutes).isNull()
        assertThat(night.suspect).isTrue()
    }

    @Test
    fun `daytime naps do not enter the night window`() {
        val night = validate(
            session("2026-10-29T23:00:00Z", "2026-10-30T05:00:00Z"),
            session("2026-10-30T14:00:00Z", "2026-10-30T14:30:00Z"),
        )

        assertThat(night.minutes).isEqualTo(360)
        assertThat(night.fragmented).isFalse()
    }

    @Test
    fun `merges blocks separated by up to two hours`() {
        val night = validate(
            session("2026-10-29T23:00:00Z", "2026-10-30T03:00:00Z"),
            session("2026-10-30T03:30:00Z", "2026-10-30T07:30:00Z"),
        )

        assertThat(night.minutes).isEqualTo(480)
        assertThat(night.fragmented).isFalse()
    }

    @Test
    fun `sums separate pieces inside the same block without counting the gap`() {
        val night = validate(
            session("2026-10-30T00:00:00Z", "2026-10-30T06:00:00Z"),
            session("2026-10-30T07:00:00Z", "2026-10-30T08:00:00Z"),
        )

        assertThat(night.minutes).isEqualTo(420)
        assertThat(night.fragmented).isFalse()
    }

    @Test
    fun `fragmented sleep beyond the gap uses the largest block`() {
        val night = validate(
            session("2026-10-29T22:00:00Z", "2026-10-30T02:00:00Z"),
            session("2026-10-30T05:00:00Z", "2026-10-30T09:00:00Z"),
        )

        assertThat(night.minutes).isEqualTo(240)
        assertThat(night.fragmented).isTrue()
        assertThat(night.suspect).isFalse()
    }

    @Test
    fun `no session is suspect instead of a zero night`() {
        val night = SleepNightValidator.validate(date, emptyList(), zone)

        assertThat(night.minutes).isNull()
        assertThat(night.suspect).isTrue()
    }

    @Test
    fun `rejects a session that ends before it starts`() {
        org.assertj.core.api.Assertions.assertThatThrownBy {
            HealthSleepSession(
                start = Instant.parse("2026-10-30T06:00:00Z"),
                end = Instant.parse("2026-10-30T05:00:00Z"),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun validate(vararg sessions: HealthSleepSession) =
        SleepNightValidator.validate(date, sessions.toList(), zone)

    private fun session(start: String, end: String) = HealthSleepSession(
        start = Instant.parse(start),
        end = Instant.parse(end),
    )
}
