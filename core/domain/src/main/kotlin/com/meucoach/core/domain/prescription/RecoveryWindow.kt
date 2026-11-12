package com.meucoach.core.domain.prescription

import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.RaceStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A race the athlete actually ran, used to derive a recovery window (ADR 0037).
 *
 * [raceDate] is the event's local target date, never the moment the completion was
 * confirmed: an import resolved two days later must not shift the window.
 */
data class CompletedRace(val eventId: String, val distance: RaceDistance, val raceDate: LocalDate) {
    init {
        require(eventId.isNotBlank()) { "Completed race event id must not be blank" }
    }

    companion object {
        /** Only a completed event starts a recovery window (ADR 0030/0037). */
        fun from(event: RaceEvent): CompletedRace? = if (event.status == RaceStatus.COMPLETED) {
            CompletedRace(event.id, event.distance, event.targetDate)
        } else {
            null
        }
    }
}

/**
 * Post-race recovery window (C3-07 / FR-207, ADR 0037).
 *
 * The window is a product convention classified as `LIMITED`: after a completed
 * race the athlete keeps short easy sessions, with no quality and no long run for
 * a distance-dependent number of days. It never increases load and never creates a
 * session; it only caps what the plan and the day's prescription may prescribe.
 *
 * Day numbering starts at 1, the day after [CompletedRace.raceDate]. The race day
 * itself (0) is not part of the window: it is a competition day with no training
 * prescription.
 */
data class RecoveryWindow(val race: CompletedRace) {

    /** Number of days the window stays active after the race. */
    val totalDays: Int
        get() = when (race.distance) {
            RaceDistance.FIVE_K -> 2
            RaceDistance.TEN_K -> 3
            RaceDistance.HALF_MARATHON -> 5
            RaceDistance.MARATHON -> 10
        }

    /** Days since the race: 0 on the race day, 1 on the first recovery day. */
    fun dayNumber(date: LocalDate): Long = ChronoUnit.DAYS.between(race.raceDate, date)

    fun isActive(date: LocalDate): Boolean = dayNumber(date) in 1..totalDays.toLong()

    /** Inclusive "day N of M" shown to the athlete; 0 when outside the window. */
    fun dayOfWindow(date: LocalDate): Int = if (isActive(date)) dayNumber(date).toInt() else 0

    fun daysRemaining(date: LocalDate): Int = if (isActive(date)) totalDays - dayNumber(date).toInt() + 1 else 0

    fun allowsQuality(date: LocalDate): Boolean = !isActive(date)

    fun allowsLongRun(date: LocalDate): Boolean = !isActive(date)

    /**
     * Upper bound for a recovery session on [date], or `null` when the date is
     * outside the window. The bound never increases a session.
     */
    fun maxSessionMinutes(date: LocalDate): Int? {
        if (!isActive(date)) return null
        val day = dayNumber(date).toInt()
        return when (race.distance) {
            RaceDistance.FIVE_K, RaceDistance.TEN_K -> SHORT_PHASE_MINUTES

            RaceDistance.HALF_MARATHON ->
                if (day <= HALF_FIRST_PHASE_DAYS) SHORT_PHASE_MINUTES else MEDIUM_PHASE_MINUTES

            RaceDistance.MARATHON ->
                if (day <= MARATHON_FIRST_PHASE_DAYS) SHORT_PHASE_MINUTES else LONG_PHASE_MINUTES
        }
    }

    /** True when a planned session violates the window on its date. */
    fun blocks(type: WorkoutType, date: LocalDate, durationMin: Int?): Boolean {
        if (!isActive(date)) return false
        if (type == WorkoutType.QUALITY && !allowsQuality(date)) return true
        if (type == WorkoutType.LONG && !allowsLongRun(date)) return true
        val cap = maxSessionMinutes(date) ?: return false
        return durationMin != null && durationMin > cap
    }

    /** True when any day between [start] and [end] (inclusive) is inside the window. */
    fun intersects(start: LocalDate, end: LocalDate): Boolean {
        if (end.isBefore(start)) return false
        val first = race.raceDate.plusDays(1)
        val last = endDate()
        return !end.isBefore(first) && !start.isAfter(last)
    }

    /** Last day of the window, inclusive. */
    fun endDate(): LocalDate = race.raceDate.plusDays(totalDays.toLong())

    companion object {
        const val SHORT_PHASE_MINUTES = 30
        const val MEDIUM_PHASE_MINUTES = 45
        const val LONG_PHASE_MINUTES = 60
        const val HALF_FIRST_PHASE_DAYS = 3
        const val MARATHON_FIRST_PHASE_DAYS = 5

        /**
         * The window that governs [date], if any. When two completed races overlap,
         * the one that ends last wins, because it is the most conservative.
         */
        fun active(races: List<CompletedRace>, date: LocalDate): RecoveryWindow? = races
            .map(::RecoveryWindow)
            .filter { it.isActive(date) }
            .maxByOrNull { it.endDate() }

        /**
         * The window that intersects [start]..[end], if any, choosing the one that
         * ends last. Used to resolve the window that applies to a plan week.
         */
        fun covering(races: List<CompletedRace>, start: LocalDate, end: LocalDate): RecoveryWindow? = races
            .map(::RecoveryWindow)
            .filter { it.intersects(start, end) }
            .maxByOrNull { it.endDate() }
    }
}
