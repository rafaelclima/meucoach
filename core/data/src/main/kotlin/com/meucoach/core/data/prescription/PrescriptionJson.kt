package com.meucoach.core.data.prescription

import com.meucoach.core.domain.prescription.HeartRateTarget
import com.meucoach.core.domain.prescription.PaceTarget
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.model.Pace
import org.json.JSONArray
import org.json.JSONObject

/** JSON round-trip for prescription targets and ID lists inside Room columns. */
internal object PrescriptionJson {
    fun targetsToJson(targets: PrescriptionTargets): String {
        val root = JSONObject()
        targets.pace?.let { pace ->
            root.put("paceFastSecPerKm", pace.fast.secondsPerKm)
            root.put("paceSlowSecPerKm", pace.slow.secondsPerKm)
        }
        targets.heartRate?.let { heartRate ->
            root.put("heartRateMinBpm", heartRate.minBpm)
            heartRate.maxBpm?.let { root.put("heartRateMaxBpm", it) }
        }
        targets.zone?.let { root.put("zone", it) }
        targets.distanceKm?.let { root.put("distanceKm", it) }
        targets.rpe?.let { root.put("rpe", it) }
        return root.toString()
    }

    fun targetsFromJson(json: String?): PrescriptionTargets {
        if (json.isNullOrBlank()) return PrescriptionTargets()
        val root = runCatching { JSONObject(json) }.getOrElse { return PrescriptionTargets() }
        val fast = root.optNullableInt("paceFastSecPerKm")
        val slow = root.optNullableInt("paceSlowSecPerKm")
        val pace = if (fast != null && slow != null && fast > 0 && slow >= fast) {
            PaceTarget(
                fast = Pace.fromSecondsPerKm(fast),
                slow = Pace.fromSecondsPerKm(slow),
            )
        } else {
            null
        }
        val minBpm = root.optNullableInt("heartRateMinBpm")
        val maxBpm = root.optNullableInt("heartRateMaxBpm")
        val heartRate = if (minBpm != null && (maxBpm == null || maxBpm >= minBpm)) {
            HeartRateTarget(minBpm = minBpm, maxBpm = maxBpm)
        } else {
            null
        }
        val distance = if (root.has("distanceKm") && !root.isNull("distanceKm")) {
            root.getDouble("distanceKm")
        } else {
            null
        }
        return PrescriptionTargets(
            pace = pace,
            heartRate = heartRate,
            zone = if (root.has("zone") && !root.isNull("zone")) root.getString("zone") else null,
            distanceKm = distance,
            rpe = root.optNullableInt("rpe"),
        )
    }

    fun idsToJson(ids: List<String>): String = JSONArray(ids).toString()

    fun idsFromJson(json: String): List<String> {
        val array = runCatching { JSONArray(json) }.getOrElse { return emptyList() }
        return List(array.length()) { index -> array.getString(index) }
    }

    private fun JSONObject.optNullableInt(name: String): Int? = if (has(name) && !isNull(name)) getInt(name) else null
}
