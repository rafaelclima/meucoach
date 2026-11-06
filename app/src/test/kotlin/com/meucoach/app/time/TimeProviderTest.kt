package com.meucoach.app.time

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class TimeProviderTest {
    @Test
    fun `fixed clock gives a deterministic day and week`() = runTest {
        val clock = MutableClock(Instant.parse("2026-11-06T12:00:00Z"))
        val provider = SystemTimeProvider(clock, backgroundScope, ZoneOffset.UTC)

        assertThat(provider.currentDate()).isEqualTo(LocalDate.of(2026, 11, 6))
        assertThat(provider.currentWeekStart()).isEqualTo(LocalDate.of(2026, 11, 2))
        assertThat(provider.today.value).isEqualTo(LocalDate.of(2026, 11, 6))
    }

    @Test
    fun `23 59 and 00 01 are different local days`() = runTest {
        val zone = ZoneId.of("America/Sao_Paulo")
        val clock = MutableClock(Instant.parse("2026-11-07T02:59:00Z"))
        val provider = SystemTimeProvider(clock, backgroundScope, zone)

        assertThat(provider.currentDate()).isEqualTo(LocalDate.of(2026, 11, 6))

        clock.current = Instant.parse("2026-11-07T03:01:00Z")
        provider.refresh()
        runCurrent()

        assertThat(provider.currentDate()).isEqualTo(LocalDate.of(2026, 11, 7))
        assertThat(provider.today.value).isEqualTo(LocalDate.of(2026, 11, 7))
    }

    @Test
    fun `sunday to monday changes the week start`() = runTest {
        val zone = ZoneId.of("America/Sao_Paulo")
        val clock = MutableClock(Instant.parse("2026-11-09T02:59:00Z"))
        val provider = SystemTimeProvider(clock, backgroundScope, zone)

        assertThat(provider.currentDate()).isEqualTo(LocalDate.of(2026, 11, 8))
        assertThat(provider.currentWeekStart()).isEqualTo(LocalDate.of(2026, 11, 2))

        clock.current = Instant.parse("2026-11-09T03:01:00Z")
        provider.refresh()
        runCurrent()

        assertThat(provider.currentDate()).isEqualTo(LocalDate.of(2026, 11, 9))
        assertThat(provider.currentWeekStart()).isEqualTo(LocalDate.of(2026, 11, 9))
        assertThat(provider.atStartOfDay(provider.currentDate()).toLocalDate()).isEqualTo(LocalDate.of(2026, 11, 9))
        assertThat(provider.nextMidnight()).isEqualTo(Instant.parse("2026-11-10T03:00:00Z"))
    }

    @Test
    fun `month and year rollover keep the week coherent`() = runTest {
        val zone = ZoneId.of("America/Sao_Paulo")
        val clock = MutableClock(Instant.parse("2027-01-01T02:59:00Z"))
        val provider = SystemTimeProvider(clock, backgroundScope, zone)

        assertThat(provider.currentDate()).isEqualTo(LocalDate.of(2026, 12, 31))
        assertThat(provider.currentWeekStart()).isEqualTo(LocalDate.of(2026, 12, 28))

        clock.current = Instant.parse("2027-01-01T03:01:00Z")
        provider.refresh()
        runCurrent()

        assertThat(provider.currentDate()).isEqualTo(LocalDate.of(2027, 1, 1))
        assertThat(provider.currentWeekStart()).isEqualTo(LocalDate.of(2026, 12, 28))
    }

    @Test
    fun `changing timezone changes the local day without changing the instant`() = runTest {
        val clock = MutableClock(Instant.parse("2026-11-07T03:30:00Z"))
        val provider = SystemTimeProvider(clock, backgroundScope, ZoneId.of("America/Sao_Paulo"))

        assertThat(provider.currentDate()).isEqualTo(LocalDate.of(2026, 11, 7))

        provider.updateZone(ZoneId.of("America/New_York"))
        provider.refresh()
        runCurrent()

        assertThat(provider.now()).isEqualTo(Instant.parse("2026-11-07T03:30:00Z"))
        assertThat(provider.currentDate()).isEqualTo(LocalDate.of(2026, 11, 6))
        assertThat(provider.today.value).isEqualTo(LocalDate.of(2026, 11, 6))
    }

    @Test
    fun `next midnight respects daylight saving transitions`() = runTest {
        val zone = ZoneId.of("America/New_York")

        val springClock = MutableClock(Instant.parse("2027-03-14T05:00:00Z"))
        val spring = SystemTimeProvider(springClock, backgroundScope, zone)
        assertThat(spring.currentDate()).isEqualTo(LocalDate.of(2027, 3, 14))
        assertThat(Duration.between(springClock.instant(), spring.nextMidnight()))
            .isEqualTo(Duration.ofHours(23))

        val fallClock = MutableClock(Instant.parse("2026-11-01T04:00:00Z"))
        val fall = SystemTimeProvider(fallClock, backgroundScope, zone)
        assertThat(fall.currentDate()).isEqualTo(LocalDate.of(2026, 11, 1))
        assertThat(Duration.between(fallClock.instant(), fall.nextMidnight()))
            .isEqualTo(Duration.ofHours(25))
    }
}

private class MutableClock(var current: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    override fun instant(): Instant = current
}
