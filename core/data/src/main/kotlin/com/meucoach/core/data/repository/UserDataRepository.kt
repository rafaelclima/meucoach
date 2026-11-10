package com.meucoach.core.data.repository

import androidx.room.withTransaction
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.ActivityAlternateEntity
import com.meucoach.core.data.db.entity.ActivityEntity
import com.meucoach.core.data.db.entity.AthleteProfileEntity
import com.meucoach.core.data.db.entity.DailyCheckInEntity
import com.meucoach.core.data.db.entity.DailyMetricsEntity
import com.meucoach.core.data.db.entity.DailyPrescriptionEntity
import com.meucoach.core.data.db.entity.ImportJobEntity
import com.meucoach.core.data.db.entity.LapEntity
import com.meucoach.core.data.db.entity.RaceEventEntity
import com.meucoach.core.data.db.entity.SampleBlobEntity
import com.meucoach.core.data.db.entity.SessionExecutionEntity
import com.meucoach.core.data.health.HealthSyncStore
import com.meucoach.core.data.prescription.PlanJson
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.time.Instant

/** How much data the last [UserDataRepository.deleteAllData] removed. */
data class UserDataDeletion(
    val activities: Int,
    val checkIns: Int,
    val metrics: Int,
    val plans: Int,
    val profileDeleted: Boolean,
    val prescriptions: Int = 0,
    val executions: Int = 0,
    val planRevisions: Int = 0,
    val raceEvents: Int = 0,
) {
    val removedAnything: Boolean
        get() = activities > 0 || checkIns > 0 || metrics > 0 || plans > 0 ||
            prescriptions > 0 || executions > 0 || planRevisions > 0 || raceEvents > 0 || profileDeleted
}

/**
 * Whole-device backup and reset. Activities, profile, check-ins, daily metrics
 * and plans live in the same database, so a single transaction can export or
 * erase them together. Sample blobs are represented by their count, exactly as
 * in the activity export; the compressed blob never leaves the device.
 */
class UserDataRepository(
    private val database: MeuCoachDatabase,
    private val healthSyncStore: HealthSyncStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun exportJson(firstRun: JSONObject? = null, includeTrash: Boolean = false): String =
        database.withTransaction {
            val trashedIds = if (includeTrash) emptySet() else database.activityDao().getDeletedIds().toSet()
            val root = JSONObject()
                .put("schemaVersion", MeuCoachDatabase.VERSION)
                .put("exportedAt", clock.instant().toString())
                .put("profile", database.profileDao().get()?.toJson() ?: JSONObject.NULL)
                .put("checkIns", database.dailyCheckInDao().getAll().toJsonArray { it.toJson() })
                .put("dailyMetrics", database.dailyMetricsDao().getAll().toJsonArray { it.toJson() })
                .put("weeklyPlans", exportPlans())
                .put("weeklyPlanRevisions", exportPlanRevisions())
                .put("dailyPrescriptions", exportPrescriptions())
                .put("sessionExecutions", exportExecutions(trashedIds))
                .put("raceEvents", exportRaceEvents())
                .put("activities", exportActivities(includeTrash))
                .put(
                    "importJobs",
                    database.importJobDao()
                        .getAll()
                        .filterNot { it.activityId in trashedIds }
                        .toJsonArray { it.toJson() },
                )
            firstRun?.let { root.put("firstRun", it) }
            root.toString(2)
        }

    suspend fun deleteAllData(): UserDataDeletion {
        val deletion = database.withTransaction {
            // Race events reference activities; delete the link owners first.
            val raceEvents = database.raceEventDao().deleteAll()
            val activities = database.activityDao().deleteAll()
            database.importJobDao().deleteAll()
            val checkIns = database.dailyCheckInDao().deleteAll()
            val metrics = database.dailyMetricsDao().deleteAll()
            // Sessions are deleted explicitly instead of relying on the cascade.
            val executions = database.sessionExecutionDao().deleteAll()
            val planRevisions = database.weeklyPlanRevisionDao().deleteAll()
            database.trainingPlanDao().deleteAllSessions()
            val plans = database.trainingPlanDao().deleteAllPlans()
            val prescriptions = database.dailyPrescriptionDao().deleteAll()
            val profileDeleted = database.profileDao().deleteAll() > 0
            UserDataDeletion(
                activities = activities,
                checkIns = checkIns,
                metrics = metrics,
                plans = plans,
                profileDeleted = profileDeleted,
                prescriptions = prescriptions,
                executions = executions,
                planRevisions = planRevisions,
                raceEvents = raceEvents,
            )
        }
        // A fresh profile must read the full initial Health Connect window again.
        healthSyncStore.reset()
        return deletion
    }

    private suspend fun exportActivities(includeTrash: Boolean): JSONArray {
        val array = JSONArray()
        val rows = if (includeTrash) {
            database.activityDao().getAllIncludingDeleted()
        } else {
            database.activityDao().getAll()
        }
        rows.forEach { activity ->
            array.put(
                activity.toJson(
                    laps = database.lapDao().getForActivity(activity.id),
                    blob = database.sampleBlobDao().getForActivity(activity.id),
                    alternates = database.activityAlternateDao().getForActivity(activity.id),
                ),
            )
        }
        return array
    }

    private suspend fun exportPlans(): JSONArray {
        val sessionsByWeek = database
            .trainingPlanDao()
            .getAllSessions()
            .groupBy { it.weekStart }
        val array = JSONArray()
        database.trainingPlanDao().getAllPlans().forEach { plan ->
            array.put(PlanJson.weeklyPlanToJson(plan, sessionsByWeek[plan.weekStart].orEmpty()))
        }
        return array
    }

    private suspend fun exportPlanRevisions(): JSONArray = database
        .weeklyPlanRevisionDao()
        .getAll()
        .toJsonArray { PlanJson.revisionToJson(it) }

    private suspend fun exportPrescriptions(): JSONArray = database
        .dailyPrescriptionDao()
        .getAll()
        .toJsonArray { it.toJson() }

    private suspend fun exportExecutions(excludedActivityIds: Set<String>): JSONArray = database
        .sessionExecutionDao()
        .getAll()
        .filterNot { it.activityId in excludedActivityIds }
        .toJsonArray { it.toJson() }

    private suspend fun exportRaceEvents(): JSONArray = database
        .raceEventDao()
        .getAll()
        .toJsonArray { it.toJson() }
}

private inline fun <T> List<T>.toJsonArray(crossinline toJson: (T) -> JSONObject): JSONArray {
    val array = JSONArray()
    forEach { array.put(toJson(it)) }
    return array
}

private fun RaceEventEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("distance", distance)
    put("targetDate", targetDate)
    putNullable("targetTimeSec", targetTimeSec)
    put("status", status)
    putNullable("activityId", activityId)
    put("createdAt", Instant.ofEpochMilli(createdAt).toString())
    put("updatedAt", Instant.ofEpochMilli(updatedAt).toString())
    put("resolvedAt", resolvedAt?.let { Instant.ofEpochMilli(it).toString() } ?: JSONObject.NULL)
}

private fun AthleteProfileEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("birthDate", birthDate)
    put("sex", sex.name)
    putNullable("heightCm", heightCm)
    putNullable("weightKg", weightKg)
    putNullable("restingHeartRate", restingHeartRate)
    putNullable("maxHeartRateObserved", maxHeartRateObserved)
    putNullable("lactateThresholdHeartRate", lactateThresholdHeartRate)
    putNullable("thresholdPaceSecPerKm", thresholdPaceSecPerKm)
    putNullable("vdot", vdot)
    put("experience", experience.name)
    put("timezone", timezone)
    put("weeklyAvailability", JSONObject(availabilityJson))
    put("goal", goalJson?.let(::JSONObject) ?: JSONObject.NULL)
    putNullable("city", city)
    putNullable("latitude", latitude)
    putNullable("longitude", longitude)
    put("units", units.name)
    put("treadmillAccess", treadmillAccess)
    put("createdAt", Instant.ofEpochMilli(createdAt).toString())
    put("updatedAt", Instant.ofEpochMilli(updatedAt).toString())
}

private fun DailyCheckInEntity.toJson(): JSONObject = JSONObject().apply {
    put("date", date)
    put("sleepQuality", sleepQuality)
    put("fatigue", fatigue)
    put("soreness", soreness)
    put("stress", stress)
    put("illnessOrInjury", illnessOrInjury)
    put("heatLevel", heatLevel.name)
    put("createdAt", Instant.ofEpochMilli(createdAt).toString())
    put("updatedAt", Instant.ofEpochMilli(updatedAt).toString())
}

private fun DailyMetricsEntity.toJson(): JSONObject = JSONObject().apply {
    put("date", date)
    putNullable("sleepDurationMin", sleepDurationMin)
    putNullable("restingHeartRate", restingHeartRate)
    putNullable("hrvRmssdMs", hrvRmssdMs)
    putNullable("spo2Avg", spo2Avg)
    put("source", source.name)
    put("createdAt", Instant.ofEpochMilli(createdAt).toString())
    put("updatedAt", Instant.ofEpochMilli(updatedAt).toString())
}

private fun SessionExecutionEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    putNullable("plannedSessionId", plannedSessionId)
    putNullable("activityId", activityId)
    putNullable("startedAt", startedAt?.let { Instant.ofEpochMilli(it).toString() })
    putNullable("endedAt", endedAt?.let { Instant.ofEpochMilli(it).toString() })
    put("pauses", runCatching { JSONArray(pausesJson) }.getOrElse { JSONArray() })
    put("durationSec", durationSec)
    putNullable("distanceM", distanceM)
    putNullable("rpe", rpe)
    put("source", source.name)
    put("createdAt", Instant.ofEpochMilli(createdAt).toString())
}

private fun DailyPrescriptionEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("date", date)
    putNullable("plannedSessionId", plannedSessionId)
    putNullable("plannedType", plannedType?.name)
    putNullable("plannedTitle", plannedTitle)
    putNullable("plannedDurationMin", plannedDurationMin)
    put("prescribedType", prescribedType.name)
    put("title", title)
    putNullable("prescribedDurationMin", prescribedDurationMin)
    put("targets", JSONObject(targetsJson))
    put("adjustment", adjustment.name)
    put("rationale", rationale)
    put("ruleIds", JSONArray(ruleIdsJson))
    put("evidenceIds", JSONArray(evidenceIdsJson))
    put("confidence", confidence.name)
    put("engineVersion", engineVersion)
    put("inputsHash", inputsHash)
    putNullable("supersedesId", supersedesId)
    put("createdAt", Instant.ofEpochMilli(createdAt).toString())
}

private fun ActivityEntity.toJson(
    laps: List<LapEntity>,
    blob: SampleBlobEntity?,
    alternates: List<ActivityAlternateEntity>,
): JSONObject = JSONObject().apply {
    put("id", id)
    put("source", source.name)
    putNullable("sourceId", sourceId)
    putNullable("sourceFileSha256", sourceFileSha256)
    put("sport", sport.name)
    putNullable("subSport", subSport)
    put("startTimeUtc", Instant.ofEpochMilli(startTimeUtc).toString())
    put("zoneId", zoneId)
    put("localDate", localDate)
    put("durationSec", durationSec)
    putNullable("movingTimeSec", movingTimeSec)
    put("distanceM", distanceM)
    putNullable("averagePaceSecPerKm", averagePaceSecPerKm)
    putNullable("bestPaceSecPerKm", bestPaceSecPerKm)
    putNullable("averageHeartRate", averageHeartRate)
    putNullable("maxHeartRate", maxHeartRate)
    putNullable("averageCadenceSpm", averageCadenceSpm)
    putNullable("maxCadenceSpm", maxCadenceSpm)
    putNullable("averagePowerW", averagePowerW)
    putNullable("maxPowerW", maxPowerW)
    putNullable("averageStepLengthM", averageStepLengthM)
    putNullable("elevationGainM", elevationGainM)
    putNullable("elevationLossM", elevationLossM)
    putNullable("calories", calories)
    putNullable("trainingEffectAerobic", trainingEffectAerobic)
    putNullable("trainingEffectAnaerobic", trainingEffectAnaerobic)
    putNullable("trainingLoadPeak", trainingLoadPeak)
    putNullable("deviceName", deviceName)
    putNullable("rawFilePath", rawFilePath)
    put("parserVersion", parserVersion)
    put("importedAt", Instant.ofEpochMilli(importedAt).toString())
    putNullable("deletedAt", deletedAt?.let { Instant.ofEpochMilli(it).toString() })
    put("sampleCount", blob?.sampleCount ?: 0)
    put("laps", laps.toJsonArray { it.toJson() })
    put("alternates", alternates.toJsonArray { it.toJson() })
}

private fun ActivityAlternateEntity.toJson(): JSONObject = JSONObject().apply {
    put("source", source.name)
    putNullable("sourceId", sourceId)
    putNullable("sourceFileSha256", sourceFileSha256)
    putNullable("rawFilePath", rawFilePath)
    put("parserVersion", parserVersion)
    put("importedAt", Instant.ofEpochMilli(importedAt).toString())
    put("sampleCount", sampleCount)
    put("durationSec", durationSec)
    put("distanceM", distanceM)
}

private fun LapEntity.toJson(): JSONObject = JSONObject().apply {
    put("index", lapIndex)
    putNullable("startTimeUtc", startTimeUtc?.let { Instant.ofEpochMilli(it).toString() })
    put("durationSec", durationSec)
    put("distanceM", distanceM)
    putNullable("averageHeartRate", averageHeartRate)
    putNullable("maxHeartRate", maxHeartRate)
    putNullable("averagePaceSecPerKm", averagePaceSecPerKm)
    putNullable("averageCadenceSpm", averageCadenceSpm)
    putNullable("averagePowerW", averagePowerW)
    putNullable("elevationGainM", elevationGainM)
}

private fun ImportJobEntity.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("fileName", fileName)
    put("sha256", sha256)
    putNullable("mime", mime)
    put("sizeBytes", sizeBytes)
    put("status", status.name)
    putNullable("parserVersion", parserVersion)
    putNullable("activityId", activityId)
    putNullable("errorCode", errorCode)
    putNullable("errorMessage", errorMessage)
    put("createdAt", Instant.ofEpochMilli(createdAt).toString())
    putNullable("finishedAt", finishedAt?.let { Instant.ofEpochMilli(it).toString() })
}

private fun JSONObject.putNullable(key: String, value: Any?) {
    put(key, value ?: JSONObject.NULL)
}
