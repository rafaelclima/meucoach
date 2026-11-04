package com.meucoach.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

enum class Sex {
    FEMALE,
    MALE,
    OTHER,
    UNSPECIFIED,
}

enum class Experience {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
}

enum class Units {
    METRIC,
}

enum class GoalType {
    GENERAL_FITNESS,
    FIVE_K,
    TEN_K,
    HALF_MARATHON,
    MARATHON,
    CUSTOM,
}

/** Planned running minutes per weekday. Missing days mean "not available". */
data class WeeklyAvailability(val minutesByDay: Map<DayOfWeek, Int> = emptyMap()) {
    init {
        require(minutesByDay.values.all { it in 1..MAX_MINUTES_PER_DAY }) {
            "Availability must be between 1 and $MAX_MINUTES_PER_DAY minutes per day"
        }
    }

    val totalMinutes: Int get() = minutesByDay.values.sum()

    fun minutesOn(day: DayOfWeek): Int = minutesByDay[day] ?: 0

    private companion object {
        const val MAX_MINUTES_PER_DAY = 1_440
    }
}

data class TrainingGoal(
    val type: GoalType = GoalType.GENERAL_FITNESS,
    val targetDate: LocalDate? = null,
    val targetDistanceM: Double? = null,
    val targetTimeSec: Int? = null,
) {
    init {
        require(targetDistanceM == null || (targetDistanceM.isFinite() && targetDistanceM > 0.0)) {
            "Goal distance must be finite and positive"
        }
        require(targetTimeSec == null || targetTimeSec > 0) { "Goal time must be positive" }
    }
}

data class AthleteProfile(
    val id: String,
    val birthDate: LocalDate,
    val sex: Sex,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val restingHeartRate: Int? = null,
    val maxHeartRateObserved: Int? = null,
    val lactateThresholdHeartRate: Int? = null,
    val thresholdPace: Pace? = null,
    val vdot: Double? = null,
    val experience: Experience = Experience.BEGINNER,
    val timezone: String = "UTC",
    val weeklyAvailability: WeeklyAvailability = WeeklyAvailability(),
    val goal: TrainingGoal? = null,
    val city: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val units: Units = Units.METRIC,
    /** Athlete has access to a treadmill, which is a valid modality in heat (`R-PLAN-08`). */
    val treadmillAccess: Boolean = false,
    val name: String? = null,
    /** Local path of the imported profile photo (app-private storage). */
    val avatarPath: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Athlete id must not be blank" }
        require(heightCm == null || heightCm > 0.0) { "Height must be positive" }
        require(weightKg == null || weightKg > 0.0) { "Weight must be positive" }
        require(restingHeartRate == null || restingHeartRate in 30..120) {
            "Resting heart rate out of range"
        }
        require(maxHeartRateObserved == null || maxHeartRateObserved in 100..250) {
            "Max heart rate out of range"
        }
        require(lactateThresholdHeartRate == null || lactateThresholdHeartRate in 100..230) {
            "Lactate threshold heart rate out of range"
        }
        require(vdot == null || vdot > 0.0) { "VDOT must be positive" }
        require(runCatching { ZoneId.of(timezone) }.isSuccess) {
            "Timezone must be a valid IANA zone"
        }
        require(city == null || city.isNotBlank()) { "City must not be blank" }
        require(latitude == null || latitude in -90.0..90.0) { "Latitude out of range" }
        require(longitude == null || longitude in -180.0..180.0) { "Longitude out of range" }
    }
}
