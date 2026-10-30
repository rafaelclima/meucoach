package com.meucoach.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "laps",
    primaryKeys = ["activityId", "lapIndex"],
    foreignKeys = [
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LapEntity(
    val activityId: String,
    val lapIndex: Int,
    val startTimeUtc: Long?,
    val durationSec: Int,
    val distanceM: Double,
    val averageHeartRate: Int?,
    val maxHeartRate: Int?,
    val averagePaceSecPerKm: Double?,
    val averageCadenceSpm: Int?,
    val averagePowerW: Int?,
    val elevationGainM: Double?,
)
