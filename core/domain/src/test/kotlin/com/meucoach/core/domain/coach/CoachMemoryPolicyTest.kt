package com.meucoach.core.domain.coach

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

class CoachMemoryPolicyTest {
    private val now = Instant.parse("2026-11-18T12:00:00Z")

    @Test
    fun `messages expire after 90 days and the boundary is inclusive`() {
        val exactly = now.minus(Duration.ofDays(90))
        val older = exactly.minusMillis(1)

        assertThat(CoachMemoryPolicy.MESSAGE_TTL_DAYS).isEqualTo(90)
        assertThat(CoachMemoryPolicy.isMessageExpired(exactly, now)).isFalse()
        assertThat(CoachMemoryPolicy.isMessageExpired(older, now)).isTrue()
    }

    @Test
    fun `summaries expire after 180 days and the boundary is inclusive`() {
        val exactly = now.minus(Duration.ofDays(180))
        val older = exactly.minusMillis(1)

        assertThat(CoachMemoryPolicy.SUMMARY_TTL_DAYS).isEqualTo(180)
        assertThat(CoachMemoryPolicy.isSummaryExpired(exactly, now)).isFalse()
        assertThat(CoachMemoryPolicy.isSummaryExpired(older, now)).isTrue()
    }

    @Test
    fun `cutoff is deterministic and derived only from the clock`() {
        assertThat(CoachMemoryPolicy.messageCutoff(now)).isEqualTo(now.minus(Duration.ofDays(90)))
        assertThat(CoachMemoryPolicy.summaryCutoff(now)).isEqualTo(now.minus(Duration.ofDays(180)))
    }

    @Test
    fun `turn expiry follows the origin TTL`() {
        val createdAt = Instant.parse("2026-11-18T12:00:00Z")

        assertThat(CoachMemoryPolicy.turnExpiresAt(createdAt, ChatTurn.Origin.EXPLAIN))
            .isEqualTo(createdAt.plus(Duration.ofDays(90)))
        assertThat(CoachMemoryPolicy.turnExpiresAt(createdAt, ChatTurn.Origin.REVIEW))
            .isEqualTo(createdAt.plus(Duration.ofDays(90)))
        assertThat(CoachMemoryPolicy.turnExpiresAt(createdAt, ChatTurn.Origin.SUMMARY))
            .isEqualTo(createdAt.plus(Duration.ofDays(180)))
    }
}
