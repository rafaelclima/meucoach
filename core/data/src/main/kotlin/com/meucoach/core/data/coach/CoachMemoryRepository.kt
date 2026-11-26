package com.meucoach.core.data.coach

import androidx.room.withTransaction
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.ChatTurnEntity
import com.meucoach.core.data.id.UuidV7
import com.meucoach.core.domain.coach.ChatTurn
import com.meucoach.core.domain.coach.CoachMemoryPolicy
import com.meucoach.core.domain.coach.CoachMessageKind
import com.meucoach.core.domain.coach.FreeInputParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Rows removed by one lazy TTL pass over the single Coach table (Room v21). */
data class CoachMemoryPruneResult(val turns: Int) {
    val removedAnything: Boolean get() = turns > 0
}

/**
 * Local memory of the IA Coach (Room v21, ADR 0055).
 *
 * The repository only stores text that already passed
 * [com.meucoach.core.domain.coach.CoachResponseValidator]. It never produces
 * training content and never feeds load, pace or planning: reading a memory
 * back is copy context, and the engine remains the only authority.
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
        CoachMemoryPruneResult(turns = database.chatTurnDao().deleteOlderThan(clock.millis()))
    }

    /**
     * Stores a validated turn. The same `(origin, inputsHash)` is replaced in
     * place: reopening the tab does not duplicate the conversation, and a
     * successful retry replaces the deterministic fallback answer (D9).
     */
    suspend fun recordMessage(
        kind: CoachMessageKind,
        inputsHash: String,
        title: String,
        body: String,
        bullets: List<String>,
    ): Boolean = database.withTransaction {
        val now = clock.millis()
        database.chatTurnDao().deleteOlderThan(now)
        database.chatTurnDao().deleteByOriginAndInputsHash(kind.name, inputsHash)
        val inserted = database.chatTurnDao().insert(
            turnEntity("message-${idGenerator()}", kind.name, inputsHash, title, body, bullets, now),
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
            database.chatTurnDao().deleteOlderThan(now)
            database.chatTurnDao().deleteById("summary-$weekStart")
            database.chatTurnDao().insert(
                turnEntity(
                    id = "summary-$weekStart",
                    origin = ChatTurn.Origin.SUMMARY.name,
                    inputsHash = inputsHash,
                    title = title,
                    body = body,
                    bullets = bullets,
                    now = now,
                    weekStart = weekStart.toString(),
                ),
            )
        }
    }

    /**
     * Stores the athlete's typed turn next to the answers (Room v22, ADR 0056).
     *
     * The UI passes the parser-sanitized text; the repository trims and caps
     * it again defensively and stores nothing when it is blank. `YOU` rows
     * carry no origin or motor hash, live 90 days and are wiped by the same
     * `clearAll`/revocation path as Coach turns. They never enter a prompt.
     */
    suspend fun recordYouTurn(body: String): Boolean = database.withTransaction {
        val clean = body.trim().take(FreeInputParser.MAX_CHARS)
        if (clean.isEmpty()) {
            false
        } else {
            val now = clock.millis()
            database.chatTurnDao().deleteOlderThan(now)
            database.chatTurnDao().insert(
                ChatTurnEntity(
                    id = "you-${idGenerator()}",
                    role = TURN_ROLE_YOU,
                    title = "",
                    body = clean,
                    bulletsJson = "[]",
                    origin = null,
                    weekStart = null,
                    createdAt = now,
                    expiresAt = CoachMemoryPolicy.youExpiresAt(Instant.ofEpochMilli(now)).toEpochMilli(),
                    inputsHash = null,
                ),
            )
            true
        }
    }

    /** Revoking the opt-in erases the whole Coach memory; training data is untouched. */
    suspend fun clearAll() {
        database.withTransaction {
            database.chatTurnDao().deleteAll()
        }
    }

    /** Removes one unified turn; returns false when the row was already gone. */
    suspend fun deleteTurn(id: String): Boolean = database.chatTurnDao().deleteById(id) > 0

    /**
     * Unified timeline for the Coach tab (newest first). Same corruption
     * filters as before the single table: blank titles/bodies, provider JSON
     * objects and unknown roles/origins are ignored instead of breaking the
     * list. Visibility rules (current explanation, week under review, review
     * covered by its summary) live in `ChatTurns.visibleTurns`, applied by
     * the caller with its live state.
     */
    fun observeTurns(): Flow<List<ChatTurn>> = database
        .chatTurnDao()
        .observeAll()
        .map { rows -> rows.mapNotNull { it.toTurn() } }

    private fun ChatTurnEntity.toTurn(): ChatTurn? = when (role) {
        TURN_ROLE_YOU -> youTurn()
        TURN_ROLE_COACH -> coachTurn()
        else -> null
    }

    /**
     * `YOU` rows need no origin; a `YOU` role with a Coach origin is a corrupt
     * write and stays hidden like any other unknown row.
     */
    private fun ChatTurnEntity.youTurn(): ChatTurn.You? {
        if (origin != null || body.isBlank()) return null
        return ChatTurn.You(id = id, body = body, createdAt = Instant.ofEpochMilli(createdAt))
    }

    private fun ChatTurnEntity.coachTurn(): ChatTurn.Coach? {
        if (title.isBlank() || body.isBlank() || body.isLegacyJsonObject()) return null
        val turnOrigin = origin?.let { ChatTurn.Origin.entries.firstOrNull { entry -> entry.name == it } }
            ?: return null
        return ChatTurn.Coach(
            id = id,
            title = title,
            body = body,
            bullets = bulletsFromJson(bulletsJson),
            createdAt = Instant.ofEpochMilli(createdAt),
            origin = turnOrigin,
            weekStart = weekStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            inputsHash = inputsHash,
        )
    }

    private fun turnEntity(
        id: String,
        origin: String,
        inputsHash: String,
        title: String,
        body: String,
        bullets: List<String>,
        now: Long,
        weekStart: String? = null,
    ): ChatTurnEntity {
        val createdAt = Instant.ofEpochMilli(now)
        val turnOrigin = ChatTurn.Origin.valueOf(origin)
        return ChatTurnEntity(
            id = id,
            role = TURN_ROLE_COACH,
            title = title,
            body = body,
            bulletsJson = bulletsToJson(bullets),
            origin = origin,
            weekStart = weekStart,
            createdAt = now,
            expiresAt = CoachMemoryPolicy.turnExpiresAt(createdAt, turnOrigin).toEpochMilli(),
            inputsHash = inputsHash,
        )
    }

    private companion object {
        const val TURN_ROLE_COACH = "COACH"
        const val TURN_ROLE_YOU = "YOU"
    }

    /**
     * Older builds could persist a provider payload with `body` as an object.
     * Validated Coach prose never starts with `{`; hiding those rows keeps the
     * history readable until the TTL removes them (no destructive migration).
     */
    private fun String.isLegacyJsonObject(): Boolean = trim().startsWith("{")

    private fun bulletsFromJson(json: String): List<String> {
        val array = runCatching { JSONArray(json) }.getOrElse { return emptyList() }
        return buildList {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun bulletsToJson(bullets: List<String>): String = JSONArray().apply { bullets.forEach(::put) }.toString()
}
