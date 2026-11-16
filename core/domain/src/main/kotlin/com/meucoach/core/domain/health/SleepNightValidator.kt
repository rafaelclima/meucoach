package com.meucoach.core.domain.health

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * One validated local night (ADR 0049 §2.4).
 *
 * [minutes] is `null` when the night is suspect: the data is absent, not a
 * short night. [fragmented] means more than one block survived the merge and
 * only the largest block was used.
 */
data class SleepNight(val date: LocalDate, val minutes: Int?, val fragmented: Boolean = false) {
    init {
        require(
            minutes == null || minutes in SleepNightValidator.MIN_VALID_MINUTES..SleepNightValidator.MAX_VALID_MINUTES,
        ) {
            "A valid night must fit the plausible range"
        }
    }

    val suspect: Boolean get() = minutes == null
}

/**
 * Validates the previous night instead of trusting every synced session.
 *
 * The window is `[previous 18:00, current 12:00]` local, so a daytime nap is
 * not part of the night. Sessions separated by up to two hours belong to the
 * same block; overlapping records are unioned and the awake gap is not counted
 * as sleep (4 h + 4 h with a 30-min gap is 8 h). If blocks remain apart, the
 * night is fragmented and the largest block wins: summing pieces would
 * overestimate sleep. Finally, anything outside 180..840 minutes is suspect
 * and becomes absent downstream.
 */
object SleepNightValidator {
    const val MIN_VALID_MINUTES = 180
    const val MAX_VALID_MINUTES = 840
    const val MERGE_GAP_MINUTES = 120L

    val NIGHT_START: LocalTime = LocalTime.of(18, 0)
    val NIGHT_END: LocalTime = LocalTime.of(12, 0)

    fun validate(date: LocalDate, sessions: List<HealthSleepSession>, zoneId: ZoneId): SleepNight {
        val windowStart = date.minusDays(1).atTime(NIGHT_START).atZone(zoneId).toInstant()
        val windowEnd = date.atTime(NIGHT_END).atZone(zoneId).toInstant()
        val mergeGap = Duration.ofMinutes(MERGE_GAP_MINUTES)

        val groups = mutableListOf<MutableList<HealthSleepSession>>()
        var groupEnd: Instant? = null
        sessions
            .asSequence()
            .filter { it.start.isBefore(windowEnd) && it.end.isAfter(windowStart) }
            .sortedBy { it.start }
            .forEach { session ->
                val openEnd = groupEnd
                if (openEnd != null && !session.start.isAfter(openEnd.plus(mergeGap))) {
                    groups.last() += session
                    if (session.end.isAfter(openEnd)) groupEnd = session.end
                } else {
                    groups += mutableListOf(session)
                    groupEnd = session.end
                }
            }

        val blockMinutes = groups.map(::unionMinutes)
        val largestMinutes = blockMinutes.maxOrNull() ?: 0L
        val valid = largestMinutes in MIN_VALID_MINUTES.toLong()..MAX_VALID_MINUTES.toLong()
        return SleepNight(
            date = date,
            minutes = if (valid) largestMinutes.toInt() else null,
            fragmented = groups.size > 1,
        )
    }

    /** Union of the sessions inside one block; overlapping records count once. */
    private fun unionMinutes(group: List<HealthSleepSession>): Long {
        val ordered = group.sortedBy { it.start }
        var blockStart = ordered.first().start
        var blockEnd = ordered.first().end
        var minutes = 0L
        ordered.drop(1).forEach { session ->
            if (session.start.isAfter(blockEnd)) {
                minutes += Duration.between(blockStart, blockEnd).toMinutes()
                blockStart = session.start
                blockEnd = session.end
            } else if (session.end.isAfter(blockEnd)) {
                blockEnd = session.end
            }
        }
        return minutes + Duration.between(blockStart, blockEnd).toMinutes()
    }
}
