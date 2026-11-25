package com.meucoach.core.data.repository

import androidx.room.withTransaction
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.DailyPrescriptionEntity
import com.meucoach.core.data.id.UuidV7
import com.meucoach.core.data.prescription.PrescriptionJson
import com.meucoach.core.data.prescription.WorkoutStructureJson
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.QualityBlockReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/**
 * Persists the daily prescription versions (ADR 0023).
 *
 * The latest row of a date is the authority for the UI. Appending never
 * overwrites a previous version; an identical (date, inputsHash, engineVersion)
 * is ignored so concurrent collectors cannot duplicate the same decision.
 */
class DailyPrescriptionRepository(
    private val database: MeuCoachDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val idGenerator: () -> String = { UuidV7.generate(clock) },
) {
    private val dao = database.dailyPrescriptionDao()

    fun observeLatest(date: LocalDate): Flow<DailyPrescription?> =
        dao.observeLatest(date.toString()).map { it?.toModel() }

    suspend fun getLatest(date: LocalDate): DailyPrescription? = dao.latest(date.toString())?.toModel()

    suspend fun getVersions(date: LocalDate): List<DailyPrescription> =
        dao.versions(date.toString()).map { it.toModel() }

    /**
     * Latest decision of each day in the seven-day window. The weekly plan
     * card uses it to show the day's prescription instead of the original
     * plan intent when the day was adjusted (R-PRESC-03).
     */
    suspend fun getLatestForWeek(weekStart: LocalDate): Map<LocalDate, DailyPrescription> = dao
        .range(weekStart.toString(), weekStart.plusDays(6).toString())
        .groupBy { LocalDate.parse(it.date) }
        .mapValues { (_, versions) -> versions.last().toModel() }

    suspend fun append(prescription: DailyPrescription): DailyPrescription = database.withTransaction {
        val date = prescription.date.toString()
        val latest = dao.latest(date)
        if (latest != null &&
            latest.engineVersion == prescription.engineVersion &&
            latest.inputsHash == prescription.inputsHash
        ) {
            return@withTransaction latest.toModel()
        }
        val entity = prescription.toEntity(
            id = prescription.id ?: idGenerator(),
            supersedesId = prescription.supersedesId ?: latest?.id,
            createdAt = clock.millis(),
        )
        dao.insert(entity)
        entity.toModel()
    }

    private fun DailyPrescription.toEntity(id: String, supersedesId: String?, createdAt: Long) =
        DailyPrescriptionEntity(
            id = id,
            date = date.toString(),
            plannedSessionId = plannedSessionId,
            plannedType = plannedType,
            plannedTitle = plannedTitle,
            plannedDurationMin = plannedDurationMin,
            prescribedType = prescribedType,
            title = title,
            prescribedDurationMin = prescribedDurationMin,
            targetsJson = PrescriptionJson.targetsToJson(targets),
            adjustment = adjustment,
            rationale = rationale,
            ruleIdsJson = PrescriptionJson.idsToJson(ruleIds),
            evidenceIdsJson = PrescriptionJson.idsToJson(evidenceIds),
            confidence = confidence,
            engineVersion = engineVersion,
            inputsHash = inputsHash,
            supersedesId = supersedesId,
            createdAt = createdAt,
            structureJson = structure?.let(WorkoutStructureJson::toJson),
            qualityBlockReason = qualityBlockReason?.name,
            decisionDetail = decisionDetail,
            unlockHint = unlockHint,
        )

    private fun DailyPrescriptionEntity.toModel(): DailyPrescription {
        // An unknown reason (a newer writer) drops the whole block instead of
        // violating the domain invariant that reason, detail and hint agree.
        val reason = qualityBlockReason?.let { runCatching { QualityBlockReason.valueOf(it) }.getOrNull() }
        return DailyPrescription(
            date = LocalDate.parse(date),
            plannedSessionId = plannedSessionId,
            plannedType = plannedType,
            plannedTitle = plannedTitle,
            plannedDurationMin = plannedDurationMin,
            prescribedType = prescribedType,
            title = title,
            prescribedDurationMin = prescribedDurationMin,
            targets = PrescriptionJson.targetsFromJson(targetsJson),
            adjustment = adjustment,
            rationale = rationale,
            ruleIds = PrescriptionJson.idsFromJson(ruleIdsJson),
            evidenceIds = PrescriptionJson.idsFromJson(evidenceIdsJson),
            confidence = confidence,
            engineVersion = engineVersion,
            inputsHash = inputsHash,
            qualityBlockReason = reason,
            decisionDetail = decisionDetail.takeIf { reason != null },
            unlockHint = unlockHint.takeIf { reason != null },
            structure = WorkoutStructureJson.fromJson(structureJson),
            id = id,
            supersedesId = supersedesId,
        )
    }
}
