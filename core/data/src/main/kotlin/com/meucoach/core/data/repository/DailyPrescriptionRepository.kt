package com.meucoach.core.data.repository

import androidx.room.withTransaction
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.DailyPrescriptionEntity
import com.meucoach.core.data.id.UuidV7
import com.meucoach.core.data.prescription.PrescriptionJson
import com.meucoach.core.domain.prescription.DailyPrescription
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
        )

    private fun DailyPrescriptionEntity.toModel() = DailyPrescription(
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
        id = id,
        supersedesId = supersedesId,
    )
}
