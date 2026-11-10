package com.meucoach.core.domain.replan

import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Experience
import com.meucoach.core.model.GoalType
import com.meucoach.core.model.TrainingGoal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId

/**
 * Canonical projection of the training goal that can change the weekly plan.
 *
 * Only the supported race distances participate. General-fitness/custom goals
 * project to `null`, so switching between plans without an event never creates
 * a false T4. When C3-05 moves the source to `RaceEvent`, the same type,
 * distance and local date must produce this same projection.
 */
data class PlanningGoal(val type: GoalType, val targetDistanceM: Double? = null, val targetDate: LocalDate? = null) {
    init {
        require(type in SUPPORTED_TYPES) { "Unsupported goal type in planning projection: $type" }
        require(targetDistanceM == null || (targetDistanceM.isFinite() && targetDistanceM > 0.0)) {
            "Goal distance must be finite and positive"
        }
    }

    internal fun canonical(): String = buildString {
        append("type=").append(type.name)
        append(",distanceM=").append(targetDistanceM?.toString() ?: "null")
        append(",date=").append(targetDate?.toString() ?: "null")
    }

    companion object {
        val SUPPORTED_TYPES: Set<GoalType> = setOf(
            GoalType.FIVE_K,
            GoalType.TEN_K,
            GoalType.HALF_MARATHON,
            GoalType.MARATHON,
        )

        /** Projects a supported race goal; returns `null` when there is no event to plan around. */
        fun from(goal: TrainingGoal?): PlanningGoal? {
            val type = goal?.type ?: return null
            if (type !in SUPPORTED_TYPES) return null
            return PlanningGoal(
                type = type,
                targetDistanceM = goal.targetDistanceM,
                targetDate = goal.targetDate,
            )
        }
    }
}

/**
 * Minimal, versioned allowlist of profile fields that can change the week or
 * its targets (ADR 0031). Name, avatar, weight, height, sex, city and
 * coordinates are deliberately absent.
 */
data class PlanningProfileSnapshot(
    val experience: Experience,
    val thresholdPaceSecondsPerKm: Int?,
    val lactateThresholdHeartRate: Int?,
    val treadmillAccess: Boolean,
    val goal: PlanningGoal?,
) {
    internal fun canonical(): String = buildString {
        append("experience=").append(experience.name)
        append("|thresholdPaceSecPerKm=").append(thresholdPaceSecondsPerKm?.toString() ?: "null")
        append("|lactateThresholdHeartRateBpm=")
        append(lactateThresholdHeartRate?.toString() ?: "null")
        append("|treadmillAccess=").append(treadmillAccess)
        append("|goal=").append(goal?.canonical() ?: "null")
    }
}

/** A profile fingerprint plus the minimal snapshot that explains it. */
data class PlanningFingerprint(val version: String, val hash: String, val snapshot: PlanningProfileSnapshot) {
    init {
        require(version.isNotBlank()) { "Planning fingerprint version must not be blank" }
        require(hash.isNotBlank()) { "Planning fingerprint must not be blank" }
    }
}

/**
 * Deterministic planning fingerprint (ADR 0031).
 *
 * The version is part of the compared value: a format change requires a new
 * version and an explicit baseline policy. `null` is distinct from zero and
 * maps are never part of this fingerprint (availability has its own hash).
 */
object PlanningFingerprints {
    const val VERSION = "planning-v1"
    const val TEMPORAL_CONTEXT_VERSION = "planning-context-v1"

    fun of(profile: AthleteProfile): PlanningFingerprint {
        val snapshot = PlanningProfileSnapshot(
            experience = profile.experience,
            thresholdPaceSecondsPerKm = profile.thresholdPace?.secondsPerKm,
            lactateThresholdHeartRate = profile.lactateThresholdHeartRate,
            treadmillAccess = profile.treadmillAccess,
            goal = PlanningGoal.from(profile.goal),
        )
        val canonical = "$VERSION|${snapshot.canonical()}"
        return PlanningFingerprint(
            version = VERSION,
            hash = sha256(canonical),
            snapshot = snapshot,
        )
    }

    /**
     * Separates the timezone that defines `today`/week start from the athlete's
     * planning identity. A change invalidates a temporal evaluation without
     * changing the profile fingerprint.
     */
    fun temporalContextHash(zoneId: ZoneId): String = sha256("$TEMPORAL_CONTEXT_VERSION|zoneId=${zoneId.id}")

    fun temporalContextHash(profile: AthleteProfile): String = temporalContextHash(ZoneId.of(profile.timezone))
}

/** Everything a `NoChange` or accepted revision needs to persist as the evaluated checkpoint. */
data class PlanEvaluationCheckpoint(
    val fingerprintVersion: String,
    val fingerprint: String,
    val profileSnapshot: PlanningProfileSnapshot,
    val temporalContextHash: String,
    val availabilityHash: String,
) {
    init {
        require(fingerprintVersion.isNotBlank()) { "Checkpoint version must not be blank" }
        require(fingerprint.isNotBlank()) { "Checkpoint fingerprint must not be blank" }
        require(temporalContextHash.isNotBlank()) { "Temporal context hash must not be blank" }
        require(availabilityHash.isNotBlank()) { "Availability hash must not be blank" }
    }
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    val digits = "0123456789abcdef"
    val hex = CharArray(digest.size * 2)
    digest.forEachIndexed { index, byte ->
        val unsigned = byte.toInt() and 0xFF
        hex[index * 2] = digits[unsigned ushr 4]
        hex[index * 2 + 1] = digits[unsigned and 0x0F]
    }
    return String(hex)
}
