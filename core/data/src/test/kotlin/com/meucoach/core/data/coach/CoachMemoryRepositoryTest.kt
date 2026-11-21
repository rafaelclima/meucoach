package com.meucoach.core.data.coach

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.ChatTurnEntity
import com.meucoach.core.domain.coach.ChatTurn
import com.meucoach.core.domain.coach.CoachMessageKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoachMemoryRepositoryTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var clock: MutableClock
    private lateinit var memory: CoachMemoryRepository
    private var idCounter = 0
    private val now = Instant.parse("2026-11-18T12:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        clock = MutableClock(now)
        memory = CoachMemoryRepository(database, clock) { "message-${++idCounter}" }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `records messages as turns with the origin TTL`(): Unit = runBlocking {
        assertThat(memory.recordMessage(CoachMessageKind.EXPLAIN, "hash-1", "Foco", "40 min", listOf("Base")))
            .isTrue()

        val turns = database.chatTurnDao().getAll()

        assertThat(turns).hasSize(1)
        val turn = turns.single()
        assertThat(turn.id).isEqualTo("message-message-1")
        assertThat(turn.origin).isEqualTo("EXPLAIN")
        assertThat(turn.inputsHash).isEqualTo("hash-1")
        assertThat(turn.body).isEqualTo("40 min")
        assertThat(turn.bulletsJson).isEqualTo("[\"Base\"]")
        assertThat(turn.createdAt).isEqualTo(now.toEpochMilli())
        assertThat(turn.expiresAt).isEqualTo(now.plus(Duration.ofDays(90)).toEpochMilli())
    }

    @Test
    fun `reopening the same motor context does not duplicate the turn`(): Unit = runBlocking {
        assertThat(memory.recordMessage(CoachMessageKind.EXPLAIN, "hash-1", "Foco", "40 min", emptyList()))
            .isTrue()
        clock.instant = now.plusSeconds(30)

        assertThat(memory.recordMessage(CoachMessageKind.EXPLAIN, "hash-1", "Foco", "40 min", emptyList()))
            .isFalse()

        val turns = database.chatTurnDao().getAll()
        assertThat(turns).hasSize(1)
        assertThat(turns.single().createdAt).isEqualTo(now.toEpochMilli())
    }

    @Test
    fun `keeps one summary per week and the latest validated narrative wins`(): Unit = runBlocking {
        memory.recordSummary(
            LocalDate.of(2026, 11, 2),
            "review-1",
            "Semana 1",
            "3 de 4",
            listOf("Adesão"),
        )
        memory.recordSummary(LocalDate.of(2026, 11, 9), "review-2", "Semana 2", "4 de 4", emptyList())
        clock.instant = now.plusSeconds(60)
        memory.recordSummary(
            LocalDate.of(2026, 11, 2),
            "review-1b",
            "Semana 1 revisada",
            "3 de 4, volume ok",
            emptyList(),
        )

        val turns = database.chatTurnDao().getAll()

        assertThat(turns.map { it.id })
            .containsExactly("summary-2026-11-02", "summary-2026-11-09")
        val rewritten = turns.single { it.id == "summary-2026-11-02" }
        assertThat(rewritten.title).isEqualTo("Semana 1 revisada")
        assertThat(rewritten.inputsHash).isEqualTo("review-1b")
        assertThat(rewritten.weekStart).isEqualTo("2026-11-02")
        assertThat(rewritten.origin).isEqualTo("SUMMARY")
        assertThat(rewritten.expiresAt)
            .isEqualTo(now.plusSeconds(60).plus(Duration.ofDays(180)).toEpochMilli())
    }

    @Test
    fun `records mirror reviews and summaries under stable ids`(): Unit = runBlocking {
        memory.recordMessage(CoachMessageKind.REVIEW, "review-1", "Revisão", "3 de 4", emptyList())
        memory.recordSummary(LocalDate.of(2026, 11, 2), "review-1", "Semana", "3 de 4", emptyList())

        val turns = database.chatTurnDao().getAll()

        assertThat(turns.map { it.id }).containsExactly("summary-2026-11-02", "message-message-1")
        assertThat(turns.single { it.id == "message-message-1" }.origin).isEqualTo("REVIEW")
    }

    @Test
    fun `prunes expired turns by expiresAt and keeps the boundary`(): Unit = runBlocking {
        database.chatTurnDao().insert(turn("t-old", "EXPLAIN", now.minus(Duration.ofDays(91))))
        database.chatTurnDao().insert(turn("t-edge", "EXPLAIN", now.minus(Duration.ofDays(89))))

        val pruned = memory.pruneExpired()

        assertThat(pruned.turns).isEqualTo(1)
        assertThat(pruned.removedAnything).isTrue()
        assertThat(database.chatTurnDao().getAll().map { it.id }).containsExactly("t-edge")
    }

    @Test
    fun `writing prunes expired turns before inserting`(): Unit = runBlocking {
        database.chatTurnDao().insert(
            turn("t-old", "EXPLAIN", now.minus(Duration.ofDays(120)), inputsHash = "hash-new"),
        )

        assertThat(memory.recordMessage(CoachMessageKind.EXPLAIN, "hash-new", "Foco", "40 min", emptyList()))
            .isTrue()

        assertThat(database.chatTurnDao().getAll().map { it.inputsHash }).containsExactly("hash-new")
    }

    @Test
    fun `clearing erases every turn`(): Unit = runBlocking {
        memory.recordMessage(CoachMessageKind.EXPLAIN, "hash-1", "Foco", "40 min", emptyList())
        memory.recordSummary(LocalDate.of(2026, 11, 2), "review-1", "Semana", "3 de 4", emptyList())

        memory.clearAll()

        assertThat(database.chatTurnDao().getAll()).isEmpty()
    }

    @Test
    fun `observes turns newest-first skipping corrupted rows`(): Unit = runBlocking {
        database.chatTurnDao().insert(turn("t-old", "EXPLAIN", now.minusSeconds(60)))
        database.chatTurnDao().insert(turn("t-new", "SUMMARY", now, weekStart = "2026-11-09"))
        database.chatTurnDao().insert(turn("t-null-hash", "EXPLAIN", now.minusSeconds(30), inputsHash = null))
        database.chatTurnDao().insert(turn("t-bad-role", "EXPLAIN", now, role = "YOU"))
        database.chatTurnDao().insert(turn("t-bad-origin", "YOU", now))
        database.chatTurnDao().insert(
            turn("t-json", "EXPLAIN", now, body = """{"":": "legado"}"""),
        )
        database.chatTurnDao().insert(turn("t-blank", "EXPLAIN", now, title = "  "))

        val turns = memory.observeTurns().first()

        assertThat(turns.map { it.id }).containsExactly("t-new", "t-null-hash", "t-old")
        assertThat(turns.filterIsInstance<ChatTurn.Coach>().map { it.origin }).containsExactly(
            ChatTurn.Origin.SUMMARY,
            ChatTurn.Origin.EXPLAIN,
            ChatTurn.Origin.EXPLAIN,
        )
        assertThat(turns.single { it.id == "t-null-hash" }.let { it as ChatTurn.Coach }.inputsHash).isNull()
    }

    @Test
    fun `deletes one turn by id without touching the rest`(): Unit = runBlocking {
        memory.recordMessage(CoachMessageKind.EXPLAIN, "hash-1", "Foco", "40 min", emptyList())
        clock.instant = now.plusSeconds(60)
        memory.recordMessage(CoachMessageKind.REVIEW, "hash-2", "Semana", "3 de 4", emptyList())

        assertThat(memory.deleteTurn("message-message-1")).isTrue()

        assertThat(memory.observeTurns().first().map { it.id })
            .containsExactly("message-message-2")
        assertThat(memory.deleteTurn("message-message-1")).isFalse()
    }

    private fun turn(
        id: String,
        origin: String,
        createdAt: Instant,
        weekStart: String? = null,
        body: String = "Corpo",
        title: String = "Titulo",
        role: String = "COACH",
        inputsHash: String? = "hash-$id",
    ) = ChatTurnEntity(
        id = id,
        role = role,
        title = title,
        body = body,
        bulletsJson = "[]",
        origin = origin,
        weekStart = weekStart,
        createdAt = createdAt.toEpochMilli(),
        expiresAt = createdAt.plus(Duration.ofDays(90)).toEpochMilli(),
        inputsHash = inputsHash,
    )

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = instant
    }
}
