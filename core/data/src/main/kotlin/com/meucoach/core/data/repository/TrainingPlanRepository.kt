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
import com.meucoach.core.data.profile.toModel
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.replan.PlanEvaluationCheckpoint
import com.meucoach.core.domain.replan.PlanningFingerprints
import com.meucoach.core.domain.replan.ReplanSuggestion
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
    val revisedAt: Instant?,
    val inputsHash: String?,
    val availabilityHash: String?,
    val generatedFromFingerprintVersion: String? = null,
    val generatedFromFingerprint: String? = null,
    val generatedFromProfileSnapshotJson: String? = null,
    val lastEvaluatedFingerprintVersion: String? = null,
    val lastEvaluatedFingerprint: String? = null,
    val temporalContextHash: String? = null,
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
}

sealed interface ReplanApplyOutcome {
    data class Applied(val version: Int, val revisionId: String) : ReplanApplyOutcome

    data class Failed(val failure: ReplanApplyFailure, val message: String) : ReplanApplyOutcome
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
            sessions
                .map { session ->
                    dao.updateSessionContent(
                        id = plannedSessionId(weekStart, session.date),
                        type = session.type,
                        title = session.title,
                        durationMin = session.durationMin,
                        rationale = session.rationale,
                        ruleIdsJson = session.ruleIds.toJson(),
                        evidenceIdsJson = session.evidenceIds.toJson(),
                        targetsJson = PrescriptionJson.targetsToJson(session.targets),
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
                createdAt = clock.millis(),
                createdOn = plan.createdOn.toString(),
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

    suspend fun deleteWeek(weekStart: LocalDate): Int = database.withTransaction {
        database.sessionExecutionDao().deleteForWeek(weekStart.toString())
        dao.deleteSessions(weekStart.toString())
        dao.deletePlan(weekStart.toString())
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
    )

    private fun WeeklyPlanEntity.toRecord(sessions: List<PlannedSessionEntity>) = StoredWeeklyPlan(
        plan = toModel(sessions),
        version = version,
        revisionReason = revisionReason,
        revisedAt = revisedAt?.let(Instant::ofEpochMilli),
        inputsHash = inputsHash,
        availabilityHash = availabilityHash,
        generatedFromFingerprintVersion = generatedFromFingerprintVersion,
        generatedFromFingerprint = generatedFromFingerprint,
        generatedFromProfileSnapshotJson = generatedFromProfileSnapshotJson,
        lastEvaluatedFingerprintVersion = lastEvaluatedFingerprintVersion,
        lastEvaluatedFingerprint = lastEvaluatedFingerprint,
        temporalContextHash = temporalContextHash,
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
}
