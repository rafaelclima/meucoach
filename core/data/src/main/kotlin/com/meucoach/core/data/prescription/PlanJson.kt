package com.meucoach.core.data.prescription

import com.meucoach.core.data.db.entity.PlannedSessionEntity
import com.meucoach.core.data.db.entity.WeeklyPlanEntity
import com.meucoach.core.data.db.entity.WeeklyPlanRevisionEntity
import com.meucoach.core.domain.prescription.SessionClosureReason
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.CompletionEvidence
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Serialization of plans and plan revisions. The same shape is used by the
 * whole-device export and by the append-only revision snapshot, so a revision
 * can always be explained with the same fields the user can export.
 */
internal object PlanJson {
    fun planMetadataToJson(plan: WeeklyPlanEntity): JSONObject = JSONObject().apply {
        put("weekStart", plan.weekStart)
        put("createdAt", Instant.ofEpochMilli(plan.createdAt).toString())
        put("createdOn", plan.createdOn)
        put("confidence", plan.confidence.name)
        put("rationale", plan.rationale)
        put("weeklyMinutes", plan.weeklyMinutes)
        putNullable("longRunMinutes", plan.longRunMinutes)
        put("qualityCount", plan.qualityCount)
        put("ruleIds", JSONArray(plan.ruleIdsJson))
        put("evidenceIds", JSONArray(plan.evidenceIdsJson))
        put("version", plan.version)
        putNullable("revisionReason", plan.revisionReason)
        putNullable("revisedAt", plan.revisedAt?.let { Instant.ofEpochMilli(it).toString() })
        putNullable("inputsHash", plan.inputsHash)
        putNullable("availabilityHash", plan.availabilityHash)
        putNullable("generatedFromFingerprintVersion", plan.generatedFromFingerprintVersion)
        putNullable("generatedFromFingerprint", plan.generatedFromFingerprint)
        putNullable("generatedFromProfileSnapshotJson", plan.generatedFromProfileSnapshotJson)
        putNullable("lastEvaluatedFingerprintVersion", plan.lastEvaluatedFingerprintVersion)
        putNullable("lastEvaluatedFingerprint", plan.lastEvaluatedFingerprint)
        putNullable("temporalContextHash", plan.temporalContextHash)
    }

    fun weeklyPlanToJson(plan: WeeklyPlanEntity, sessions: List<PlannedSessionEntity>): JSONObject =
        planMetadataToJson(plan).apply {
            put("sessions", sessions.toJsonArray { sessionToJson(it) })
        }

    fun revisionToJson(revision: WeeklyPlanRevisionEntity): JSONObject = JSONObject().apply {
        put("id", revision.id)
        put("weekStart", revision.weekStart)
        put("fromVersion", revision.fromVersion)
        put("toVersion", revision.toVersion)
        putNullable("fromInputsHash", revision.fromInputsHash)
        put("toInputsHash", revision.toInputsHash)
        put("fromAvailabilityHash", revision.fromAvailabilityHash)
        put("toAvailabilityHash", revision.toAvailabilityHash)
        putNullable("fromFingerprintVersion", revision.fromFingerprintVersion)
        putNullable("fromFingerprint", revision.fromFingerprint)
        putNullable("fromProfileSnapshotJson", revision.fromProfileSnapshotJson)
        putNullable("fromTemporalContextHash", revision.fromTemporalContextHash)
        putNullable("toFingerprintVersion", revision.toFingerprintVersion)
        putNullable("toFingerprint", revision.toFingerprint)
        putNullable("toProfileSnapshotJson", revision.toProfileSnapshotJson)
        putNullable("toTemporalContextHash", revision.toTemporalContextHash)
        put("generatorVersion", revision.generatorVersion)
        put("previousPlan", runCatching { JSONObject(revision.previousPlanJson) }.getOrElse { JSONObject() })
        put(
            "replacedSessions",
            runCatching { JSONArray(revision.replacedSessionsJson) }.getOrElse { JSONArray() },
        )
        put("trigger", revision.trigger)
        put("reason", revision.reason)
        put("ruleIds", JSONArray(revision.ruleIdsJson))
        put("evidenceIds", JSONArray(revision.evidenceIdsJson))
        put("confidence", revision.confidence.name)
        put("createdAt", Instant.ofEpochMilli(revision.createdAt).toString())
    }

    fun sessionToJson(session: PlannedSessionEntity): JSONObject = JSONObject().apply {
        put("id", session.id)
        put("date", session.date)
        put("type", session.type.name)
        put("title", session.title)
        putNullable("durationMin", session.durationMin)
        put("rationale", session.rationale)
        put("ruleIds", JSONArray(session.ruleIdsJson))
        put("evidenceIds", JSONArray(session.evidenceIdsJson))
        put("targets", JSONObject(session.targetsJson))
        put("status", session.status.name)
        putNullable("skipReason", session.skipReason)
        putNullable("completedAt", session.completedAt?.let { Instant.ofEpochMilli(it).toString() })
        putNullable("completionEvidence", session.completionEvidence?.name)
        putNullable("executionId", session.executionId)
        putNullable("activityId", session.activityId)
        putNullable("closureReason", session.closureReason?.name)
        putNullable("closedAt", session.closedAt?.let { Instant.ofEpochMilli(it).toString() })
    }

    fun sessionsToJson(sessions: List<PlannedSessionEntity>): String =
        sessions.toJsonArray { sessionToJson(it) }.toString()

    /**
     * Rebuilds the snapshot sessions of a revision. The week is passed by the
     * caller because the session JSON only carries the deterministic id.
     */
    fun sessionsFromJson(value: String, weekStart: String): List<PlannedSessionEntity> {
        val array = runCatching { JSONArray(value) }.getOrElse { return emptyList() }
        return List(array.length()) { index -> sessionFromJson(array.getJSONObject(index), weekStart) }
    }

    private fun sessionFromJson(json: JSONObject, weekStart: String) = PlannedSessionEntity(
        id = json.getString("id"),
        weekStart = weekStart,
        date = json.getString("date"),
        type = WorkoutType.valueOf(json.getString("type")),
        title = json.getString("title"),
        durationMin = if (json.isNull("durationMin")) null else json.getInt("durationMin"),
        rationale = json.getString("rationale"),
        ruleIdsJson = json.getJSONArray("ruleIds").toString(),
        evidenceIdsJson = json.getJSONArray("evidenceIds").toString(),
        targetsJson = json.getJSONObject("targets").toString(),
        status = SessionStatus.valueOf(json.getString("status")),
        skipReason = json.optNullableString("skipReason"),
        completedAt = json.optNullableString("completedAt")?.let { Instant.parse(it).toEpochMilli() },
        completionEvidence = json.optNullableString("completionEvidence")?.let(CompletionEvidence::valueOf),
        executionId = json.optNullableString("executionId"),
        activityId = json.optNullableString("activityId"),
        closureReason = json.optNullableString("closureReason")?.let(SessionClosureReason::valueOf),
        closedAt = json.optNullableString("closedAt")?.let { Instant.parse(it).toEpochMilli() },
    )

    private fun <T> List<T>.toJsonArray(toJson: (T) -> JSONObject): JSONArray {
        val array = JSONArray()
        forEach { array.put(toJson(it)) }
        return array
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
