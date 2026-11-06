package com.meucoach.core.domain.retention

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant

class TrashPolicyTest {
    private val deletedAt = Instant.parse("2026-11-01T12:00:00Z")

    @Test
    fun `keeps the item before the seven-day window`() {
        assertThat(TrashPolicy.purgeable(deletedAt, Instant.parse("2026-11-08T11:59:59Z"))).isFalse()
        assertThat(TrashPolicy.daysRemaining(deletedAt, Instant.parse("2026-11-07T12:00:00Z"))).isEqualTo(1)
    }

    @Test
    fun `purges exactly at seven days and after`() {
        assertThat(TrashPolicy.purgeable(deletedAt, Instant.parse("2026-11-08T12:00:00Z"))).isTrue()
        assertThat(TrashPolicy.purgeable(deletedAt, Instant.parse("2026-11-08T12:00:01Z"))).isTrue()
        assertThat(TrashPolicy.daysRemaining(deletedAt, Instant.parse("2026-11-08T12:00:00Z"))).isZero()
    }

    @Test
    fun `expiry is seven days after deletion`() {
        assertThat(TrashPolicy.expiresAt(deletedAt)).isEqualTo(Instant.parse("2026-11-08T12:00:00Z"))
    }
}
