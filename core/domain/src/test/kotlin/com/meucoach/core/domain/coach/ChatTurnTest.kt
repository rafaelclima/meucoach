package com.meucoach.core.domain.coach

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ChatTurnTest {
    private val now = Instant.parse("2026-11-20T12:00:00Z")

    @Test
    fun `memoria vira turns preservando as regras de visibilidade`() {
        val explain = message(CoachMessageKind.EXPLAIN, "explain-1", "Foco", now.minusSeconds(300))
        val review = message(CoachMessageKind.REVIEW, "review-1", "Revisão", now.minusSeconds(200))
        val summary = summary("review-1", now.minusSeconds(100))

        val turns = ChatTurns.fromMemory(listOf(explain, review), listOf(summary))

        assertThat(turns).hasSize(2)
        assertThat(turns.filterIsInstance<ChatTurn.Coach>().map { it.origin }).containsExactly(
            ChatTurn.Origin.SUMMARY,
            ChatTurn.Origin.EXPLAIN,
        )
    }

    @Test
    fun `explicacao corrente e semana em revisao nao entram no arquivo`() {
        val current = message(CoachMessageKind.EXPLAIN, "explain-current", "Atual", now)
        val summary = summary("review-current", now, LocalDate.of(2026, 11, 9))

        val turns = ChatTurns.fromMemory(
            messages = listOf(current),
            summaries = listOf(summary),
            currentExplainHash = "explain-current",
            currentReviewWeek = LocalDate.of(2026, 11, 9),
        )

        assertThat(turns).isEmpty()
    }

    @Test
    fun `review sem resumo continua visivel como turn`() {
        val review = message(CoachMessageKind.REVIEW, "review-1", "Revisão", now)

        val turns = ChatTurns.fromMemory(listOf(review), emptyList())

        assertThat(turns).hasSize(1)
        val coach = turns.single() as ChatTurn.Coach
        assertThat(coach.origin).isEqualTo(ChatTurn.Origin.REVIEW)
        assertThat(coach.title).isEqualTo("Revisão")
    }

    @Test
    fun `memoria vazia vira lista vazia`() {
        assertThat(ChatTurns.fromMemory(emptyList(), emptyList())).isEmpty()
    }

    private fun message(kind: CoachMessageKind, inputsHash: String, title: String, createdAt: Instant) =
        CoachMemoryMessage(
            id = "msg-$inputsHash",
            kind = kind,
            inputsHash = inputsHash,
            title = title,
            body = "Corpo de $title.",
            bullets = emptyList(),
            createdAt = createdAt,
        )

    private fun summary(inputsHash: String, createdAt: Instant, weekStart: LocalDate = LocalDate.of(2026, 11, 9)) =
        CoachMemorySummary(
            weekStart = weekStart,
            inputsHash = inputsHash,
            title = "Resumo",
            body = "Corpo do resumo.",
            bullets = emptyList(),
            createdAt = createdAt,
        )
}
