package com.meucoach.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.meucoach.core.model.DailyMetricsSource

@Entity(tableName = "daily_metrics")
data class DailyMetricsEntity(
    @PrimaryKey val date: String,
    val sleepDurationMin: Int?,
    val restingHeartRate: Double?,
    val hrvRmssdMs: Double?,
    val spo2Avg: Double?,
    val source: DailyMetricsSource,
    val createdAt: Long,
    val updatedAt: Long,
)
