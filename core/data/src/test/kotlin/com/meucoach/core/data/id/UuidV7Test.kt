package com.meucoach.core.data.id

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Random
import java.util.UUID

class UuidV7Test {
    @Test
    fun `generates a time ordered UUID version 7`() {
        val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)

        val id = UuidV7.generate(clock, Random(1))
        val uuid = UUID.fromString(id)

        assertThat(uuid.version()).isEqualTo(7)
        assertThat(uuid.variant()).isEqualTo(2)
        assertThat(uuid.mostSignificantBits ushr 16).isEqualTo(clock.millis())
    }

    @Test
    fun `is deterministic when clock and random source are fixed`() {
        val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)

        val first = UuidV7.generate(clock, Random(42))
        val second = UuidV7.generate(clock, Random(42))

        assertThat(first).isEqualTo(second)
    }
}
