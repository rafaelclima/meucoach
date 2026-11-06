package com.meucoach.app.time

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Deterministic clock for tests. Advancing it explicitly is the only way the
 * local day changes, so tests never depend on the machine clock.
 */
class FakeTimeProvider(instant: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) : TimeProvider {
    private var instant = instant
    private val zoneState = MutableStateFlow(zone)
    private val todayState = MutableStateFlow(instant.atZone(zone).toLocalDate())

    override val today: StateFlow<LocalDate> = todayState.asStateFlow()
    override val zoneId: StateFlow<ZoneId> = zoneState.asStateFlow()

    override fun now(): Instant = instant

    override fun refresh() {
        todayState.value = currentDate()
    }

    fun setNow(newInstant: Instant) {
        instant = newInstant
        refresh()
    }

    fun setZone(newZone: ZoneId) {
        zoneState.value = newZone
        refresh()
    }

    fun set(instant: Instant, zone: ZoneId) {
        this.instant = instant
        zoneState.value = zone
        refresh()
    }
}
