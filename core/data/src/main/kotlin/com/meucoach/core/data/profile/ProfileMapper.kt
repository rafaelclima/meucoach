package com.meucoach.core.data.profile

import com.meucoach.core.data.db.entity.AthleteProfileEntity
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Pace
import java.time.LocalDate

/**
 * Single mapping between the persisted profile and the domain model.
 *
 * It lives outside `ProfileRepository` so a transaction that must re-read the
 * current profile (for example the T4 compare-and-apply) does not go through a
 * second repository/transaction (ADR 0031).
 */
internal fun AthleteProfileEntity.toModel(): AthleteProfile = AthleteProfile(
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
    vdotSource = vdotSource,
    vdotCalibratedAt = vdotCalibratedAt?.let { LocalDate.parse(it) },
    vdotReferenceDistanceM = vdotReferenceDistanceM,
    vdotReferenceDurationSec = vdotReferenceDurationSec,
    experience = experience,
    timezone = timezone,
    weeklyAvailability = ProfileJson.decodeAvailability(availabilityJson),
    goal = ProfileJson.decodeGoal(goalJson),
    city = city,
    latitude = latitude,
    longitude = longitude,
    units = units,
    treadmillAccess = treadmillAccess,
    name = name,
    avatarPath = avatarPath,
)

internal fun AthleteProfile.toEntity(createdAt: Long, updatedAt: Long): AthleteProfileEntity = AthleteProfileEntity(
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
    vdotSource = vdotSource,
    vdotCalibratedAt = vdotCalibratedAt?.toString(),
    vdotReferenceDistanceM = vdotReferenceDistanceM,
    vdotReferenceDurationSec = vdotReferenceDurationSec,
    experience = experience,
    timezone = timezone,
    availabilityJson = ProfileJson.encodeAvailability(weeklyAvailability),
    goalJson = ProfileJson.encodeGoal(goal),
    city = city,
    latitude = latitude,
    longitude = longitude,
    units = units,
    treadmillAccess = treadmillAccess,
    name = name,
    avatarPath = avatarPath,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
