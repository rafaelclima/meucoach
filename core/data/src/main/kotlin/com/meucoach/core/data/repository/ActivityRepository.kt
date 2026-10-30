package com.meucoach.core.data.repository

import androidx.room.withTransaction
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.ActivityAlternateEntity
import com.meucoach.core.data.db.entity.ActivityEntity
import com.meucoach.core.data.db.entity.ImportJobEntity
import com.meucoach.core.data.db.entity.LapEntity
import com.meucoach.core.data.db.entity.SampleBlobEntity
import com.meucoach.core.data.id.UuidV7
import com.meucoach.core.data.samples.SampleBlobCodec
import com.meucoach.core.data.samples.SampleBlobException
import com.meucoach.core.domain.importer.ActivityDeduplicator
import com.meucoach.core.model.ActivityLap
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.Distance
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Local source of truth for activities. Import adapters produce
 * [ImportedActivity]; this repository maps it to Room and back without
 * exposing persistence entities to the UI.
 */
class ActivityRepository(
    private val database: MeuCoachDatabase,
    private val codec: SampleBlobCodec = SampleBlobCodec(),
    private val clock: Clock = Clock.systemUTC(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val idGenerator: () -> String = { UuidV7.generate(clock) },
    private val alternateIdGenerator: () -> String = { UuidV7.generate(clock) },
    private val deduplicator: ActivityDeduplicator = ActivityDeduplicator(),
) {
    fun observeActivitySummaries(): Flow<List<ActivitySummary>> = database
        .activityDao()
        .observeAll()
        .map { activities -> activities.map { it.toSummary() } }

    suspend fun listActivitySummaries(): List<ActivitySummary> = database
        .activityDao()
        .getAll()
        .map { it.toSummary() }

    suspend fun saveImportedActivity(
        imported: ImportedActivity,
        sourceFileSha256: String? = null,
        rawFilePath: String? = null,
    ): SaveActivityResult = database.withTransaction {
        val sha = sourceFileSha256?.takeIf { it.isNotBlank() }
        if (sha != null) {
            database.activityDao().findBySha256IncludingDeleted(sha)?.let { existing ->
                if (existing.deletedAt != null) {
                    database.activityDao().restore(existing.id)
                    return@withTransaction SaveActivityResult.Restored(existing.id)
                }
                return@withTransaction SaveActivityResult.Duplicate(existing.id)
            }
            database.activityAlternateDao().findBySha256(sha)?.let { alternate ->
                return@withTransaction SaveActivityResult.Duplicate(alternate.activityId)
            }
        }

        val importedAt = clock.instant()
        val matching = findMatchingActivity(imported)
        if (matching == null) {
            val id = idGenerator()
            insertAggregate(imported, id, importedAt, sha, rawFilePath)
            return@withTransaction SaveActivityResult.Saved(id)
        }

        val existing = matching.toImportedActivity(laps = emptyList(), samples = emptyList())
        val existingSampleCount = database.sampleBlobDao().getSampleCount(matching.id) ?: 0
        if (deduplicator.isPreferred(imported, existing, existingSampleCount)) {
            recordAlternate(matching)
            replaceAggregate(matching.id, imported, importedAt, sha, rawFilePath)
            SaveActivityResult.Replaced(matching.id, matching.source)
        } else {
            recordAlternate(
                activityId = matching.id,
                imported = imported,
                importedAt = importedAt,
                sourceFileSha256 = sha,
                rawFilePath = rawFilePath,
            )
            SaveActivityResult.Duplicate(matching.id)
        }
    }

    private suspend fun findMatchingActivity(imported: ImportedActivity): ActivityEntity? {
        val startUtc = imported.startTime.toEpochMilli()
        return database
            .activityDao()
            .findCandidates(
                sport = imported.sport,
                fromUtc = startUtc - DEDUP_START_TOLERANCE_MS,
                toUtc = startUtc + DEDUP_START_TOLERANCE_MS,
            )
            .firstOrNull { candidate ->
                deduplicator.matches(
                    candidate.toImportedActivity(laps = emptyList(), samples = emptyList()),
                    imported,
                )
            }
    }

    private suspend fun insertAggregate(
        imported: ImportedActivity,
        id: String,
        importedAt: Instant,
        sourceFileSha256: String?,
        rawFilePath: String?,
    ) {
        database.activityDao().insert(
            imported.toEntity(
                id = id,
                importedAt = importedAt,
                sourceFileSha256 = sourceFileSha256,
                rawFilePath = rawFilePath,
            ),
        )
        insertChildren(id, imported)
    }

    private suspend fun replaceAggregate(
        id: String,
        imported: ImportedActivity,
        importedAt: Instant,
        sourceFileSha256: String?,
        rawFilePath: String?,
    ) {
        database.activityDao().update(
            imported.toEntity(
                id = id,
                importedAt = importedAt,
                sourceFileSha256 = sourceFileSha256,
                rawFilePath = rawFilePath,
            ),
        )
        database.lapDao().deleteForActivity(id)
        database.sampleBlobDao().deleteForActivity(id)
        insertChildren(id, imported)
    }

    private suspend fun insertChildren(activityId: String, imported: ImportedActivity) {
        val laps = imported.laps.map { it.toEntity(activityId) }
        if (laps.isNotEmpty()) {
            database.lapDao().insertAll(laps)
        }

        if (imported.samples.isNotEmpty()) {
            database.sampleBlobDao().upsert(
                SampleBlobEntity(
                    activityId = activityId,
                    version = SampleBlobCodec.VERSION,
                    encoding = SampleBlobCodec.ENCODING,
                    bytes = codec.encode(imported.samples),
                    sampleCount = imported.samples.size,
                    startTimeUtc = imported.startTime.toEpochMilli(),
                ),
            )
        }
    }

    private suspend fun recordAlternate(existing: ActivityEntity) {
        val blob = database.sampleBlobDao().getForActivity(existing.id)
        recordAlternate(
            activityId = existing.id,
            source = existing.source,
            sourceId = existing.sourceId,
            sourceFileSha256 = existing.sourceFileSha256,
            rawFilePath = existing.rawFilePath,
            parserVersion = existing.parserVersion,
            importedAt = Instant.ofEpochMilli(existing.importedAt),
            sampleCount = blob?.sampleCount ?: 0,
            durationSec = existing.durationSec,
            distanceM = existing.distanceM,
        )
    }

    private suspend fun recordAlternate(
        activityId: String,
        imported: ImportedActivity,
        importedAt: Instant,
        sourceFileSha256: String?,
        rawFilePath: String?,
    ) {
        recordAlternate(
            activityId = activityId,
            source = imported.source,
            sourceId = imported.sourceId,
            sourceFileSha256 = sourceFileSha256,
            rawFilePath = rawFilePath,
            parserVersion = imported.parserVersion,
            importedAt = importedAt,
            sampleCount = imported.samples.size,
            durationSec = imported.durationSec,
            distanceM = imported.distanceM,
        )
    }

    private suspend fun recordAlternate(
        activityId: String,
        source: ActivitySource,
        sourceId: String?,
        sourceFileSha256: String?,
        rawFilePath: String?,
        parserVersion: String,
        importedAt: Instant,
        sampleCount: Int,
        durationSec: Int,
        distanceM: Double,
    ) {
        if (sourceFileSha256 != null && database.activityAlternateDao().findBySha256(sourceFileSha256) != null) {
            return
        }
        database.activityAlternateDao().insert(
            ActivityAlternateEntity(
                id = alternateIdGenerator(),
                activityId = activityId,
                source = source,
                sourceId = sourceId,
                sourceFileSha256 = sourceFileSha256,
                rawFilePath = rawFilePath,
                parserVersion = parserVersion,
                importedAt = importedAt.toEpochMilli(),
                sampleCount = sampleCount,
                durationSec = durationSec,
                distanceM = distanceM,
            ),
        )
    }

    suspend fun openActivity(id: String): StoredActivity? = database.withTransaction {
        val entity = database.activityDao().findById(id) ?: return@withTransaction null
        val laps = database.lapDao().getForActivity(id).map { it.toModel() }
        val alternates = database.activityAlternateDao().getForActivity(id).map { it.toStoredAlternate() }
        val blob = database.sampleBlobDao().getForActivity(id)
        var samples = emptyList<ActivitySample>()
        var sampleWarning: String? = null

        if (blob != null) {
            if (blob.version != SampleBlobCodec.VERSION || blob.encoding != SampleBlobCodec.ENCODING) {
                sampleWarning = "Unsupported sample blob ${blob.encoding} v${blob.version}"
            } else {
                try {
                    samples = codec.decode(blob.bytes, Instant.ofEpochMilli(blob.startTimeUtc))
                    if (samples.size != blob.sampleCount) {
                        sampleWarning =
                            "Sample blob has ${samples.size} samples, expected ${blob.sampleCount}"
                    }
                } catch (error: SampleBlobException) {
                    sampleWarning = "Samples unavailable: ${error.message}"
                }
            }
        }

        StoredActivity(
            id = entity.id,
            imported = entity.toImportedActivity(laps, samples),
            importedAt = Instant.ofEpochMilli(entity.importedAt),
            rawFilePath = entity.rawFilePath,
            sourceFileSha256 = entity.sourceFileSha256,
            zoneId = entity.zoneId,
            localDate = LocalDate.parse(entity.localDate),
            sampleWarning = sampleWarning,
            alternates = alternates,
        )
    }

    suspend fun deleteActivity(id: String, deletedAt: Instant = clock.instant()): Boolean =
        database.activityDao().softDelete(id, deletedAt.toEpochMilli()) > 0

    suspend fun permanentlyDeleteActivity(id: String): Boolean = database.activityDao().hardDelete(id) > 0

    suspend fun deleteAllData(): Int = database.withTransaction {
        val deletedActivities = database.activityDao().deleteAll()
        database.importJobDao().deleteAll()
        deletedActivities
    }

    /**
     * Basic JSON export for the settings screen and manual backup. Samples are
     * represented by their count; the compressed blob stays in the database.
     */
    suspend fun exportJson(): String = database.withTransaction {
        val activities = database.activityDao().getAllIncludingDeleted()
        val jobs = database.importJobDao().getAll()

        val activityArray = JSONArray()
        activities.forEach { activity ->
            val laps = database.lapDao().getForActivity(activity.id)
            val blob = database.sampleBlobDao().getForActivity(activity.id)
            val alternates = database.activityAlternateDao().getForActivity(activity.id)
            activityArray.put(activity.toJson(laps, blob, alternates))
        }

        val jobArray = JSONArray()
        jobs.forEach { jobArray.put(it.toJson()) }

        JSONObject()
            .put("schemaVersion", MeuCoachDatabase.VERSION)
            .put("exportedAt", clock.instant().toString())
            .put("activities", activityArray)
            .put("importJobs", jobArray)
            .toString(2)
    }

    private fun ImportedActivity.toEntity(
        id: String,
        importedAt: Instant,
        sourceFileSha256: String?,
        rawFilePath: String?,
    ) = ActivityEntity(
        id = id,
        source = source,
        sourceId = sourceId,
        sourceFileSha256 = sourceFileSha256,
        sport = sport,
        subSport = subSport,
        startTimeUtc = startTime.toEpochMilli(),
        zoneId = zoneId.id,
        localDate = startTime.atZone(zoneId).toLocalDate().toString(),
        durationSec = durationSec,
        movingTimeSec = movingTimeSec,
        distanceM = distanceM,
        averagePaceSecPerKm = averagePace?.secondsPerKm?.toDouble(),
        bestPaceSecPerKm = null,
        averageHeartRate = averageHeartRate,
        maxHeartRate = maxHeartRate,
        averageCadenceSpm = averageCadenceSpm,
        maxCadenceSpm = maxCadenceSpm,
        averagePowerW = averagePowerW,
        maxPowerW = maxPowerW,
        averageStepLengthM = averageStepLengthM,
        elevationGainM = elevationGainM,
        elevationLossM = elevationLossM,
        calories = calories,
        trainingEffectAerobic = null,
        trainingEffectAnaerobic = null,
        trainingLoadPeak = null,
        deviceName = deviceName,
        rawFilePath = rawFilePath,
        parserVersion = parserVersion,
        importedAt = importedAt.toEpochMilli(),
        deletedAt = null,
    )

    private fun ActivityLap.toEntity(activityId: String) = LapEntity(
        activityId = activityId,
        lapIndex = index,
        startTimeUtc = startTime?.toEpochMilli(),
        durationSec = durationSec,
        distanceM = distanceM,
        averageHeartRate = averageHeartRate,
        maxHeartRate = maxHeartRate,
        averagePaceSecPerKm = averagePace?.secondsPerKm?.toDouble(),
        averageCadenceSpm = averageCadenceSpm,
        averagePowerW = averagePowerW,
        elevationGainM = elevationGainM,
    )

    private fun ActivityEntity.toSummary() = ActivitySummary(
        id = id,
        source = source,
        sport = sport,
        startTime = Instant.ofEpochMilli(startTimeUtc),
        durationSec = durationSec,
        distance = Distance(distanceM),
        averageHeartRate = averageHeartRate,
        maxHeartRate = maxHeartRate,
        averagePace = averagePaceSecPerKm.toPace(),
        averageCadenceSpm = averageCadenceSpm?.takeIf { it > 0 },
        trainingLoad = null,
    )

    private fun ActivityEntity.toImportedActivity(laps: List<ActivityLap>, samples: List<ActivitySample>) =
        ImportedActivity(
            source = source,
            sport = sport,
            startTime = Instant.ofEpochMilli(startTimeUtc),
            durationSec = durationSec,
            distanceM = distanceM,
            sourceId = sourceId,
            subSport = subSport,
            movingTimeSec = movingTimeSec,
            averageHeartRate = averageHeartRate,
            maxHeartRate = maxHeartRate,
            averagePace = averagePaceSecPerKm.toPace(),
            averageCadenceSpm = averageCadenceSpm,
            maxCadenceSpm = maxCadenceSpm,
            averagePowerW = averagePowerW,
            maxPowerW = maxPowerW,
            averageStepLengthM = averageStepLengthM,
            elevationGainM = elevationGainM,
            elevationLossM = elevationLossM,
            calories = calories,
            laps = laps,
            samples = samples,
            deviceName = deviceName,
            parserVersion = parserVersion,
        )

    private fun LapEntity.toModel() = ActivityLap(
        index = lapIndex,
        durationSec = durationSec,
        distanceM = distanceM,
        startTime = startTimeUtc?.let(Instant::ofEpochMilli),
        averageHeartRate = averageHeartRate,
        maxHeartRate = maxHeartRate,
        averagePace = averagePaceSecPerKm.toPace(),
        averageCadenceSpm = averageCadenceSpm,
        averagePowerW = averagePowerW,
        elevationGainM = elevationGainM,
    )

    private fun Double?.toPace(): Pace? {
        val seconds = this?.takeIf { it.isFinite() && it > 0.0 }?.roundToInt() ?: return null
        return Pace.fromSecondsPerKm(seconds)
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
        put(
            "laps",
            JSONArray().apply {
                laps.forEach { lap -> put(lap.toJson()) }
            },
        )
        put(
            "alternates",
            JSONArray().apply {
                alternates.forEach { alternate -> put(alternate.toJson()) }
            },
        )
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

    private fun ActivityAlternateEntity.toStoredAlternate() = StoredAlternate(
        source = source,
        sourceFileSha256 = sourceFileSha256,
        rawFilePath = rawFilePath,
        parserVersion = parserVersion,
        importedAt = Instant.ofEpochMilli(importedAt),
        sampleCount = sampleCount,
    )

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
}

sealed interface SaveActivityResult {
    data class Saved(val id: String) : SaveActivityResult

    data class Duplicate(val id: String) : SaveActivityResult

    data class Replaced(val id: String, val previousSource: ActivitySource) : SaveActivityResult

    data class Restored(val id: String) : SaveActivityResult
}

data class StoredAlternate(
    val source: ActivitySource,
    val sourceFileSha256: String?,
    val rawFilePath: String?,
    val parserVersion: String,
    val importedAt: Instant,
    val sampleCount: Int,
)

data class StoredActivity(
    val id: String,
    val imported: ImportedActivity,
    val importedAt: Instant,
    val rawFilePath: String?,
    val sourceFileSha256: String?,
    val zoneId: String,
    val localDate: LocalDate,
    val sampleWarning: String? = null,
    val alternates: List<StoredAlternate> = emptyList(),
)

private const val DEDUP_START_TOLERANCE_MS = 1_000L
