package com.meucoach.core.model

import java.time.LocalDate

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
    }
}
