package com.meucoach.core.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.PlannedSessionEntity
import com.meucoach.core.data.db.entity.WeeklyPlanEntity
import com.meucoach.core.data.db.entity.WeeklyPlanRevisionEntity
import com.meucoach.core.data.db.entity.plannedSessionId
import com.meucoach.core.data.prescription.PlanJson
import com.meucoach.core.data.prescription.PlanningSnapshotJson
import com.meucoach.core.data.prescription.PrescriptionJson
import com.meucoach.core.data.prescription.WorkoutStructureJson
import com.meucoach.core.data.profile.toModel
import com.meucoach.core.domain.prescription.PlanDecision
import com.meucoach.core.domain.prescription.PlanDecisionReason
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.QualityKind
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.replan.PlanEvaluationCheckpoint
import com.meucoach.core.domain.replan.PlanUpgrade
import com.meucoach.core.domain.replan.PlanUpgradeResult
import com.meucoach.core.domain.replan.PlanningFingerprints
import com.meucoach.core.domain.replan.ReplanSuggestion
import com.meucoach.core.model.Experience
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** A stored plan plus the revision metadata that is not part of the domain intent. */
data class StoredWeeklyPlan(
    val plan: WeeklyPlan,
    val version: Int,
    val revisionReason: String?,
    /** Original plan creation instant; never moves on re-save (FR-413). */
    val createdAt: Instant,
    val revisedAt: Instant?,
    val inputsHash: String?,
    val availabilityHash: String?,
    val generatedFromFingerprintVersion: String? = null,
    val generatedFromFingerprint: String? = null,
    val generatedFromProfileSnapshotJson: String? = null,
    val lastEvaluatedFingerprintVersion: String? = null,
    val lastEvaluatedFingerprint: String? = null,
    val temporalContextHash: String? = null,
    /** Engine version of the stored row; the upgrade path (C4-47) compares it. */
    val generatorVersion: String,
)

/** The last accepted revision, with the future sessions it replaced. */
data class PlanRevisionRecord(
    val id: String,
    val fromVersion: Int,
    val toVersion: Int,
    val trigger: String,
    val reason: String,
    val revisedAt: Instant?,
    val replacedSessions: List<PlannedSession>,
)

/** Why an accepted-looking revision could not be applied. */
enum class ReplanApplyFailure {
    NO_PLAN,
    STALE_VERSION,
    STALE_INPUTS,
    STALE_AVAILABILITY,
    STALE_PROFILE,
    INVALID_CANDIDATE,
    PROTECTED_CONFLICT,
    NOTHING_TO_UNDO,
}

sealed interface ReplanApplyOutcome {
    data class Applied(val version: Int, val revisionId: String) : ReplanApplyOutcome

    data class Failed(val failure: ReplanApplyFailure, val message: String) : ReplanApplyOutcome
}

sealed interface ReplanUndoOutcome {
    data class Applied(val version: Int, val revisionId: String) : ReplanUndoOutcome

    data class Failed(val failure: ReplanApplyFailure, val message: String) : ReplanUndoOutcome
}

sealed interface PlanUpgradeOutcome {
    data class Applied(val version: Int, val revisionId: String) : PlanUpgradeOutcome

    data class Skipped(val reason: String) : PlanUpgradeOutcome

    data class Failed(val failure: ReplanApplyFailure, val message: String) : PlanUpgradeOutcome
}

class TrainingPlanRepository(private val database: MeuCoachDatabase, private val clock: Clock = Clock.systemUTC()) {
    private val dao = database.trainingPlanDao()
    private val revisions = database.weeklyPlanRevisionDao()

    fun observeWeek(weekStart: LocalDate): Flow<WeeklyPlan?> = combine(
        dao.observePlan(weekStart.toString()),
        dao.observeSessions(weekStart.toString()),
    ) { plan, sessions -> plan?.toModel(sessions) }

    suspend fun getWeek(weekStart: LocalDate): WeeklyPlan? {
        val plan = dao.getPlan(weekStart.toString()) ?: return null
        return plan.toModel(dao.getSessions(weekStart.toString()))
    }

    /** Reads the domain plan together with version/hashes used by the replanner. */
    suspend fun getWeekRecord(weekStart: LocalDate): StoredWeeklyPlan? {
        val plan = dao.getPlan(weekStart.toString()) ?: return null
        return plan.toRecord(dao.getSessions(weekStart.toString()))
    }

    suspend fun getWeeks(weekStarts: List<LocalDate>): List<WeeklyPlan> = weekStarts.mapNotNull { getWeek(it) }

    /** Latest append-only revision of the week, used to explain "o que mudou". */
    suspend fun getLatestRevision(weekStart: LocalDate): PlanRevisionRecord? {
        val revision = revisions.getForWeek(weekStart.toString()).lastOrNull() ?: return null
        return PlanRevisionRecord(
            id = revision.id,
            fromVersion = revision.fromVersion,
            toVersion = revision.toVersion,
            trigger = revision.trigger,
            reason = revision.reason,
            revisedAt = Instant.ofEpochMilli(revision.createdAt),
            replacedSessions = PlanJson
                .sessionsFromJson(revision.replacedSessionsJson, weekStart.toString())
                .map { it.toModel() },
        )
    }

    /** Updates only session content, preserving status, skip reason and completedAt. */
    suspend fun updateSessions(weekStart: LocalDate, sessions: List<PlannedSession>): Boolean =
        database.withTransaction {
            val rows = dao.getSessions(weekStart.toString()).associateBy { it.id }
            sessions
                .map { session ->
                    val row = rows[plannedSessionId(weekStart, session.date)] ?: return@withTransaction false
                    dao.updateSession(
                        row.copy(
                            type = session.type,
                            title = session.title,
                            durationMin = session.durationMin,
                            rationale = session.rationale,
                            ruleIdsJson = session.ruleIds.toJson(),
                            evidenceIdsJson = session.evidenceIds.toJson(),
                            targetsJson = PrescriptionJson.targetsToJson(session.targets),
                            structureJson = session.structure?.let(WorkoutStructureJson::toJson),
                            qualityKind = session.qualityKind?.name,
                            planDecisionCode = session.planDecision?.reason?.name,
                            planDecisionDetail = session.planDecision?.detail,
                        ),
                    )
                }.all { it > 0 }
        }

    /**
     * Counts consecutive stored weeks immediately before [weekStart].
     * Used to anchor the 4-week deload block without another schema column.
     */
    suspend fun consecutiveWeeksBefore(weekStart: LocalDate): Int {
        val stored = dao.getPlanWeeksBefore(weekStart.toString()).map(LocalDate::parse).toSet()
        var expected = weekStart.minusWeeks(1)
        var count = 0
        while (expected in stored) {
            count += 1
            expected = expected.minusWeeks(1)
        }
        return count
    }

    suspend fun saveWeek(plan: WeeklyPlan) = database.withTransaction {
        val weekStart = plan.weekStart.toString()
        val existing = dao.getPlan(weekStart)
        dao.upsertPlan(
            WeeklyPlanEntity(
                weekStart = weekStart,
                // FR-413: re-saving content must not turn "created" into "last saved".
                // The first write stamps the row; later writes inherit both the
                // instant and the local creation date used to cut adherence.
                createdAt = existing?.createdAt ?: clock.millis(),
                createdOn = existing?.createdOn ?: plan.createdOn.toString(),
                confidence = plan.confidence,
                rationale = plan.rationale,
                weeklyMinutes = plan.weeklyMinutes,
                longRunMinutes = plan.longRunMinutes,
                qualityCount = plan.qualityCount,
                ruleIdsJson = plan.ruleIds.toJson(),
                evidenceIdsJson = plan.evidenceIds.toJson(),
                // Re-saving content outside applyReplan must not silently reset the revision state.
                version = existing?.version ?: 1,
                revisionReason = existing?.revisionReason,
                revisedAt = existing?.revisedAt,
                inputsHash = existing?.inputsHash,
                availabilityHash = existing?.availabilityHash,
                generatedFromFingerprintVersion = existing?.generatedFromFingerprintVersion,
                generatedFromFingerprint = existing?.generatedFromFingerprint,
                generatedFromProfileSnapshotJson = existing?.generatedFromProfileSnapshotJson,
                lastEvaluatedFingerprintVersion = existing?.lastEvaluatedFingerprintVersion,
                lastEvaluatedFingerprint = existing?.lastEvaluatedFingerprint,
                temporalContextHash = existing?.temporalContextHash,
                // The domain carries the version since C4-46; the entity column
                // stays the source of truth for the stored row.
                generatorVersion = plan.engineVersion,
            ),
        )
        dao.deleteSessions(weekStart)
        dao.insertSessions(plan.sessions.map { it.toEntity(plan.weekStart) })
    }

    /**
     * Captures the availability baseline of a legacy/initial plan without
     * pretending a revision happened. A known baseline is never overwritten
     * silently; only a real revision may change it (ADR 0028).
     */
    suspend fun captureAvailabilityBaseline(weekStart: LocalDate, availabilityHash: String): Boolean {
        require(availabilityHash.isNotBlank()) { "Availability hash must not be blank" }
        return database.withTransaction {
            val plan = dao.getPlan(weekStart.toString()) ?: return@withTransaction false
            if (plan.availabilityHash != null) return@withTransaction false
            dao.updatePlan(plan.copy(availabilityHash = availabilityHash)) == 1
        }
    }

    /**
     * Advances only the evaluated checkpoint (ADR 0031). A `NoChange` or a
     * baseline capture never increments the version or claims provenance: the
     * `generatedFrom*` fields stay exactly as they were.
     */
    suspend fun captureEvaluationCheckpoint(
        weekStart: LocalDate,
        expectedVersion: Int,
        checkpoint: PlanEvaluationCheckpoint,
    ): Boolean = database.withTransaction {
        val plan = dao.getPlan(weekStart.toString()) ?: return@withTransaction false
        if (plan.version != expectedVersion) return@withTransaction false
        dao.updatePlan(
            plan.copy(
                lastEvaluatedFingerprintVersion = checkpoint.fingerprintVersion,
                lastEvaluatedFingerprint = checkpoint.fingerprint,
                temporalContextHash = checkpoint.temporalContextHash,
                // Also establish an unknown availability baseline without a revision.
                availabilityHash = plan.availabilityHash ?: checkpoint.availabilityHash,
            ),
        ) == 1
    }

    /**
     * Applies a replanning suggestion atomically (ADR 0028).
     *
     * Protected sessions (`date <= today` or future `DONE`/`CLOSED`) are never
     * rewritten. Only strictly future `PLANNED` sessions are replaced, and the
     * insert never uses `REPLACE`, so a deterministic-ID collision aborts and
     * rolls back the whole revision. [currentInputsHash] is the hash of the
     * inputs recomputed at accept time; when supplied it invalidates a
     * suggestion built from different inputs.
     */
    suspend fun applyReplan(
        suggestion: ReplanSuggestion,
        candidate: WeeklyPlan,
        today: LocalDate,
        currentInputsHash: String? = null,
    ): ReplanApplyOutcome = runCatching {
        database.withTransaction {
            val weekStart = suggestion.weekStart
            val stored = dao.getPlan(weekStart.toString())
                ?: return@withTransaction ReplanApplyOutcome.Failed(
                    ReplanApplyFailure.NO_PLAN,
                    "O plano desta semana não existe mais.",
                )
            if (stored.version != suggestion.expectedVersion) {
                return@withTransaction ReplanApplyOutcome.Failed(
                    ReplanApplyFailure.STALE_VERSION,
                    "O plano mudou desde esta sugestão. Avalie novamente.",
                )
            }
            // Compare-and-apply of the planning identity runs inside the same
            // transaction that validates the version and writes the revision.
            val profile = database.profileDao().get()?.toModel()
                ?: return@withTransaction ReplanApplyOutcome.Failed(
                    ReplanApplyFailure.STALE_PROFILE,
                    "O perfil mudou desde esta sugestão. Avalie novamente.",
                )
            val currentFingerprint = PlanningFingerprints.of(profile)
            val currentTemporalContext = PlanningFingerprints.temporalContextHash(profile)
            if (currentFingerprint.version != suggestion.expectedFingerprintVersion ||
                currentFingerprint.hash != suggestion.expectedFingerprint ||
                currentTemporalContext != suggestion.expectedTemporalContextHash
            ) {
                return@withTransaction ReplanApplyOutcome.Failed(
                    ReplanApplyFailure.STALE_PROFILE,
                    "O perfil ou o fuso mudou desde esta sugestão. Avalie novamente.",
                )
            }
            if (currentInputsHash != null && currentInputsHash != suggestion.expectedInputsHash) {
                return@withTransaction ReplanApplyOutcome.Failed(
                    ReplanApplyFailure.STALE_INPUTS,
                    "Os dados usados na sugestão mudaram. Avalie novamente.",
                )
            }
            if (stored.availabilityHash != null &&
                suggestion.expectedAvailabilityHash != null &&
                stored.availabilityHash != suggestion.expectedAvailabilityHash
            ) {
                return@withTransaction ReplanApplyOutcome.Failed(
                    ReplanApplyFailure.STALE_AVAILABILITY,
                    "Sua disponibilidade mudou desde esta sugestão. Avalie novamente.",
                )
            }
            if (candidate.weekStart != weekStart) {
                return@withTransaction ReplanApplyOutcome.Failed(
                    ReplanApplyFailure.INVALID_CANDIDATE,
                    "A sugestão pertence a outra semana.",
                )
            }

            val storedSessions = dao.getSessions(weekStart.toString())
            val protected = storedSessions.filter {
                LocalDate.parse(it.date).isAfter(today) && it.status != SessionStatus.PLANNED
            }
            val protectedDates = protected.map { LocalDate.parse(it.date) }.toSet()
            val candidateFuture = candidate.sessions.filter { it.date.isAfter(today) }
            protected.forEach { row ->
                val match = candidateFuture.firstOrNull { it.date.toString() == row.date }
                if (match == null || !match.sameProtectedContentAs(row)) {
                    return@withTransaction ReplanApplyOutcome.Failed(
                        ReplanApplyFailure.PROTECTED_CONFLICT,
                        "Uma sessão já concluída ou encerrada seria alterada; nada foi aplicado.",
                    )
                }
            }
            val newFuture = candidateFuture.filter { it.date !in protectedDates }
            if (newFuture.any { it.status != SessionStatus.PLANNED }) {
                return@withTransaction ReplanApplyOutcome.Failed(
                    ReplanApplyFailure.INVALID_CANDIDATE,
                    "A revisão só pode propor sessões futuras planejadas.",
                )
            }

            val replaced = storedSessions.filter {
                LocalDate.parse(it.date).isAfter(today) && it.status == SessionStatus.PLANNED
            }
            val newFutureEntities = newFuture.map { it.toEntity(weekStart) }
            // The persisted aggregates describe the week that actually remains after the merge,
            // not whatever totals the caller sent with the candidate.
            val mergedSessions = storedSessions.filter {
                !LocalDate.parse(it.date).isAfter(today) || it.status != SessionStatus.PLANNED
            } + newFutureEntities
            val toVersion = stored.version + 1
            val revision = WeeklyPlanRevisionEntity(
                id = "$weekStart-v$toVersion",
                weekStart = weekStart.toString(),
                fromVersion = stored.version,
                toVersion = toVersion,
                fromInputsHash = stored.inputsHash,
                toInputsHash = suggestion.expectedInputsHash,
                fromAvailabilityHash = stored.availabilityHash,
                toAvailabilityHash = suggestion.availabilityHash,
                fromFingerprintVersion = stored.generatedFromFingerprintVersion,
                fromFingerprint = stored.generatedFromFingerprint,
                fromProfileSnapshotJson = stored.generatedFromProfileSnapshotJson,
                fromTemporalContextHash = stored.temporalContextHash,
                toFingerprintVersion = suggestion.expectedFingerprintVersion,
                toFingerprint = suggestion.expectedFingerprint,
                toProfileSnapshotJson = PlanningSnapshotJson.toJson(suggestion.profileSnapshot),
                toTemporalContextHash = suggestion.expectedTemporalContextHash,
                generatorVersion = WeeklyPlanGenerator.ENGINE_VERSION,
                previousPlanJson = PlanJson.planMetadataToJson(stored).toString(),
                replacedSessionsJson = PlanJson.sessionsToJson(replaced),
                trigger = suggestion.trigger.name,
                reason = suggestion.rationale,
                ruleIdsJson = suggestion.ruleIds.toJson(),
                evidenceIdsJson = suggestion.evidenceIds.toJson(),
                confidence = suggestion.confidence,
                createdAt = clock.millis(),
            )
            revisions.insert(revision)
            dao.deletePlannedSessionsAfter(weekStart.toString(), today.toString())
            dao.insertRevisionSessions(newFutureEntities)

            val updated = stored.copy(
                version = toVersion,
                revisionReason = suggestion.rationale,
                revisedAt = clock.millis(),
                inputsHash = suggestion.expectedInputsHash,
                availabilityHash = suggestion.availabilityHash,
                generatedFromFingerprintVersion = suggestion.expectedFingerprintVersion,
                generatedFromFingerprint = suggestion.expectedFingerprint,
                generatedFromProfileSnapshotJson = PlanningSnapshotJson.toJson(suggestion.profileSnapshot),
                lastEvaluatedFingerprintVersion = suggestion.expectedFingerprintVersion,
                lastEvaluatedFingerprint = suggestion.expectedFingerprint,
                temporalContextHash = suggestion.expectedTemporalContextHash,
                weeklyMinutes = mergedSessions.sumOf { it.durationMin ?: 0 },
                longRunMinutes = mergedSessions.firstOrNull { it.type == WorkoutType.LONG }?.durationMin,
                qualityCount = mergedSessions.count { it.type == WorkoutType.QUALITY },
                rationale = candidate.rationale,
                confidence = candidate.confidence,
                ruleIdsJson = candidate.ruleIds.toJson(),
                evidenceIdsJson = candidate.evidenceIds.toJson(),
            )
            check(dao.updatePlan(updated) == 1) { "Plan row disappeared during revision" }
            ReplanApplyOutcome.Applied(version = toVersion, revisionId = revision.id)
        }
    }.getOrElse { error ->
        when (error) {
            is SQLiteConstraintException, is IllegalArgumentException -> ReplanApplyOutcome.Failed(
                ReplanApplyFailure.INVALID_CANDIDATE,
                "A revisão conflitou com o estado atual; nada foi alterado.",
            )

            else -> throw error
        }
    }

    /**
     * Reverts the last accepted revision as a new append-only revision (ADR 0028).
     *
     * Only strictly future `PLANNED` sessions are restored from the revision
     * snapshot; today, the past and every future `DONE`/`CLOSED` session stay
     * untouched. A protected conflict aborts without writing. [expectedVersion]
     * must match the stored version, so a stale screen never undoes a newer
     * adjustment.
     */
    suspend fun undoReplan(weekStart: LocalDate, today: LocalDate, expectedVersion: Int): ReplanUndoOutcome =
        runCatching {
            database.withTransaction {
                val stored = dao.getPlan(weekStart.toString())
                    ?: return@withTransaction ReplanUndoOutcome.Failed(
                        ReplanApplyFailure.NO_PLAN,
                        "O plano desta semana não existe mais.",
                    )
                if (stored.version != expectedVersion) {
                    return@withTransaction ReplanUndoOutcome.Failed(
                        ReplanApplyFailure.STALE_VERSION,
                        "O plano mudou desde este ajuste. Nada foi desfeito.",
                    )
                }
                val revision = revisions.getForWeek(weekStart.toString()).lastOrNull()
                    ?: return@withTransaction ReplanUndoOutcome.Failed(
                        ReplanApplyFailure.NOTHING_TO_UNDO,
                        "Não há ajuste para desfazer nesta semana.",
                    )
                if (revision.toVersion != stored.version) {
                    return@withTransaction ReplanUndoOutcome.Failed(
                        ReplanApplyFailure.STALE_VERSION,
                        "O plano mudou desde este ajuste. Nada foi desfeito.",
                    )
                }
                if (revision.trigger == UNDO_TRIGGER) {
                    return@withTransaction ReplanUndoOutcome.Failed(
                        ReplanApplyFailure.NOTHING_TO_UNDO,
                        "Não há ajuste para desfazer nesta semana.",
                    )
                }

                val storedSessions = dao.getSessions(weekStart.toString())
                val protectedDates = storedSessions
                    .filter { !LocalDate.parse(it.date).isAfter(today) || it.status != SessionStatus.PLANNED }
                    .map { it.date }
                    .toSet()
                val restored = PlanJson
                    .sessionsFromJson(revision.replacedSessionsJson, weekStart.toString())
                    .map { it.toModel() }
                    .filter { it.date.isAfter(today) && it.status == SessionStatus.PLANNED }
                if (restored.any { it.date.toString() in protectedDates }) {
                    return@withTransaction ReplanUndoOutcome.Failed(
                        ReplanApplyFailure.PROTECTED_CONFLICT,
                        "Uma sessão já concluída ou encerrada seria alterada; nada foi desfeito.",
                    )
                }

                val currentFuture = storedSessions.filter {
                    LocalDate.parse(it.date).isAfter(today) && it.status == SessionStatus.PLANNED
                }
                val restoredEntities = restored.map { it.toEntity(weekStart) }
                val toVersion = stored.version + 1
                val undoRevision = WeeklyPlanRevisionEntity(
                    id = "$weekStart-v$toVersion",
                    weekStart = weekStart.toString(),
                    fromVersion = stored.version,
                    toVersion = toVersion,
                    fromInputsHash = stored.inputsHash,
                    toInputsHash = revision.fromInputsHash ?: revision.generatorVersion,
                    fromAvailabilityHash = stored.availabilityHash,
                    toAvailabilityHash = revision.fromAvailabilityHash ?: revision.generatorVersion,
                    fromFingerprintVersion = stored.generatedFromFingerprintVersion,
                    fromFingerprint = stored.generatedFromFingerprint,
                    fromProfileSnapshotJson = stored.generatedFromProfileSnapshotJson,
                    fromTemporalContextHash = stored.temporalContextHash,
                    // The plan returns to the provenance captured by the reverted revision.
                    toFingerprintVersion = revision.fromFingerprintVersion,
                    toFingerprint = revision.fromFingerprint,
                    toProfileSnapshotJson = revision.fromProfileSnapshotJson,
                    toTemporalContextHash = revision.fromTemporalContextHash,
                    generatorVersion = stored.generatorVersion,
                    previousPlanJson = PlanJson.planMetadataToJson(stored).toString(),
                    replacedSessionsJson = PlanJson.sessionsToJson(currentFuture),
                    trigger = UNDO_TRIGGER,
                    reason = UNDO_REASON,
                    ruleIdsJson = stored.ruleIdsJson,
                    evidenceIdsJson = stored.evidenceIdsJson,
                    confidence = stored.confidence,
                    createdAt = clock.millis(),
                )
                revisions.insert(undoRevision)
                dao.deletePlannedSessionsAfter(weekStart.toString(), today.toString())
                if (restoredEntities.isNotEmpty()) dao.insertRevisionSessions(restoredEntities)

                val merged = storedSessions.filter {
                    !LocalDate.parse(it.date).isAfter(today) || it.status != SessionStatus.PLANNED
                } + restoredEntities
                val updated = stored.copy(
                    version = toVersion,
                    revisionReason = UNDO_REASON,
                    revisedAt = clock.millis(),
                    inputsHash = revision.fromInputsHash,
                    availabilityHash = revision.fromAvailabilityHash,
                    generatedFromFingerprintVersion = revision.fromFingerprintVersion,
                    generatedFromFingerprint = revision.fromFingerprint,
                    generatedFromProfileSnapshotJson = revision.fromProfileSnapshotJson,
                    weeklyMinutes = merged.sumOf { it.durationMin ?: 0 },
                    longRunMinutes = merged.firstOrNull { it.type == WorkoutType.LONG }?.durationMin,
                    qualityCount = merged.count { it.type == WorkoutType.QUALITY },
                    // The plan-level rationale describes the action that produced
                    // this version; session rationales come from the restored snapshot.
                    rationale = UNDO_REASON,
                )
                check(dao.updatePlan(updated) == 1) { "Plan row disappeared during undo" }
                ReplanUndoOutcome.Applied(version = toVersion, revisionId = undoRevision.id)
            }
        }.getOrElse { error ->
            when (error) {
                is SQLiteConstraintException, is IllegalArgumentException -> ReplanUndoOutcome.Failed(
                    ReplanApplyFailure.INVALID_CANDIDATE,
                    "O ajuste conflitou com o estado atual; nada foi alterado.",
                )

                else -> throw error
            }
        }

    suspend fun deleteWeek(weekStart: LocalDate): Int = database.withTransaction {
        database.sessionExecutionDao().deleteForWeek(weekStart.toString())
        dao.deleteSessions(weekStart.toString())
        dao.deletePlan(weekStart.toString())
    }

    /**
     * Applies the idempotent engine upgrade of the stored week (ADR 0048 §2.2).
     *
     * The merge preserves today, the past and every future `DONE`/`CLOSED`
     * session, replaces only strictly future `PLANNED` sessions and records a
     * revision with trigger `ENGINE_UPGRADE`. A week already on [targetVersion],
     * a past week or a regeneration the domain refuses all return [Skipped]
     * without writing anything; a mid-write conflict rolls the transaction
     * back and the v6 week stays intact.
     */
    suspend fun applyUpgrade(
        candidate: WeeklyPlan,
        today: LocalDate,
        targetVersion: String = WeeklyPlanGenerator.ENGINE_VERSION,
        experience: Experience? = null,
    ): PlanUpgradeOutcome = runCatching {
        database.withTransaction {
            val weekStart = candidate.weekStart
            val stored = dao.getPlan(weekStart.toString())
                ?: return@withTransaction PlanUpgradeOutcome.Failed(
                    ReplanApplyFailure.NO_PLAN,
                    "O plano desta semana não existe mais.",
                )
            if (stored.generatorVersion == targetVersion) {
                return@withTransaction PlanUpgradeOutcome.Skipped(
                    "O plano já está na versão atual do motor.",
                )
            }
            val storedSessions = dao.getSessions(weekStart.toString())
            val currentPlan = stored.toModel(storedSessions)
            when (val merged = PlanUpgrade.upgrade(currentPlan, candidate, today, targetVersion, experience)) {
                is PlanUpgradeResult.Skipped -> PlanUpgradeOutcome.Skipped(merged.reason)

                is PlanUpgradeResult.Upgraded -> {
                    val upgraded = merged.plan
                    val protectedDates = storedSessions
                        .filter { LocalDate.parse(it.date).isAfter(today) && it.status != SessionStatus.PLANNED }
                        .map { it.date }
                        .toSet()
                    val newFuture = upgraded.sessions.filter {
                        it.date.isAfter(today) && it.date.toString() !in protectedDates
                    }
                    if (newFuture.any { it.status != SessionStatus.PLANNED }) {
                        return@withTransaction PlanUpgradeOutcome.Failed(
                            ReplanApplyFailure.INVALID_CANDIDATE,
                            "A atualização só propõe sessões futuras planejadas.",
                        )
                    }
                    val replaced = storedSessions.filter {
                        LocalDate.parse(it.date).isAfter(today) && it.status == SessionStatus.PLANNED
                    }
                    val newFutureEntities = newFuture.map { it.toEntity(weekStart) }
                    val toVersion = stored.version + 1
                    val revision = WeeklyPlanRevisionEntity(
                        id = "$weekStart-v$toVersion",
                        weekStart = weekStart.toString(),
                        fromVersion = stored.version,
                        toVersion = toVersion,
                        fromInputsHash = stored.inputsHash,
                        // The upgrade keeps the stored inputs/availability baseline;
                        // a legacy null is marked with the target version so the
                        // non-null audit columns never invent a hash.
                        toInputsHash = stored.inputsHash ?: targetVersion,
                        fromAvailabilityHash = stored.availabilityHash,
                        toAvailabilityHash = stored.availabilityHash ?: targetVersion,
                        fromFingerprintVersion = stored.generatedFromFingerprintVersion,
                        fromFingerprint = stored.generatedFromFingerprint,
                        fromProfileSnapshotJson = stored.generatedFromProfileSnapshotJson,
                        fromTemporalContextHash = stored.temporalContextHash,
                        toFingerprintVersion = stored.generatedFromFingerprintVersion,
                        toFingerprint = stored.generatedFromFingerprint,
                        toProfileSnapshotJson = stored.generatedFromProfileSnapshotJson,
                        toTemporalContextHash = stored.temporalContextHash,
                        generatorVersion = targetVersion,
                        previousPlanJson = PlanJson.planMetadataToJson(stored).toString(),
                        replacedSessionsJson = PlanJson.sessionsToJson(replaced),
                        trigger = PlanUpgrade.ENGINE_UPGRADE_TRIGGER,
                        reason = upgraded.rationale,
                        ruleIdsJson = upgraded.ruleIds.toJson(),
                        evidenceIdsJson = upgraded.evidenceIds.toJson(),
                        confidence = upgraded.confidence,
                        createdAt = clock.millis(),
                    )
                    revisions.insert(revision)
                    dao.deletePlannedSessionsAfter(weekStart.toString(), today.toString())
                    dao.insertRevisionSessions(newFutureEntities)

                    val mergedSessions = storedSessions.filter {
                        !LocalDate.parse(it.date).isAfter(today) || it.status != SessionStatus.PLANNED
                    } + newFutureEntities
                    val updated = stored.copy(
                        version = toVersion,
                        revisionReason = upgraded.rationale,
                        revisedAt = clock.millis(),
                        generatorVersion = targetVersion,
                        weeklyMinutes = mergedSessions.sumOf { it.durationMin ?: 0 },
                        longRunMinutes = mergedSessions.firstOrNull { it.type == WorkoutType.LONG }?.durationMin,
                        qualityCount = mergedSessions.count { it.type == WorkoutType.QUALITY },
                        rationale = upgraded.rationale,
                        confidence = upgraded.confidence,
                        ruleIdsJson = upgraded.ruleIds.toJson(),
                        evidenceIdsJson = upgraded.evidenceIds.toJson(),
                    )
                    check(dao.updatePlan(updated) == 1) { "Plan row disappeared during upgrade" }
                    PlanUpgradeOutcome.Applied(version = toVersion, revisionId = revision.id)
                }
            }
        }
    }.getOrElse { error ->
        when (error) {
            is SQLiteConstraintException, is IllegalArgumentException -> PlanUpgradeOutcome.Failed(
                ReplanApplyFailure.INVALID_CANDIDATE,
                "A atualização conflitou com o estado atual; nada foi alterado.",
            )

            else -> throw error
        }
    }

    private fun WeeklyPlanEntity.toModel(sessions: List<PlannedSessionEntity>) = WeeklyPlan(
        weekStart = LocalDate.parse(weekStart),
        sessions = sessions.map { it.toModel() },
        weeklyMinutes = weeklyMinutes,
        longRunMinutes = longRunMinutes,
        qualityCount = qualityCount,
        rationale = rationale,
        confidence = confidence,
        ruleIds = ruleIdsJson.toList(),
        evidenceIds = evidenceIdsJson.toList(),
        createdOn = LocalDate.parse(createdOn),
        engineVersion = generatorVersion,
    )

    private fun WeeklyPlanEntity.toRecord(sessions: List<PlannedSessionEntity>) = StoredWeeklyPlan(
        plan = toModel(sessions),
        version = version,
        revisionReason = revisionReason,
        createdAt = Instant.ofEpochMilli(createdAt),
        revisedAt = revisedAt?.let(Instant::ofEpochMilli),
        inputsHash = inputsHash,
        availabilityHash = availabilityHash,
        generatedFromFingerprintVersion = generatedFromFingerprintVersion,
        generatedFromFingerprint = generatedFromFingerprint,
        generatedFromProfileSnapshotJson = generatedFromProfileSnapshotJson,
        lastEvaluatedFingerprintVersion = lastEvaluatedFingerprintVersion,
        lastEvaluatedFingerprint = lastEvaluatedFingerprint,
        temporalContextHash = temporalContextHash,
        generatorVersion = generatorVersion,
    )

    private fun PlannedSessionEntity.toModel() = PlannedSession(
        date = LocalDate.parse(date),
        type = type,
        title = title,
        durationMin = durationMin,
        rationale = rationale,
        ruleIds = ruleIdsJson.toList(),
        evidenceIds = evidenceIdsJson.toList(),
        targets = PrescriptionJson.targetsFromJson(targetsJson),
        status = status,
        skipReason = skipReason,
        completionEvidence = completionEvidence,
        executionId = executionId,
        activityId = activityId,
        closureReason = closureReason,
        closedAt = closedAt?.let(Instant::ofEpochMilli),
        qualityKind = qualityKind?.let { runCatching { QualityKind.valueOf(it) }.getOrNull() },
        planDecision = planDecisionCode?.let { code ->
            runCatching { PlanDecisionReason.valueOf(code) }.getOrNull()
                ?.let { PlanDecision(it, planDecisionDetail?.takeIf(String::isNotBlank)) }
        },
        structure = WorkoutStructureJson.fromJson(structureJson),
    )

    private fun PlannedSession.toEntity(weekStart: LocalDate) = PlannedSessionEntity(
        id = plannedSessionId(weekStart, date),
        weekStart = weekStart.toString(),
        date = date.toString(),
        type = type,
        title = title,
        durationMin = durationMin,
        rationale = rationale,
        ruleIdsJson = ruleIds.toJson(),
        evidenceIdsJson = evidenceIds.toJson(),
        targetsJson = PrescriptionJson.targetsToJson(targets),
        status = status,
        skipReason = skipReason,
        completedAt = if (status == SessionStatus.DONE) clock.millis() else null,
        completionEvidence = completionEvidence,
        executionId = executionId,
        activityId = activityId,
        closureReason = closureReason,
        closedAt = closedAt?.toEpochMilli(),
        structureJson = structure?.let(WorkoutStructureJson::toJson),
        qualityKind = qualityKind?.name,
        planDecisionCode = planDecision?.reason?.name,
        planDecisionDetail = planDecision?.detail,
    )

    private fun PlannedSession.sameProtectedContentAs(row: PlannedSessionEntity): Boolean =
        date.toString() == row.date &&
            type == row.type &&
            title == row.title &&
            durationMin == row.durationMin &&
            rationale == row.rationale &&
            ruleIds == row.ruleIdsJson.toList() &&
            evidenceIds == row.evidenceIdsJson.toList() &&
            targets == PrescriptionJson.targetsFromJson(row.targetsJson) &&
            status == row.status &&
            skipReason == row.skipReason &&
            completionEvidence == row.completionEvidence &&
            executionId == row.executionId &&
            activityId == row.activityId &&
            closureReason == row.closureReason &&
            closedAt == row.closedAt?.let(Instant::ofEpochMilli)

    private fun List<String>.toJson(): String = JSONArray(this).toString()

    private fun String.toList(): List<String> {
        val array = JSONArray(this)
        return List(array.length()) { index -> array.getString(index) }
    }

    companion object {
        /** Revision trigger of an undo; a new revision, never a rewrite (ADR 0028). */
        const val UNDO_TRIGGER = "UNDO"

        const val UNDO_REASON = "Você desfez o ajuste"
    }
}
