package com.meucoach.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_check_ins")
data class DailyCheckInEntity(
    @PrimaryKey val date: String,
    val sleepQuality: Int,
    val fatigue: Int,
    val soreness: Int,
    val stress: Int,
    val illnessOrInjury: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
