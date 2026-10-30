package com.meucoach.core.model

import java.time.LocalDate

enum class DailyMetricsSource {
    CSV,
    HEALTH_CONNECT,
    MANUAL,
}

data class DailyMetrics(
    val date: LocalDate,
    val sleepDurationMin: Int? = null,
    val restingHeartRate: Double? = null,
    val hrvRmssdMs: Double? = null,
    val spo2Avg: Double? = null,
    val source: DailyMetricsSource = DailyMetricsSource.MANUAL,
) {
    init {
        require(sleepDurationMin == null || sleepDurationMin in 0..1_440) {
            "Sleep duration must be between 0 and 1440 minutes"
        }
        require(restingHeartRate == null || restingHeartRate in 30.0..120.0) {
            "Resting heart rate out of range"
        }
        require(hrvRmssdMs == null || hrvRmssdMs > 0.0) { "HRV must be positive" }
        require(spo2Avg == null || spo2Avg in 50.0..100.0) { "SpO2 out of range" }
    }

    val hasAnyMetric: Boolean
        get() = sleepDurationMin != null ||
            restingHeartRate != null ||
            hrvRmssdMs != null ||
            spo2Avg != null
}
