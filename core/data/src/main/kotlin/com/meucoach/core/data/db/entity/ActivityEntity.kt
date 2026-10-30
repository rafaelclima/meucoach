package com.meucoach.core.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.Sport

/**
 * Persisted activity aggregate. Samples live in [SampleBlobEntity]; laps are
 * stored separately to keep list queries cheap.
 */
@Entity(
    tableName = "activities",
    indices = [
        Index("startTimeUtc"),
        Index("sport", "startTimeUtc"),
        Index(value = ["sourceFileSha256"], unique = true),
    ],
)
data class ActivityEntity(
    @PrimaryKey val id: String,
    val source: ActivitySource,
    val sourceId: String?,
    val sourceFileSha256: String?,
    val sport: Sport,
    val subSport: String?,
    val startTimeUtc: Long,
    val zoneId: String,
    val localDate: String,
    val durationSec: Int,
    val movingTimeSec: Int?,
    val distanceM: Double,
    val averagePaceSecPerKm: Double?,
    val bestPaceSecPerKm: Double?,
    val averageHeartRate: Int?,
    val maxHeartRate: Int?,
    val averageCadenceSpm: Int?,
    val maxCadenceSpm: Int?,
    val averagePowerW: Int?,
    val maxPowerW: Int?,
    val averageStepLengthM: Double?,
    val elevationGainM: Double?,
    val elevationLossM: Double?,
    val calories: Int?,
    val trainingEffectAerobic: Double?,
    val trainingEffectAnaerobic: Double?,
    val trainingLoadPeak: Double?,
    val deviceName: String?,
    val rawFilePath: String?,
    val parserVersion: String,
    val importedAt: Long,
    val deletedAt: Long? = null,
)
