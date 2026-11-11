package com.meucoach.app.ui.calibration

import com.meucoach.app.ui.common.DateMask
import com.meucoach.app.ui.common.TargetTimeParts
import com.meucoach.app.ui.common.TargetTimeUnit
import com.meucoach.app.ui.common.totalSeconds
import com.meucoach.core.domain.calibration.CalibrationPerformance
import com.meucoach.core.model.ActivitySummary
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

data class CalibrationForm(
    val distanceKm: String = "5",
    val timeParts: TargetTimeParts = TargetTimeParts(),
    val timeUnit: TargetTimeUnit = TargetTimeUnit.MINUTES,
    val date: String = "",
    val averageHeartRate: String = "",
)

data class CalibrationFormErrors(
    val distance: String? = null,
    val time: String? = null,
    val date: String? = null,
    val heartRate: String? = null,
) {
    val isValid: Boolean
        get() = distance == null && time == null && date == null && heartRate == null
}

object CalibrationFormValidator {
    fun validate(form: CalibrationForm, today: LocalDate): CalibrationFormErrors {
        val distance = form.distanceKm.trim().replace(',', '.').toDoubleOrNull()
        val distanceError = when {
            distance == null -> "Informe a distância em km."
            distance !in 1.0..100.0 -> "Distância entre 1 e 100 km."
            else -> null
        }

        val duration = form.timeParts.totalSeconds(form.timeUnit)
        val timeError = when {
            form.timeParts.isBlank -> missingTimeMessage(form.timeUnit)
            duration == null -> missingTimeMessage(form.timeUnit)
            duration <= 0 -> "O tempo precisa ser maior que zero."
            duration !in MIN_EFFORT_SEC..MAX_EFFORT_SEC -> "Tempo entre 8 e 180 minutos."
            else -> null
        }

        val date = DateMask.parse(form.date)
        val dateError = when {
            date == null -> "Use dd/mm/aaaa."
            date.isAfter(today) -> "A data não pode estar no futuro."
            else -> null
        }

        val heartRate = form.averageHeartRate.trim().toIntOrNull()
        val heartRateError = when {
            form.averageHeartRate.isBlank() -> null
            heartRate == null || heartRate !in 30..250 -> "FC média entre 30 e 250 bpm."
            else -> null
        }

        return CalibrationFormErrors(
            distance = distanceError,
            time = timeError,
            date = dateError,
            heartRate = heartRateError,
        )
    }

    fun toPerformance(form: CalibrationForm, today: LocalDate): CalibrationPerformance {
        require(validate(form, today).isValid) { "Calibration form has invalid fields" }
        val distanceKm = requireNotNull(form.distanceKm.trim().replace(',', '.').toDoubleOrNull())
        return CalibrationPerformance(
            distanceM = distanceKm * 1_000.0,
            durationSec = requireNotNull(form.timeParts.totalSeconds(form.timeUnit)),
            date = requireNotNull(DateMask.parse(form.date)),
            averageHeartRate = form.averageHeartRate.trim().toIntOrNull(),
        )
    }

    fun formatTime(seconds: Int): String {
        val hours = seconds / 3_600
        val minutes = (seconds % 3_600) / 60
        val rest = seconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, rest)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, rest)
        }
    }

    private fun missingTimeMessage(unit: TargetTimeUnit): String = when (unit) {
        TargetTimeUnit.MINUTES -> "Informe minutos e segundos (0 a 59)."
        TargetTimeUnit.HOURS -> "Informe horas, minutos e segundos (0 a 59)."
    }

    private const val MIN_EFFORT_SEC = 8 * 60
    private const val MAX_EFFORT_SEC = 180 * 60
}

/** Counts activities within the last 8 weeks that have both heart rate and pace. */
fun recentEffortCount(activities: List<ActivitySummary>, zoneId: ZoneId, today: LocalDate): Int =
    activities.count { activity ->
        if (activity.averageHeartRate == null || activity.averagePace == null) return@count false
        val date = activity.startTime.atZone(zoneId).toLocalDate()
        val days = ChronoUnit.DAYS.between(date, today)
        days in 0..RECENT_DAYS
    }

private const val RECENT_DAYS = 56L
