package com.meucoach.core.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.RaceEventEntity
import com.meucoach.core.data.id.UuidV7
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.RaceStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Result of creating/updating/resolving a race event. */
sealed interface RaceEventOutcome {
    data class Saved(val event: RaceEvent) : RaceEventOutcome

    data class Rejected(val message: String) : RaceEventOutcome

    /** Only one PLANNED event is allowed; the caller must resolve the current one first. */
    data class ActiveEventExists(val active: RaceEvent) : RaceEventOutcome

    data class ActivityAlreadyLinked(val message: String) : RaceEventOutcome
}

data class NewRaceEvent(val distance: RaceDistance, val targetDate: LocalDate, val targetTimeSec: Int? = null)

/**
 * Owns race events and their optional activity link (ADR 0030).
 *
 * The database enforces at most one PLANNED event and one activity per event;
 * the cross-table rule with `session_executions` is checked inside the same
 * transaction because SQLite cannot express it as an index.
 */
class RaceEventRepository(
    private val database: MeuCoachDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val idGenerator: () -> String = { UuidV7.generate(clock) },
) {
    private val events = database.raceEventDao()

    fun observeAll(): Flow<List<RaceEvent>> = events.observeAll().map { rows -> rows.map { it.toModel() } }

    suspend fun listAll(): List<RaceEvent> = events.getAll().map { it.toModel() }

    suspend fun getPlanned(): RaceEvent? = events.findPlanned()?.toModel()

    suspend fun getForDate(date: LocalDate): RaceEvent? = events.findForDate(date.toString()).firstOrNull()?.toModel()

    suspend fun getDatesBetween(start: LocalDate, end: LocalDate): Set<LocalDate> =
        events.datesBetween(start.toString(), end.toString()).map(LocalDate::parse).toSet()

    suspend fun create(request: NewRaceEvent): RaceEventOutcome = runCatching {
        database.withTransaction {
            val active = events.findPlanned()
            if (active != null) return@withTransaction RaceEventOutcome.ActiveEventExists(active.toModel())
            val now = clock.instant()
            val entity = RaceEventEntity(
                id = idGenerator(),
                distance = request.distance.name,
                targetDate = request.targetDate.toString(),
                targetTimeSec = request.targetTimeSec,
                status = RaceStatus.PLANNED.name,
                activityId = null,
                activeKey = ACTIVE_KEY,
                createdAt = now.toEpochMilli(),
                updatedAt = now.toEpochMilli(),
                resolvedAt = null,
            )
            events.insert(entity)
            RaceEventOutcome.Saved(entity.toModel())
        }
    }.getOrElse { error ->
        when (error) {
            is SQLiteConstraintException -> {
                val active = events.findPlanned()?.toModel()
                if (active != null) {
                    RaceEventOutcome.ActiveEventExists(active)
                } else {
                    RaceEventOutcome.Rejected("Não foi possível salvar a prova.")
                }
            }

            else -> throw error
        }
    }

    suspend fun update(id: String, request: NewRaceEvent): RaceEventOutcome = database.withTransaction {
        val entity = events.findById(id)
            ?: return@withTransaction RaceEventOutcome.Rejected("Prova não encontrada.")
        if (entity.toModel().status != RaceStatus.PLANNED) {
            return@withTransaction RaceEventOutcome.Rejected("Uma prova encerrada não pode ser editada.")
        }
        val updated = entity.copy(
            distance = request.distance.name,
            targetDate = request.targetDate.toString(),
            targetTimeSec = request.targetTimeSec,
            updatedAt = clock.millis(),
        )
        events.update(updated)
        RaceEventOutcome.Saved(updated.toModel())
    }

    suspend fun complete(id: String, activityId: String? = null): RaceEventOutcome = runCatching {
        database.withTransaction {
            val entity = events.findById(id)
                ?: return@withTransaction RaceEventOutcome.Rejected("Prova não encontrada.")
            if (entity.toModel().status != RaceStatus.PLANNED) {
                return@withTransaction RaceEventOutcome.Rejected("Esta prova já foi encerrada.")
            }
            if (activityId != null) {
                if (database.activityDao().findById(activityId) == null) {
                    return@withTransaction RaceEventOutcome.Rejected(
                        "Atividade não encontrada ou na lixeira.",
                    )
                }
                if (events.findByActivity(activityId) != null ||
                    database.sessionExecutionDao().findByActivity(activityId) != null
                ) {
                    return@withTransaction RaceEventOutcome.ActivityAlreadyLinked(
                        "Esta corrida já está vinculada a outro treino ou prova.",
                    )
                }
            }
            val now = clock.millis()
            val updated = entity.copy(
                status = RaceStatus.COMPLETED.name,
                activityId = activityId,
                activeKey = null,
                updatedAt = now,
                resolvedAt = now,
            )
            events.update(updated)
            RaceEventOutcome.Saved(updated.toModel())
        }
    }.getOrElse { error ->
        when (error) {
            is SQLiteConstraintException -> RaceEventOutcome.ActivityAlreadyLinked(
                "Esta corrida já está vinculada a outro treino ou prova.",
            )

            else -> throw error
        }
    }

    suspend fun cancel(id: String): RaceEventOutcome = resolve(id, RaceStatus.CANCELLED)

    suspend fun noShow(id: String): RaceEventOutcome = resolve(id, RaceStatus.NO_SHOW)

    suspend fun deleteAll(): Int = events.deleteAll()

    private suspend fun resolve(id: String, status: RaceStatus): RaceEventOutcome = database.withTransaction {
        require(status != RaceStatus.PLANNED) { "Resolving a race event requires a terminal status" }
        val entity = events.findById(id)
            ?: return@withTransaction RaceEventOutcome.Rejected("Prova não encontrada.")
        if (entity.toModel().status != RaceStatus.PLANNED) {
            return@withTransaction RaceEventOutcome.Rejected("Esta prova já foi encerrada.")
        }
        val now = clock.millis()
        val updated = entity.copy(
            status = status.name,
            activityId = null,
            activeKey = null,
            updatedAt = now,
            resolvedAt = now,
        )
        events.update(updated)
        RaceEventOutcome.Saved(updated.toModel())
    }

    private fun RaceEventEntity.toModel() = RaceEvent(
        id = id,
        distance = RaceDistance.valueOf(distance),
        targetDate = LocalDate.parse(targetDate),
        targetTimeSec = targetTimeSec,
        status = RaceStatus.valueOf(status),
        activityId = activityId,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        resolvedAt = resolvedAt?.let(Instant::ofEpochMilli),
    )

    private companion object {
        const val ACTIVE_KEY = "ACTIVE"
    }
}
