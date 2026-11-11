package com.meucoach.core.data.repository

import androidx.room.withTransaction
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.ActivityAlternateEntity
import com.meucoach.core.data.db.entity.ActivityEntity
import com.meucoach.core.data.db.entity.ImportJobEntity
import com.meucoach.core.data.db.entity.ImportJobStatus
import com.meucoach.core.data.db.entity.LapEntity
import com.meucoach.core.data.db.entity.SampleBlobEntity
import com.meucoach.core.data.id.UuidV7
import com.meucoach.core.data.samples.SampleBlobCodec
import com.meucoach.core.data.samples.SampleBlobException
import com.meucoach.core.domain.importer.ActivityDeduplicator
import com.meucoach.core.domain.retention.TrashPolicy
import com.meucoach.core.model.ActivityLap
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.Distance
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
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
    /** Resolved at save time so imports follow the current profile timezone. */
    private val currentZone: () -> ZoneId = { zoneId },
) {
    fun observeActivitySummaries(): Flow<List<ActivitySummary>> = combine(
        database.activityDao().observeAll(),
        database.activityAlternateDao().observeSources(),
    ) { activities, sourceRows ->
        val sourcesByActivity = sourceRows.groupBy({ it.activityId }, { it.source })
        activities.map { it.toSummary(sourcesByActivity[it.id].orEmpty()) }
    }

    suspend fun listActivitySummaries(): List<ActivitySummary> = database.withTransaction {
        val sourcesByActivity = database
            .activityAlternateDao()
            .getAllSources()
            .groupBy({ it.activityId }, { it.source })
        database
            .activityDao()
            .getAll()
            .map { it.toSummary(sourcesByActivity[it.id].orEmpty()) }
    }

    /** One canonical activity, without trashed rows. */
    suspend fun getActivitySummary(id: String): ActivitySummary? = database.withTransaction {
        val entity = database.activityDao().findById(id) ?: return@withTransaction null
        val alternates = database.activityAlternateDao().getForActivity(id).map { it.source }
        entity.toSummary(alternates)
    }

    /** Activities whose start time falls in the half-open interval, excluding the trash. */
    suspend fun listActivitySummariesBetween(from: Instant, to: Instant): List<ActivitySummary> =
        database.withTransaction {
            val sourcesByActivity = database
                .activityAlternateDao()
                .getAllSources()
                .groupBy({ it.activityId }, { it.source })
            database
                .activityDao()
                .findBetween(from.toEpochMilli(), to.toEpochMilli())
                .map { it.toSummary(sourcesByActivity[it.id].orEmpty()) }
        }

    /** Last time an activity was imported, used to decide whether load data is fresh. */
    fun observeLastImportedAt(): Flow<Instant?> = database
        .activityDao()
        .observeLastImportedAt()
        .map { millis -> millis?.let(Instant::ofEpochMilli) }

    suspend fun lastImportedAt(): Instant? = database
        .activityDao()
        .lastImportedAt()
        ?.let(Instant::ofEpochMilli)

    fun observeImportJobs(): Flow<List<ImportJobSummary>> = database
        .importJobDao()
        .observeAll()
        .map { jobs -> jobs.map { it.toSummary() } }

    suspend fun listImportJobs(): List<ImportJobSummary> = database
        .importJobDao()
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
                    return@withTransaction SaveActivityResult.ConflictingTrash(existing.id)
                }
                return@withTransaction SaveActivityResult.Duplicate(existing.id)
            }
            database.activityAlternateDao().findBySha256(sha)?.let { alternate ->
                val parent = database.activityDao().findByIdIncludingDeleted(alternate.activityId)
                if (parent?.deletedAt != null) {
                    return@withTransaction SaveActivityResult.ConflictingTrash(alternate.activityId)
                }
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
                sampleWarning =
                    "As séries temporais desta atividade estão em um formato que esta versão não lê."
            } else {
                try {
                    samples = codec.decode(blob.bytes, Instant.ofEpochMilli(blob.startTimeUtc))
                    if (samples.size != blob.sampleCount) {
                        sampleWarning = "As séries temporais desta atividade estão incompletas."
                    }
                } catch (error: SampleBlobException) {
                    sampleWarning = "Não foi possível ler as séries temporais desta atividade."
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

    fun observeTrash(): Flow<List<TrashedActivity>> = database
        .activityDao()
        .observeTrash()
        .map { activities -> activities.map { it.toTrashed() } }

    suspend fun listTrash(): List<TrashedActivity> = database
        .activityDao()
        .getTrash()
        .map { it.toTrashed() }

    suspend fun restoreActivity(id: String): Boolean = database.withTransaction {
        val restored = database.activityDao().restore(id) > 0
        if (restored) {
            // ADR 0025: restoring the activity restores its evidence too.
            database.trainingPlanDao().restoreActivityEvidence(id)
        }
        restored
    }

    suspend fun deleteActivity(id: String, deletedAt: Instant = clock.instant()): Boolean = database.withTransaction {
        val deleted = database.activityDao().softDelete(id, deletedAt.toEpochMilli()) > 0
        if (deleted) {
            // The session keeps its history but stops counting as evidence.
            database.trainingPlanDao().suspendActivityEvidence(id)
        }
        deleted
    }

    suspend fun permanentlyDeleteActivity(id: String): Boolean = purgeActivity(id) != null

    /** Removes one trashed activity and its children/jobs; returns the raw file to clean. */
    suspend fun purgeActivity(id: String): PurgedActivity? = database.withTransaction {
        val entity = database.activityDao().findByIdIncludingDeleted(id) ?: return@withTransaction null
        if (entity.deletedAt == null) return@withTransaction null
        unlinkFromSession(id)
        database.importJobDao().deleteForActivity(id)
        val removed = database.activityDao().hardDelete(id) > 0
        if (removed) PurgedActivity(id = id, rawFilePath = entity.rawFilePath) else null
    }

    /** Purges every activity whose seven-day trash window expired before [now]. */
    suspend fun purgeTrashBefore(now: Instant): List<PurgedActivity> = database.withTransaction {
        val cutoff = now.minus(Duration.ofDays(TrashPolicy.RETENTION_DAYS))
        val expired = database.activityDao().getDeletedBefore(cutoff.toEpochMilli())
        if (expired.isEmpty()) return@withTransaction emptyList()
        expired.forEach { activity ->
            unlinkFromSession(activity.id)
            database.importJobDao().deleteForActivity(activity.id)
        }
        database.activityDao().hardDeleteBefore(cutoff.toEpochMilli())
        expired.map { PurgedActivity(id = it.id, rawFilePath = it.rawFilePath) }
    }

    /** ADR 0024/0025: the session keeps an honest, unverified record. */
    private suspend fun unlinkFromSession(activityId: String) {
        database.sessionExecutionDao().deleteForActivity(activityId)
        database.trainingPlanDao().purgeActivityLink(activityId)
    }

    private fun ImportedActivity.toEntity(
        id: String,
        importedAt: Instant,
        sourceFileSha256: String?,
        rawFilePath: String?,
    ): ActivityEntity {
        val zone = currentZone()
        return ActivityEntity(
            id = id,
            source = source,
            sourceId = sourceId,
            sourceFileSha256 = sourceFileSha256,
            sport = sport,
            subSport = subSport,
            startTimeUtc = startTime.toEpochMilli(),
            zoneId = zone.id,
            localDate = startTime.atZone(zone).toLocalDate().toString(),
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
    }

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

    private fun ActivityEntity.toSummary(alternateSources: List<ActivitySource> = emptyList()) = ActivitySummary(
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
        // Canonical source first; alternates keep their import order.
        sources = (listOf(source) + alternateSources).distinct(),
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

    private fun ActivityAlternateEntity.toStoredAlternate() = StoredAlternate(
        source = source,
        sourceFileSha256 = sourceFileSha256,
        rawFilePath = rawFilePath,
        parserVersion = parserVersion,
        importedAt = Instant.ofEpochMilli(importedAt),
        sampleCount = sampleCount,
    )

    private fun ImportJobEntity.toSummary() = ImportJobSummary(
        id = id,
        fileName = fileName,
        status = status,
        activityId = activityId,
        errorCode = errorCode,
        errorMessage = errorMessage,
        createdAt = Instant.ofEpochMilli(createdAt),
        finishedAt = finishedAt?.let(Instant::ofEpochMilli),
    )
}

sealed interface SaveActivityResult {
    data class Saved(val id: String) : SaveActivityResult

    data class Duplicate(val id: String) : SaveActivityResult

    data class Replaced(val id: String, val previousSource: ActivitySource) : SaveActivityResult

    data class Restored(val id: String) : SaveActivityResult

    data class ConflictingTrash(val id: String) : SaveActivityResult
}

data class ImportJobSummary(
    val id: String,
    val fileName: String,
    val status: ImportJobStatus,
    val activityId: String?,
    val errorCode: String?,
    val errorMessage: String?,
    val createdAt: Instant,
    val finishedAt: Instant?,
)

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

data class TrashedActivity(
    val id: String,
    val sport: Sport,
    val startTime: Instant,
    val durationSec: Int,
    val distance: Distance,
    val deletedAt: Instant,
)

data class PurgedActivity(val id: String, val rawFilePath: String?)

private fun ActivityEntity.toTrashed() = TrashedActivity(
    id = id,
    sport = sport,
    startTime = Instant.ofEpochMilli(startTimeUtc),
    durationSec = durationSec,
    distance = Distance(distanceM),
    deletedAt = Instant.ofEpochMilli(requireNotNull(deletedAt) { "Trash item must have deletedAt" }),
)

private const val DEDUP_START_TOLERANCE_MS = 1_000L
