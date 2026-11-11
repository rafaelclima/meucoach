package com.meucoach.app.ui.common

import com.meucoach.core.domain.race.RaceSplitProjection
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RACE_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu")

fun raceDistanceLabel(distance: RaceDistance): String = when (distance) {
    RaceDistance.FIVE_K -> "5 km"
    RaceDistance.TEN_K -> "10 km"
    RaceDistance.HALF_MARATHON -> "Meia maratona"
    RaceDistance.MARATHON -> "Maratona"
}

fun raceStatusLabel(status: RaceStatus): String = when (status) {
    RaceStatus.PLANNED -> "Planejada"
    RaceStatus.COMPLETED -> "Concluída"
    RaceStatus.CANCELLED -> "Cancelada"
    RaceStatus.NO_SHOW -> "Não compareceu"
}

fun raceDateLabel(date: LocalDate): String = date.format(RACE_DATE)

/** Declared goal only; never converted to pace, range or prediction. */
fun raceTargetTimeLabel(seconds: Int?): String? {
    if (seconds == null) return null
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val rest = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, rest)
    }
}

/** Arithmetic reference only (ADR 0032): always shown with the /km unit. */
fun racePaceLabel(secondsPerKm: Int): String = "${formatPaceSeconds(secondsPerKm)}/km"

/** Reference band derived from the declared goal; never a prescribed range. */
fun racePaceRangeLabel(range: IntRange): String {
    require(range.first > 0 && range.last >= range.first) { "Pace range must be positive and ordered" }
    return "${formatPaceSeconds(range.first)}–${formatPaceSeconds(range.last)}/km"
}

/** One projected passage; a fractional finish is labelled "Chegada". */
fun raceProjectionSplitLabel(split: RaceSplitProjection): String {
    val distance = if (split.isFinish && split.distanceMeters % 1_000 != 0) {
        "Chegada"
    } else {
        "${split.distanceMeters / 1_000} km"
    }
    return "$distance · ${raceTargetTimeLabel(split.cumulativeSec)}"
}

private fun formatPaceSeconds(seconds: Int): String = String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
