package com.meucoach.core.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.SessionExecutionEntity
import com.meucoach.core.data.id.UuidV7
import com.meucoach.core.domain.prescription.SessionClosureReason
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.ExecutionPause
import com.meucoach.core.model.ExecutionSource
import com.meucoach.core.model.SessionExecution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.time.Instant

/** What happened when trying to conclude a planned session. */
sealed interface CompletionOutcome {
    data class Recorded(val execution: SessionExecution) : CompletionOutcome

    data class Rejected(val reason: String) : CompletionOutcome

    data class AlreadyCompleted(val reason: String) : CompletionOutcome

    data class ActivityAlreadyLinked(val reason: String) : CompletionOutcome
}

/**
 * Owns the session execution and the activity link (ADR 0024).
 *
 * Completion and the 1:1 link happen in one transaction. The unique indexes on
 * planned_sessions.activityId, session_executions.plannedSessionId and
 * session_executions.activityId are the last line of defence against two
 * activities reaching the same session (or the reverse).
 */
class SessionExecutionRepository(
    private val database: MeuCoachDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val idGenerator: () -> String = { UuidV7.generate(clock) },
) {
    private val sessions = database.trainingPlanDao()
    private val executions = database.sessionExecutionDao()

    fun observeLinkedActivityIds(): Flow<Set<String>> = combine(
        sessions.observeLinkedActivityIds(),
        executions.observeLinkedActivityIds(),
    ) { fromSessions, fromExecutions -> (fromSessions + fromExecutions).toSet() }

    suspend fun getExecution(id: String): SessionExecution? = executions.findById(id)?.toModel()

    suspend fun getForSession(plannedSessionId: String): SessionExecution? =
        executions.findBySession(plannedSessionId)?.toModel()

    suspend fun getForActivity(activityId: String): SessionExecution? = executions.findByActivity(activityId)?.toModel()

    suspend fun listAll(): List<SessionExecution> = executions.getAll().map { it.toModel() }

    suspend fun recordActivityExecution(
        plannedSessionId: String,
        activityId: String,
        startedAt: Instant,
        durationSec: Int,
        distanceM: Double?,
    ): CompletionOutcome = runCatching {
        database.withTransaction {
            val session = sessions.getSession(plannedSessionId)
                ?: return@withTransaction CompletionOutcome.Rejected("Sessão planejada não encontrada.")
            if (session.status != SessionStatus.PLANNED) {
                return@withTransaction CompletionOutcome.AlreadyCompleted(
                    "Este treino já foi concluído ou encerrado.",
                )
            }
            if (sessions.getSessionByActivityId(activityId) != null ||
                executions.findByActivity(activityId) != null
            ) {
                return@withTransaction CompletionOutcome.ActivityAlreadyLinked(
                    "Esta corrida já está vinculada a outro treino.",
                )
            }
            if (executions.findBySession(plannedSessionId) != null) {
                return@withTransaction CompletionOutcome.AlreadyCompleted(
                    "Este treino já tem uma execução registrada.",
                )
            }
            val execution = SessionExecution(
                id = idGenerator(),
                plannedSessionId = plannedSessionId,
                activityId = activityId,
                startedAt = startedAt,
                endedAt = startedAt.plusSeconds(durationSec.toLong()),
                durationSec = durationSec,
                distanceM = distanceM,
                rpe = null,
                source = ExecutionSource.ACTIVITY,
                createdAt = clock.instant(),
            )
            executions.insert(execution.toEntity())
            val updated = sessions.markDone(
                id = plannedSessionId,
                evidence = CompletionEvidence.ACTIVITY.name,
                executionId = execution.id,
                activityId = activityId,
                completedAt = clock.millis(),
            )
            if (updated != 1) throw SessionChangedException()
            CompletionOutcome.Recorded(execution)
        }
    }.getOrElse { error ->
        when (error) {
            is SQLiteConstraintException -> CompletionOutcome.ActivityAlreadyLinked(
                "Esta corrida já está vinculada a outro treino.",
            )

            is SessionChangedException -> CompletionOutcome.AlreadyCompleted(
                "Este treino já foi concluído ou encerrado.",
            )

            else -> throw error
        }
    }
    suspend fun recordManualExecution(plannedSessionId: String, durationMin: Int, rpe: Int): CompletionOutcome =
        runCatching {
            database.withTransaction {
                val session = sessions.getSession(plannedSessionId)
                    ?: return@withTransaction CompletionOutcome.Rejected("Sessão planejada não encontrada.")
                if (session.status != SessionStatus.PLANNED) {
                    return@withTransaction CompletionOutcome.AlreadyCompleted(
                        "Este treino já foi concluído ou encerrado.",
                    )
                }
                if (executions.findBySession(plannedSessionId) != null) {
                    return@withTransaction CompletionOutcome.AlreadyCompleted(
                        "Este treino já tem uma execução registrada.",
                    )
                }
                val execution = SessionExecution(
                    id = idGenerator(),
                    plannedSessionId = plannedSessionId,
                    activityId = null,
                    startedAt = null,
                    endedAt = null,
                    durationSec = durationMin * SECONDS_PER_MINUTE,
                    distanceM = null,
                    rpe = rpe,
                    source = ExecutionSource.MANUAL,
                    createdAt = clock.instant(),
                )
                executions.insert(execution.toEntity())
                val updated = sessions.markDone(
                    id = plannedSessionId,
                    evidence = CompletionEvidence.MANUAL.name,
                    executionId = execution.id,
                    activityId = null,
                    completedAt = clock.millis(),
                )
                if (updated != 1) throw SessionChangedException()
                CompletionOutcome.Recorded(execution)
            }
        }.getOrElse { error ->
            when (error) {
                is SQLiteConstraintException -> CompletionOutcome.AlreadyCompleted(
                    "Este treino já tem uma execução registrada.",
                )

                is SessionChangedException -> CompletionOutcome.AlreadyCompleted(
                    "Este treino já foi concluído ou encerrado.",
                )

                else -> throw error
            }
        }

    /**
     * Replaces an explicit MANUAL completion with the real imported run.
     *
     * This is a correction, not a new completion: the manual declaration
     * (duration/RPE) is discarded, the execution row is upgraded in place and
     * the session keeps its completion date. The 1:1 indexes still guarantee
     * that an activity never points to two sessions.
     */
    suspend fun replaceManualWithActivity(
        plannedSessionId: String,
        activityId: String,
        startedAt: Instant,
        durationSec: Int,
        distanceM: Double?,
    ): CompletionOutcome = runCatching {
        database.withTransaction {
            val session = sessions.getSession(plannedSessionId)
                ?: return@withTransaction CompletionOutcome.Rejected("Sessão planejada não encontrada.")
            if (session.status != SessionStatus.DONE ||
                session.completionEvidence != CompletionEvidence.MANUAL
            ) {
                return@withTransaction CompletionOutcome.Rejected(
                    "Este treino não tem um registro manual para substituir.",
                )
            }
            val execution = executions.findBySession(plannedSessionId)
                ?: return@withTransaction CompletionOutcome.Rejected(
                    "Execução do registro manual não encontrada.",
                )
            if (execution.source != ExecutionSource.MANUAL) {
                return@withTransaction CompletionOutcome.Rejected(
                    "Este treino não tem um registro manual para substituir.",
                )
            }
            if (sessions.getSessionByActivityId(activityId) != null ||
                executions.findByActivity(activityId) != null
            ) {
                return@withTransaction CompletionOutcome.ActivityAlreadyLinked(
                    "Esta corrida já está vinculada a outro treino.",
                )
            }
            val updatedExecution = execution.copy(
                activityId = activityId,
                startedAt = startedAt.toEpochMilli(),
                endedAt = startedAt.plusSeconds(durationSec.toLong()).toEpochMilli(),
                durationSec = durationSec,
                distanceM = distanceM,
                // The manual RPE was tied to the discarded declaration.
                rpe = null,
                source = ExecutionSource.ACTIVITY,
            )
            if (executions.update(updatedExecution) != 1) throw SessionChangedException()
            val updated = sessions.replaceManualEvidence(
                id = plannedSessionId,
                evidence = CompletionEvidence.ACTIVITY.name,
                executionId = execution.id,
                activityId = activityId,
                completedAt = session.completedAt ?: clock.millis(),
            )
            if (updated != 1) throw SessionChangedException()
            CompletionOutcome.Recorded(updatedExecution.toModel())
        }
    }.getOrElse { error ->
        when (error) {
            is SQLiteConstraintException -> CompletionOutcome.ActivityAlreadyLinked(
                "Esta corrida já está vinculada a outro treino.",
            )

            is SessionChangedException -> CompletionOutcome.Rejected(
                "Não foi possível substituir o registro. Tente novamente.",
            )

            else -> throw error
        }
    }

    /** Closes a day that will not be trained (rest, illness, skip, missed, replaced). */
    suspend fun closeSession(
        plannedSessionId: String,
        reason: SessionClosureReason,
        note: String? = null,
        closedAt: Instant = clock.instant(),
    ): Boolean = sessions.close(
        id = plannedSessionId,
        reason = reason.name,
        note = note?.trim()?.ifBlank { null },
        closedAt = closedAt.toEpochMilli(),
    ) > 0

    private fun SessionExecution.toEntity() = SessionExecutionEntity(
        id = id,
        plannedSessionId = plannedSessionId,
        activityId = activityId,
        startedAt = startedAt?.toEpochMilli(),
        endedAt = endedAt?.toEpochMilli(),
        pausesJson = pauses.toJson(),
        durationSec = durationSec,
        distanceM = distanceM,
        rpe = rpe,
        source = source,
        createdAt = createdAt.toEpochMilli(),
    )

    private fun SessionExecutionEntity.toModel() = SessionExecution(
        id = id,
        plannedSessionId = plannedSessionId,
        activityId = activityId,
        startedAt = startedAt?.let(Instant::ofEpochMilli),
        endedAt = endedAt?.let(Instant::ofEpochMilli),
        pauses = pausesJson.toPauses(),
        durationSec = durationSec,
        distanceM = distanceM,
        rpe = rpe,
        source = source,
        createdAt = Instant.ofEpochMilli(createdAt),
    )

    private fun List<ExecutionPause>.toJson(): String = JSONArray().apply {
        forEach { pause ->
            put(
                JSONObject()
                    .put("startedAt", pause.startedAt.toEpochMilli())
                    .put("endedAt", pause.endedAt.toEpochMilli()),
            )
        }
    }.toString()

    private fun String.toPauses(): List<ExecutionPause> {
        val array = runCatching { JSONArray(this) }.getOrElse { return emptyList() }
        return List(array.length()) { index ->
            val row = array.getJSONObject(index)
            ExecutionPause(
                startedAt = Instant.ofEpochMilli(row.getLong("startedAt")),
                endedAt = Instant.ofEpochMilli(row.getLong("endedAt")),
            )
        }
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
    }
}

/** Raised inside the transaction when another writer already changed the session. */
private class SessionChangedException : RuntimeException("Session changed concurrently")
