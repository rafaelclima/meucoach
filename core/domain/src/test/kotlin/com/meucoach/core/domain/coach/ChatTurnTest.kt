package com.meucoach.core.domain.coach

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ChatTurnTest {
    private val now = Instant.parse("2026-11-20T12:00:00Z")

    @Test
    fun `resumo substitui o review do mesmo hash na timeline`() {
        val explain = turn("message-e1", ChatTurn.Origin.EXPLAIN, "explain-1", now.minusSeconds(300))
        val review = turn("message-r1", ChatTurn.Origin.REVIEW, "review-1", now.minusSeconds(200))
        val summary = turn(
            "summary-2026-11-09",
            ChatTurn.Origin.SUMMARY,
            "review-1",
            now.minusSeconds(100),
            weekStart = LocalDate.of(2026, 11, 9),
        )

        val visible = ChatTurns.visibleTurns(listOf(explain, review, summary))

        assertThat(visible.map { it.id }).containsExactly("summary-2026-11-09", "message-e1")
    }

    @Test
    fun `review sem resumo continua visivel como turn`() {
        val review = turn("message-r1", ChatTurn.Origin.REVIEW, "review-1", now)

        val visible = ChatTurns.visibleTurns(listOf(review))

        assertThat(visible).hasSize(1)
        val coach = visible.single() as ChatTurn.Coach
        assertThat(coach.origin).isEqualTo(ChatTurn.Origin.REVIEW)
        assertThat(coach.title).isEqualTo("Título de message-r1.")
    }

    @Test
    fun `resumo nao esconde review de outro payload`() {
        val review = turn("message-r2", ChatTurn.Origin.REVIEW, "review-2", now)
        val summary = turn(
            "summary-2026-11-09",
            ChatTurn.Origin.SUMMARY,
            "review-1",
            now,
            weekStart = LocalDate.of(2026, 11, 9),
        )

        val visible = ChatTurns.visibleTurns(listOf(review, summary))

        assertThat(visible.map { it.id }).containsExactlyInAnyOrder("message-r2", "summary-2026-11-09")
    }

    @Test
    fun `explicacao corrente e semana em revisao nao entram no arquivo`() {
        val current = turn("message-cur", ChatTurn.Origin.EXPLAIN, "explain-current", now)
        val summary = turn(
            "summary-2026-11-09",
            ChatTurn.Origin.SUMMARY,
            "review-current",
            now,
            weekStart = LocalDate.of(2026, 11, 9),
        )

        val visible = ChatTurns.visibleTurns(
            turns = listOf(current, summary),
            currentExplainHash = "explain-current",
            currentReviewWeek = LocalDate.of(2026, 11, 9),
        )

        assertThat(visible).isEmpty()
    }

    @Test
    fun `explicacao antiga e resumo de outra semana continuam no arquivo`() {
        val older = turn("message-old", ChatTurn.Origin.EXPLAIN, "explain-old", now.minusSeconds(60))
        val current = turn("message-cur", ChatTurn.Origin.EXPLAIN, "explain-current", now)
        val currentSummary = turn(
            "summary-2026-11-09",
            ChatTurn.Origin.SUMMARY,
            "review-current",
            now,
            weekStart = LocalDate.of(2026, 11, 9),
        )
        val olderSummary = turn(
            "summary-2026-11-02",
            ChatTurn.Origin.SUMMARY,
            "review-old",
            now.minusSeconds(120),
            weekStart = LocalDate.of(2026, 11, 2),
        )

        val visible = ChatTurns.visibleTurns(
            turns = listOf(current, older, currentSummary, olderSummary),
            currentExplainHash = "explain-current",
            currentReviewWeek = LocalDate.of(2026, 11, 9),
        )

        assertThat(visible.map { it.id }).containsExactly("message-old", "summary-2026-11-02")
    }

    @Test
    fun `empate de createdAt mostra o resumo antes`() {
        val review = turn("message-r1", ChatTurn.Origin.REVIEW, "review-1", now)
        val summary = turn(
            "summary-2026-11-09",
            ChatTurn.Origin.SUMMARY,
            "review-9",
            now,
            weekStart = LocalDate.of(2026, 11, 9),
        )

        val visible = ChatTurns.visibleTurns(listOf(review, summary))

        assertThat(visible.map { it.id }).containsExactly("summary-2026-11-09", "message-r1")
    }

    @Test
    fun `hash ausente nunca esconde o turn`() {
        val review = turn("message-r1", ChatTurn.Origin.REVIEW, null, now.minusSeconds(10))
        val summary = turn(
            "summary-2026-11-09",
            ChatTurn.Origin.SUMMARY,
            null,
            now,
            weekStart = LocalDate.of(2026, 11, 9),
        )
        val explain = turn("message-e1", ChatTurn.Origin.EXPLAIN, null, now.minusSeconds(20))

        val visible = ChatTurns.visibleTurns(
            turns = listOf(review, summary, explain),
            currentExplainHash = "explain-current",
        )

        assertThat(visible.map { it.id })
            .containsExactly("summary-2026-11-09", "message-r1", "message-e1")
    }

    @Test
    fun `timeline vazia continua vazia`() {
        assertThat(ChatTurns.visibleTurns(emptyList())).isEmpty()
    }

    @Test
    fun `turno you nunca e escondido pelas regras do arquivo`() {
        val current = turn("message-cur", ChatTurn.Origin.EXPLAIN, "explain-current", now)
        val you = ChatTurn.You(id = "you-1", body = "só tenho 30 min quinta", createdAt = now)

        val visible = ChatTurns.visibleTurns(
            turns = listOf(current, you),
            currentExplainHash = "explain-current",
        )

        assertThat(visible.map { it.id }).containsExactly("you-1")
    }

    @Test
    fun `turno you ordena junto com os coach por createdAt`() {
        val older = turn("message-old", ChatTurn.Origin.EXPLAIN, "explain-old", now.minusSeconds(60))
        val you = ChatTurn.You(id = "you-1", body = "perdi o treino", createdAt = now)

        val visible = ChatTurns.visibleTurns(listOf(older, you))

        assertThat(visible.map { it.id }).containsExactly("you-1", "message-old")
    }

    private fun turn(
        id: String,
        origin: ChatTurn.Origin,
        inputsHash: String?,
        createdAt: Instant,
        weekStart: LocalDate? = null,
    ) = ChatTurn.Coach(
        id = id,
        title = "Título de $id.",
        body = "Corpo de $id.",
        bullets = emptyList(),
        createdAt = createdAt,
        origin = origin,
        weekStart = weekStart,
        inputsHash = inputsHash,
    )
}
