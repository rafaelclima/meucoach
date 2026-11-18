package com.meucoach.core.data.coach

import androidx.room.withTransaction
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.CoachMessageEntity
import com.meucoach.core.data.db.entity.CoachSummaryEntity
import com.meucoach.core.data.id.UuidV7
import com.meucoach.core.domain.coach.CoachMemoryMessage
import com.meucoach.core.domain.coach.CoachMemoryPolicy
import com.meucoach.core.domain.coach.CoachMemorySummary
import com.meucoach.core.domain.coach.CoachMessageKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Rows removed by one lazy TTL pass. */
data class CoachMemoryPruneResult(val messages: Int, val summaries: Int) {
    val removedAnything: Boolean get() = messages > 0 || summaries > 0
}

/**
 * Local memory of the IA Coach (Room v19, ADR 0052).
 *
 * The repository only stores text that already passed
 * [com.meucoach.core.domain.coach.CoachResponseValidator]. It never produces
 * training content and never feeds load, pace or planning: reading a memory back
 * is copy context, and the engine remains the only authority.
 *
 * TTL is enforced lazily on every write and on [pruneExpired]; the app has no
 * background worker for it because the Coach tab is the only writer/reader.
 */
class CoachMemoryRepository(
    private val database: MeuCoachDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val idGenerator: () -> String = { UuidV7.generate(clock) },
) {
    suspend fun pruneExpired(): CoachMemoryPruneResult = database.withTransaction {
        val now = clock.millis()
        CoachMemoryPruneResult(
            messages = database.coachMessageDao().deleteOlderThan(messageCutoffMillis(now)),
            summaries = database.coachSummaryDao().deleteOlderThan(summaryCutoffMillis(now)),
        )
    }

    /**
     * Stores a validated turn. The same `(kind, inputsHash)` is written once;
     * reopening the tab or retrying does not duplicate the conversation.
     */
    suspend fun recordMessage(
        kind: CoachMessageKind,
        inputsHash: String,
        title: String,
        body: String,
        bullets: List<String>,
    ): Boolean = database.withTransaction {
        val now = clock.millis()
        database.coachMessageDao().deleteOlderThan(messageCutoffMillis(now))
        val inserted = database.coachMessageDao().insert(
            CoachMessageEntity(
                id = idGenerator(),
                kind = kind.name,
                inputsHash = inputsHash,
                title = title,
                body = body,
                bulletsJson = bulletsToJson(bullets),
                createdAt = now,
            ),
        )
        inserted != -1L
    }

    /** One compact summary per closed week; the latest validated narrative wins. */
    suspend fun recordSummary(
        weekStart: LocalDate,
        inputsHash: String,
        title: String,
        body: String,
        bullets: List<String>,
    ) {
        database.withTransaction {
            val now = clock.millis()
            database.coachSummaryDao().deleteOlderThan(summaryCutoffMillis(now))
            database.coachSummaryDao().upsert(
                CoachSummaryEntity(
                    weekStart = weekStart.toString(),
                    inputsHash = inputsHash,
                    title = title,
                    body = body,
                    bulletsJson = bulletsToJson(bullets),
                    createdAt = now,
                ),
            )
        }
    }

    /** Revoking the opt-in erases the whole Coach memory; training data is untouched. */
    suspend fun clearAll() {
        database.withTransaction {
            database.coachMessageDao().deleteAll()
            database.coachSummaryDao().deleteAll()
        }
    }

    /** Removes one turn; returns false when the row was already gone. */
    suspend fun deleteMessage(id: String): Boolean = database.coachMessageDao().deleteById(id) > 0

    /** Removes one weekly summary; returns false when the row was already gone. */
    suspend fun deleteSummary(weekStart: LocalDate): Boolean =
        database.coachSummaryDao().deleteByWeek(weekStart.toString()) > 0

    /**
     * Read-only history for the Coach tab (newest first).
     *
     * Rows written by older/corrupted versions are ignored instead of breaking
     * the list: the memory is copy, so a bad row must never take the tab down.
     */
    fun observeMessages(): Flow<List<CoachMemoryMessage>> = database
        .coachMessageDao()
        .observeAll()
        .map { rows -> rows.mapNotNull { it.toDomain() } }

    fun observeSummaries(): Flow<List<CoachMemorySummary>> = database
        .coachSummaryDao()
        .observeAll()
        .map { rows -> rows.mapNotNull { it.toDomain() } }

    private fun CoachMessageEntity.toDomain(): CoachMemoryMessage? {
        val messageKind = CoachMessageKind.entries.firstOrNull { it.name == kind } ?: return null
        return CoachMemoryMessage(
            id = id,
            kind = messageKind,
            title = title,
            body = body,
            bullets = bulletsFromJson(bulletsJson),
            createdAt = Instant.ofEpochMilli(createdAt),
        )
    }

    private fun CoachSummaryEntity.toDomain(): CoachMemorySummary? {
        val week = runCatching { LocalDate.parse(weekStart) }.getOrNull() ?: return null
        return CoachMemorySummary(
            weekStart = week,
            title = title,
            body = body,
            bullets = bulletsFromJson(bulletsJson),
            createdAt = Instant.ofEpochMilli(createdAt),
        )
    }

    private fun bulletsFromJson(json: String): List<String> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun messageCutoffMillis(nowMillis: Long): Long =
        CoachMemoryPolicy.messageCutoff(Instant.ofEpochMilli(nowMillis)).toEpochMilli()

    private fun summaryCutoffMillis(nowMillis: Long): Long =
        CoachMemoryPolicy.summaryCutoff(Instant.ofEpochMilli(nowMillis)).toEpochMilli()

    private fun bulletsToJson(bullets: List<String>): String = JSONArray().apply { bullets.forEach(::put) }.toString()
}
