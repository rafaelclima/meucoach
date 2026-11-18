package com.meucoach.core.data.coach

import androidx.room.withTransaction
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.CoachMessageEntity
import com.meucoach.core.data.db.entity.CoachSummaryEntity
import com.meucoach.core.data.id.UuidV7
import com.meucoach.core.domain.coach.CoachMemoryPolicy
import com.meucoach.core.domain.coach.CoachMessageKind
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

    private fun messageCutoffMillis(nowMillis: Long): Long =
        CoachMemoryPolicy.messageCutoff(Instant.ofEpochMilli(nowMillis)).toEpochMilli()

    private fun summaryCutoffMillis(nowMillis: Long): Long =
        CoachMemoryPolicy.summaryCutoff(Instant.ofEpochMilli(nowMillis)).toEpochMilli()

    private fun bulletsToJson(bullets: List<String>): String = JSONArray().apply { bullets.forEach(::put) }.toString()
}
