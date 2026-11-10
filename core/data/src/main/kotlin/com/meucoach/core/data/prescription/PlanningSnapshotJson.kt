package com.meucoach.core.data.prescription

import com.meucoach.core.domain.replan.PlanningGoal
import com.meucoach.core.domain.replan.PlanningProfileSnapshot
import com.meucoach.core.model.Experience
import com.meucoach.core.model.GoalType
import org.json.JSONObject
import java.time.LocalDate

/**
 * Serializes the minimal profile allowlist of ADR 0031.
 *
 * The snapshot never carries name, avatar, weight, height, sex, city or
 * coordinates. It is stored for audit/export only; the fingerprint itself is
 * the value compared by T4.
 */
internal object PlanningSnapshotJson {
    fun toJson(snapshot: PlanningProfileSnapshot): String = JSONObject().apply {
        put("version", SNAPSHOT_VERSION)
        put("experience", snapshot.experience.name)
        putNullable("thresholdPaceSecPerKm", snapshot.thresholdPaceSecondsPerKm)
        putNullable("lactateThresholdHeartRateBpm", snapshot.lactateThresholdHeartRate)
        put("treadmillAccess", snapshot.treadmillAccess)
        put(
            "goal",
            snapshot.goal?.let { goal ->
                JSONObject().apply {
                    put("type", goal.type.name)
                    putNullable("distanceM", goal.targetDistanceM)
                    putNullable("date", goal.targetDate?.toString())
                }
            } ?: JSONObject.NULL,
        )
    }.toString()

    fun fromJson(value: String?): PlanningProfileSnapshot? {
        if (value.isNullOrBlank()) return null
        val json = runCatching { JSONObject(value) }.getOrNull() ?: return null
        val goal = json.optJSONObject("goal")?.let { goalJson ->
            PlanningGoal(
                type = GoalType.valueOf(goalJson.getString("type")),
                targetDistanceM = if (goalJson.isNull("distanceM")) null else goalJson.getDouble("distanceM"),
                targetDate = if (goalJson.isNull("date")) null else LocalDate.parse(goalJson.getString("date")),
            )
        }
        return PlanningProfileSnapshot(
            experience = Experience.valueOf(json.getString("experience")),
            thresholdPaceSecondsPerKm = if (json.isNull("thresholdPaceSecPerKm")) {
                null
            } else {
                json.getInt("thresholdPaceSecPerKm")
            },
            lactateThresholdHeartRate = if (json.isNull("lactateThresholdHeartRateBpm")) {
                null
            } else {
                json.getInt("lactateThresholdHeartRateBpm")
            },
            treadmillAccess = json.getBoolean("treadmillAccess"),
            goal = goal,
        )
    }

    private const val SNAPSHOT_VERSION = "planning-snapshot-v1"

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }
}
