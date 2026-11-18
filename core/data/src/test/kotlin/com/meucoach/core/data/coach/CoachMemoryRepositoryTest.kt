package com.meucoach.core.data.coach

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.CoachMessageEntity
import com.meucoach.core.data.db.entity.CoachSummaryEntity
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
    fun `records messages and reads them in chronological order`(): Unit = runBlocking {
        assertThat(memory.recordMessage(CoachMessageKind.EXPLAIN, "hash-1", "Foco", "40 min", listOf("Base")))
            .isTrue()
        clock.instant = now.plusSeconds(60)
        assertThat(memory.recordMessage(CoachMessageKind.REVIEW, "hash-2", "Semana", "3 de 4", emptyList()))
            .isTrue()

        val messages = database.coachMessageDao().getAll()

        assertThat(messages.map { it.kind }).containsExactly("EXPLAIN", "REVIEW")
        assertThat(messages.first().body).isEqualTo("40 min")
        assertThat(messages.first().bulletsJson).isEqualTo("[\"Base\"]")
        assertThat(messages.last().createdAt).isEqualTo(clock.millis())
    }

    @Test
    fun `reopening the same motor context does not duplicate the conversation`(): Unit = runBlocking {
        assertThat(memory.recordMessage(CoachMessageKind.EXPLAIN, "hash-1", "Foco", "40 min", emptyList()))
            .isTrue()
        clock.instant = now.plusSeconds(30)

        assertThat(memory.recordMessage(CoachMessageKind.EXPLAIN, "hash-1", "Foco", "40 min", emptyList()))
            .isFalse()

        val messages = database.coachMessageDao().getAll()
        assertThat(messages).hasSize(1)
        assertThat(messages.single().createdAt).isEqualTo(now.toEpochMilli())
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

        val summaries = database.coachSummaryDao().getAll()

        assertThat(summaries.map { it.weekStart })
            .containsExactly("2026-11-09", "2026-11-02")
        assertThat(summaries.last().title).isEqualTo("Semana 1 revisada")
        assertThat(summaries.last().inputsHash).isEqualTo("review-1b")
    }

    @Test
    fun `prunes rows older than the TTL and keeps the boundary`(): Unit = runBlocking {
        database.coachMessageDao().insert(message("old", "hash-old", now.minus(Duration.ofDays(91))))
        database.coachMessageDao().insert(message("edge", "hash-edge", now.minus(Duration.ofDays(90))))
        database.coachSummaryDao().upsert(summary("2026-08-01", now.minus(Duration.ofDays(181))))
        database.coachSummaryDao().upsert(summary("2026-08-02", now.minus(Duration.ofDays(180))))

        val pruned = memory.pruneExpired()

        assertThat(pruned.messages).isEqualTo(1)
        assertThat(pruned.summaries).isEqualTo(1)
        assertThat(pruned.removedAnything).isTrue()
        assertThat(database.coachMessageDao().getAll().map { it.id }).containsExactly("edge")
        assertThat(database.coachSummaryDao().getAll().map { it.weekStart }).containsExactly("2026-08-02")
    }

    @Test
    fun `writing prunes expired messages before inserting`(): Unit = runBlocking {
        database.coachMessageDao().insert(message("old", "hash-old", now.minus(Duration.ofDays(120))))

        memory.recordMessage(CoachMessageKind.EXPLAIN, "hash-new", "Foco", "40 min", emptyList())

        assertThat(database.coachMessageDao().getAll().map { it.inputsHash })
            .containsExactly("hash-new")
    }

    @Test
    fun `clearing erases messages and summaries`(): Unit = runBlocking {
        memory.recordMessage(CoachMessageKind.EXPLAIN, "hash-1", "Foco", "40 min", emptyList())
        memory.recordSummary(LocalDate.of(2026, 11, 2), "review-1", "Semana", "3 de 4", emptyList())

        memory.clearAll()

        assertThat(database.coachMessageDao().getAll()).isEmpty()
        assertThat(database.coachSummaryDao().getAll()).isEmpty()
    }

    @Test
    fun `observes the memory as newest-first read-only rows`(): Unit = runBlocking {
        memory.recordMessage(
            CoachMessageKind.EXPLAIN,
            "hash-1",
            "Foco",
            "40 min",
            listOf("Base", "Leve"),
        )
        clock.instant = now.plusSeconds(60)
        memory.recordMessage(CoachMessageKind.REVIEW, "hash-2", "Semana", "3 de 4", emptyList())
        memory.recordSummary(LocalDate.of(2026, 11, 2), "review-1", "Semana 1", "3 de 4", emptyList())
        memory.recordSummary(LocalDate.of(2026, 11, 9), "review-2", "Semana 2", "4 de 4", listOf("Adesão"))

        val messages = memory.observeMessages().first()

        assertThat(messages.map { it.kind })
            .containsExactly(CoachMessageKind.REVIEW, CoachMessageKind.EXPLAIN)
        assertThat(messages.first().createdAt).isEqualTo(now.plusSeconds(60))
        assertThat(messages.last().bullets).containsExactly("Base", "Leve")

        val summaries = memory.observeSummaries().first()

        assertThat(summaries.map { it.weekStart })
            .containsExactly(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 2))
        assertThat(summaries.first().bullets).containsExactly("Adesão")
    }

    @Test
    fun `deletes one message without touching the rest`(): Unit = runBlocking {
        memory.recordMessage(CoachMessageKind.EXPLAIN, "hash-1", "Foco", "40 min", emptyList())
        clock.instant = now.plusSeconds(60)
        memory.recordMessage(CoachMessageKind.REVIEW, "hash-2", "Semana", "3 de 4", emptyList())
        val explain = memory.observeMessages().first().single { it.kind == CoachMessageKind.EXPLAIN }

        assertThat(memory.deleteMessage(explain.id)).isTrue()

        assertThat(memory.observeMessages().first().map { it.kind })
            .containsExactly(CoachMessageKind.REVIEW)
        assertThat(memory.deleteMessage(explain.id)).isFalse()
    }

    @Test
    fun `deletes one summary by week without touching the rest`(): Unit = runBlocking {
        memory.recordSummary(LocalDate.of(2026, 11, 2), "review-1", "Semana 1", "3 de 4", emptyList())
        memory.recordSummary(LocalDate.of(2026, 11, 9), "review-2", "Semana 2", "4 de 4", emptyList())

        assertThat(memory.deleteSummary(LocalDate.of(2026, 11, 2))).isTrue()

        assertThat(memory.observeSummaries().first().map { it.weekStart })
            .containsExactly(LocalDate.of(2026, 11, 9))
        assertThat(memory.deleteSummary(LocalDate.of(2026, 11, 2))).isFalse()
    }

    @Test
    fun `unreadable memory rows are ignored instead of breaking the history`(): Unit = runBlocking {
        database.coachMessageDao().insert(
            CoachMessageEntity(
                id = "bad-kind",
                kind = "UNKNOWN",
                inputsHash = "hash",
                title = "T",
                body = "B",
                bulletsJson = "[]",
                createdAt = now.toEpochMilli(),
            ),
        )
        database.coachSummaryDao().upsert(
            CoachSummaryEntity(
                weekStart = "not-a-date",
                inputsHash = "hash",
                title = "T",
                body = "B",
                bulletsJson = "[]",
                createdAt = now.toEpochMilli(),
            ),
        )

        assertThat(memory.observeMessages().first()).isEmpty()
        assertThat(memory.observeSummaries().first()).isEmpty()
    }

    private fun message(id: String, hash: String, createdAt: Instant) = CoachMessageEntity(
        id = id,
        kind = CoachMessageKind.EXPLAIN.name,
        inputsHash = hash,
        title = "Foco",
        body = "40 min",
        bulletsJson = "[]",
        createdAt = createdAt.toEpochMilli(),
    )

    private fun summary(weekStart: String, createdAt: Instant) = CoachSummaryEntity(
        weekStart = weekStart,
        inputsHash = "review-$weekStart",
        title = "Semana",
        body = "3 de 4",
        bulletsJson = "[]",
        createdAt = createdAt.toEpochMilli(),
    )

    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = instant
    }
}
