package com.meucoach.core.domain.coach

import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WorkoutType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class CoachNextResolverTest {
    private val monday = LocalDate.of(2026, 11, 16)

    @Test
    fun `hoje pendente vence a proxima sessao futura`() {
        val today = session(monday, SessionStatus.PLANNED)
        val future = session(monday.plusDays(2), SessionStatus.PLANNED)

        val next = CoachNextResolver.resolve(listOf(today, future), monday)

        assertThat(next?.session).isEqualTo(today)
        assertThat(next?.isToday).isTrue()
    }

    @Test
    fun `sem pendencia hoje devolve a proxima futura planejada`() {
        val done = session(monday, SessionStatus.DONE)
        val thursday = session(monday.plusDays(3), SessionStatus.PLANNED)
        val saturday = session(monday.plusDays(5), SessionStatus.PLANNED)

        val next = CoachNextResolver.resolve(listOf(done, saturday, thursday), monday)

        assertThat(next?.session).isEqualTo(thursday)
        assertThat(next?.isToday).isFalse()
    }

    @Test
    fun `ignora concluidas, encerradas e passado planejado`() {
        val past = session(monday.minusDays(2), SessionStatus.PLANNED)
        val closed = session(monday.plusDays(1), SessionStatus.CLOSED)
        val done = session(monday.plusDays(2), SessionStatus.DONE)

        val next = CoachNextResolver.resolve(listOf(past, closed, done), monday)

        assertThat(next).isNull()
    }

    @Test
    fun `sem sessoes nao ha proximo`() {
        assertThat(CoachNextResolver.resolve(emptyList(), monday)).isNull()
    }

    private fun session(date: LocalDate, status: SessionStatus) = PlannedSession(
        date = date,
        type = WorkoutType.EASY,
        title = "Fácil e conversável",
        durationMin = 30,
        rationale = "Base aeróbica.",
        ruleIds = listOf("R-PLAN-03"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = status,
        completionEvidence = if (status == SessionStatus.DONE) {
            com.meucoach.core.model.CompletionEvidence.MANUAL
        } else {
            null
        },
        closureReason = if (status == SessionStatus.CLOSED) {
            com.meucoach.core.domain.prescription.SessionClosureReason.USER_SKIPPED
        } else {
            null
        },
    )
}
