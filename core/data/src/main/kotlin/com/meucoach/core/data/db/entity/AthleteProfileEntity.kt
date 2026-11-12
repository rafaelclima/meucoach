package com.meucoach.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Sex
import com.meucoach.core.model.Units
import com.meucoach.core.model.VdotSource

@Entity(tableName = "athlete_profile")
data class AthleteProfileEntity(
    @PrimaryKey val id: String,
    val birthDate: String,
    val sex: Sex,
    val heightCm: Double?,
    val weightKg: Double?,
    val restingHeartRate: Int?,
    val maxHeartRateObserved: Int?,
    val lactateThresholdHeartRate: Int?,
    val thresholdPaceSecPerKm: Int?,
    val vdot: Double?,
    val vdotSource: VdotSource? = null,
    val vdotCalibratedAt: String? = null,
    val vdotReferenceDistanceM: Double? = null,
    val vdotReferenceDurationSec: Int? = null,
    val experience: Experience,
    val timezone: String,
    val availabilityJson: String,
    val goalJson: String?,
    val city: String?,
    val latitude: Double?,
    val longitude: Double?,
    val units: Units,
    val treadmillAccess: Boolean,
    val name: String?,
    val avatarPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
