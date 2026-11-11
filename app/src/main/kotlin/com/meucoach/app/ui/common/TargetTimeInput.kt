package com.meucoach.app.ui.common

/** How the user chose to enter a declared race target time. */
enum class TargetTimeUnit {
    MINUTES,
    HOURS,
}

/**
 * Explicit time segments. The UI renders them as a mask (`mm:ss` or `h:mm:ss`)
 * so the athlete never has to type colons or guess the expected format.
 */
data class TargetTimeParts(val hours: String = "", val minutes: String = "", val seconds: String = "") {
    val isBlank: Boolean
        get() = hours.isBlank() && minutes.isBlank() && seconds.isBlank()
}

/** Splits a persisted target into the fields of the selected unit. */
fun targetTimeParts(seconds: Int?, unit: TargetTimeUnit): TargetTimeParts {
    if (seconds == null) return TargetTimeParts()
    return when (unit) {
        TargetTimeUnit.MINUTES -> TargetTimeParts(
            minutes = (seconds / 60).toString(),
            seconds = (seconds % 60).toString().padStart(2, '0'),
        )

        TargetTimeUnit.HOURS -> TargetTimeParts(
            hours = (seconds / 3_600).toString(),
            minutes = ((seconds % 3_600) / 60).toString().padStart(2, '0'),
            seconds = (seconds % 60).toString().padStart(2, '0'),
        )
    }
}

/** Converts the current fields when the athlete switches unit. */
fun TargetTimeParts.convert(from: TargetTimeUnit, to: TargetTimeUnit): TargetTimeParts {
    if (from == to) return this
    val totalSeconds = totalSeconds(from) ?: return TargetTimeParts()
    return targetTimeParts(totalSeconds, to)
}

/**
 * Total declared seconds. Returns `null` for blank or impossible segments
 * (minutes/seconds above 59), so the form can distinguish "optional" from
 * "invalid". The caller still rejects a zero total.
 */
fun TargetTimeParts.totalSeconds(unit: TargetTimeUnit): Int? {
    if (isBlank) return null
    val parsedMinutes = minutes.toIntOrNull() ?: if (minutes.isBlank()) 0 else return null
    val parsedSeconds = seconds.toIntOrNull() ?: if (seconds.isBlank()) 0 else return null
    if (parsedSeconds !in 0..59) return null
    return when (unit) {
        TargetTimeUnit.MINUTES -> parsedMinutes * 60 + parsedSeconds

        TargetTimeUnit.HOURS -> {
            val parsedHours = hours.toIntOrNull() ?: if (hours.isBlank()) 0 else return null
            if (parsedMinutes !in 0..59) return null
            parsedHours * 3_600 + parsedMinutes * 60 + parsedSeconds
        }
    }
}

fun TargetTimeUnit.maskLabel(): String = when (this) {
    TargetTimeUnit.MINUTES -> "mm:ss"
    TargetTimeUnit.HOURS -> "h:mm:ss"
}

fun TargetTimeUnit.unitLabel(): String = when (this) {
    TargetTimeUnit.MINUTES -> "Minutos"
    TargetTimeUnit.HOURS -> "Horas"
}

/** Unit a persisted target opens with: hours only when the value needs them. */
fun targetTimeUnitFor(seconds: Int?): TargetTimeUnit =
    if ((seconds ?: 0) >= 3_600) TargetTimeUnit.HOURS else TargetTimeUnit.MINUTES
