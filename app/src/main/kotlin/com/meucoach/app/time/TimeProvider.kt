package com.meucoach.app.time

import com.meucoach.app.di.ApplicationScope
import com.meucoach.core.data.repository.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single application clock. Everything that needs to know "what day is it" must
 * come through here; the zone is the profile zone when one exists and the
 * system zone only as an explicit pre-onboarding fallback (ADR 0022).
 */
interface TimeProvider {
    val today: StateFlow<LocalDate>
    val zoneId: StateFlow<ZoneId>

    fun now(): Instant
    fun currentDate(): LocalDate = now().atZone(zoneId.value).toLocalDate()
    fun currentWeekStart(): LocalDate = currentDate().weekStart()
    fun atStartOfDay(date: LocalDate): ZonedDateTime = date.atStartOfDay(zoneId.value)
    fun nextMidnight(): Instant = currentDate().plusDays(1).atStartOfDay(zoneId.value).toInstant()

    /** Re-evaluates the local day and any fallback timezone on process resume. */
    fun refresh()
}

internal fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/**
 * System-backed time source. It owns the midnight signal and exposes a single
 * mutable zone that [ProfileTimeProvider] keeps in sync with the profile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
open class SystemTimeProvider(
    private val clock: Clock,
    private val scope: CoroutineScope,
    initialZone: ZoneId = ZoneId.systemDefault(),
) : TimeProvider {
    private val zone = MutableStateFlow(initialZone)
    private val refreshTick = MutableStateFlow(0L)

    override val zoneId: StateFlow<ZoneId> = zone.asStateFlow()

    override val today: StateFlow<LocalDate> = combine(zone, refreshTick) { currentZone, _ ->
        currentZone
    }
        .flatMapLatest { currentZone ->
            flow {
                while (true) {
                    val now = clock.instant()
                    val date = now.atZone(currentZone).toLocalDate()
                    emit(date)
                    val next = date.plusDays(1).atStartOfDay(currentZone).toInstant()
                    val waitMillis = Duration.between(now, next).toMillis().coerceAtLeast(1L)
                    delay(waitMillis)
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = clock.instant().atZone(zone.value).toLocalDate(),
        )

    override fun now(): Instant = clock.instant()

    override fun refresh() {
        refreshTick.update { it + 1 }
    }

    /** Used by [ProfileTimeProvider] when the profile zone or system fallback changes. */
    fun updateZone(zoneId: ZoneId) {
        if (zone.value != zoneId) zone.value = zoneId
    }
}

/**
 * Observes the profile timezone. Before onboarding there is no profile, so the
 * system zone is used as a documented fallback. Once a profile exists, every
 * local day/week decision follows it and a change is reflected immediately.
 */
@Singleton
class ProfileTimeProvider @Inject constructor(
    private val profileRepository: ProfileRepository,
    clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) : SystemTimeProvider(clock, scope) {
    @Volatile
    private var explicitZone: ZoneId? = null

    init {
        scope.launch {
            profileRepository.observeProfile().collect { profile ->
                val parsed = profile?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                explicitZone = parsed
                updateZone(parsed ?: ZoneId.systemDefault())
            }
        }
    }

    override fun refresh() {
        // A traveling user with no explicit profile zone must follow the device
        // zone change when the app comes back to the foreground.
        if (explicitZone == null) updateZone(ZoneId.systemDefault())
        super.refresh()
    }
}
