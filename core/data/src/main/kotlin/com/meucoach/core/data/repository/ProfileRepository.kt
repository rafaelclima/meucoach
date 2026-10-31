package com.meucoach.core.data.repository

import androidx.room.withTransaction
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.AthleteProfileEntity
import com.meucoach.core.data.profile.ProfileJson
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Pace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** Single local athlete profile, stored next to the activities. */
class ProfileRepository(private val database: MeuCoachDatabase, private val clock: Clock = Clock.systemUTC()) {
    private val dao = database.profileDao()

    fun observeProfile(): Flow<AthleteProfile?> = dao.observe().map { it?.toModel() }

    suspend fun getProfile(): AthleteProfile? = dao.get()?.toModel()

    suspend fun saveProfile(profile: AthleteProfile): AthleteProfile = database.withTransaction {
        val now = clock.millis()
        val existing = dao.get()
        val createdAt = existing?.takeIf { it.id == profile.id }?.createdAt ?: now
        // The profile is a singleton; replacing the row keeps the schema simple.
        dao.deleteAll()
        dao.upsert(profile.toEntity(createdAt = createdAt, updatedAt = now))
        profile
    }

    suspend fun deleteProfile(): Int = dao.deleteAll()

    private fun AthleteProfileEntity.toModel() = AthleteProfile(
        id = id,
        birthDate = LocalDate.parse(birthDate),
        sex = sex,
        heightCm = heightCm,
        weightKg = weightKg,
        restingHeartRate = restingHeartRate,
        maxHeartRateObserved = maxHeartRateObserved,
        lactateThresholdHeartRate = lactateThresholdHeartRate,
        thresholdPace = thresholdPaceSecPerKm?.let { Pace.fromSecondsPerKm(it) },
        vdot = vdot,
        experience = experience,
        timezone = timezone,
        weeklyAvailability = ProfileJson.decodeAvailability(availabilityJson),
        goal = ProfileJson.decodeGoal(goalJson),
        city = city,
        latitude = latitude,
        longitude = longitude,
        units = units,
        treadmillAccess = treadmillAccess,
    )

    private fun AthleteProfile.toEntity(createdAt: Long, updatedAt: Long) = AthleteProfileEntity(
        id = id,
        birthDate = birthDate.toString(),
        sex = sex,
        heightCm = heightCm,
        weightKg = weightKg,
        restingHeartRate = restingHeartRate,
        maxHeartRateObserved = maxHeartRateObserved,
        lactateThresholdHeartRate = lactateThresholdHeartRate,
        thresholdPaceSecPerKm = thresholdPace?.secondsPerKm,
        vdot = vdot,
        experience = experience,
        timezone = timezone,
        availabilityJson = ProfileJson.encodeAvailability(weeklyAvailability),
        goalJson = ProfileJson.encodeGoal(goal),
        city = city,
        latitude = latitude,
        longitude = longitude,
        units = units,
        treadmillAccess = treadmillAccess,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
