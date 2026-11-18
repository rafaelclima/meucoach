package com.meucoach.core.domain.coach

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CoachHistoryTest {
    private val createdAt = Instant.parse("2026-11-18T12:00:00Z")

    @Test
    fun `summary replaces the review turn with the same inputs hash`() {
        val explain = message(CoachMessageKind.EXPLAIN, "explain-1", "Foco")
        val review = message(CoachMessageKind.REVIEW, "review-1", "Revisão")
        val summary = summary("review-1")

        val visible = CoachHistory.view(listOf(explain, review), listOf(summary))

        assertThat(visible.messages).containsExactly(explain)
        assertThat(visible.summaries).containsExactly(summary)
    }

    @Test
    fun `review stays visible when the summary is absent`() {
        val review = message(CoachMessageKind.REVIEW, "review-1", "Revisão")

        val visible = CoachHistory.view(listOf(review), emptyList())

        assertThat(visible.messages).containsExactly(review)
    }

    @Test
    fun `summary does not hide a review from another payload`() {
        val review = message(CoachMessageKind.REVIEW, "review-2", "Revisão")
        val summary = summary("review-1")

        val visible = CoachHistory.view(listOf(review), listOf(summary))

        assertThat(visible.messages).containsExactly(review)
    }

    @Test
    fun `current explanation and current review week are not repeated in the archive`() {
        val currentExplain = message(CoachMessageKind.EXPLAIN, "explain-current", "Atual")
        val olderExplain = message(CoachMessageKind.EXPLAIN, "explain-old", "Antiga")
        val currentSummary = summary("review-current", LocalDate.of(2026, 11, 9))
        val olderSummary = summary("review-old", LocalDate.of(2026, 11, 2))

        val visible = CoachHistory.view(
            messages = listOf(currentExplain, olderExplain),
            summaries = listOf(currentSummary, olderSummary),
            currentExplainHash = "explain-current",
            currentReviewWeek = LocalDate.of(2026, 11, 9),
        )

        assertThat(visible.messages).containsExactly(olderExplain)
        assertThat(visible.summaries).containsExactly(olderSummary)
        assertThat(visible.isEmpty).isFalse()
    }

    @Test
    fun `archive is empty when everything is already on screen`() {
        val visible = CoachHistory.view(
            messages = listOf(message(CoachMessageKind.EXPLAIN, "explain-current", "Atual")),
            summaries = listOf(summary("review-current", LocalDate.of(2026, 11, 9))),
            currentExplainHash = "explain-current",
            currentReviewWeek = LocalDate.of(2026, 11, 9),
        )

        assertThat(visible.isEmpty).isTrue()
    }

    private fun message(kind: CoachMessageKind, hash: String, title: String) = CoachMemoryMessage(
        id = "$kind-$hash",
        kind = kind,
        inputsHash = hash,
        title = title,
        body = "Texto validado.",
        bullets = emptyList(),
        createdAt = createdAt,
    )

    private fun summary(hash: String, weekStart: LocalDate = LocalDate.of(2026, 11, 9)) = CoachMemorySummary(
        weekStart = weekStart,
        inputsHash = hash,
        title = "Semana",
        body = "Texto validado.",
        bullets = emptyList(),
        createdAt = createdAt,
    )
}
