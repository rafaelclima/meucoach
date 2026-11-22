package com.meucoach.core.domain.coach

import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.RaceStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CoachNextResolverTest {
    private val monday = LocalDate.of(2026, 11, 16)

    @Test
    fun `hoje pendente vence a proxima sessao futura`() {
        val today = session(monday, SessionStatus.PLANNED)
        val future = session(monday.plusDays(2), SessionStatus.PLANNED)

        val next = CoachNextResolver.resolve(listOf(today, future), monday)

        assertThat(next?.subject).isEqualTo(CoachNextSubject.Training(today))
        assertThat(next?.isToday).isTrue()
    }

    @Test
    fun `sem pendencia hoje devolve a proxima futura planejada`() {
        val done = session(monday, SessionStatus.DONE)
        val thursday = session(monday.plusDays(3), SessionStatus.PLANNED)
        val saturday = session(monday.plusDays(5), SessionStatus.PLANNED)

        val next = CoachNextResolver.resolve(listOf(done, saturday, thursday), monday)

        assertThat(next?.subject).isEqualTo(CoachNextSubject.Training(thursday))
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

    @Test
    fun `prova amanha vence a sessao de hoje`() {
        val today = session(monday, SessionStatus.PLANNED)
        val race = race(monday.plusDays(1))

        val next = CoachNextResolver.resolve(listOf(today), monday, listOf(race))

        assertThat(next?.subject).isEqualTo(CoachNextSubject.Race(race))
        assertThat(next?.isToday).isFalse()
    }

    @Test
    fun `prova hoje vence mesmo com sessao artificial na data`() {
        val today = session(monday, SessionStatus.PLANNED)
        val race = race(monday)

        val next = CoachNextResolver.resolve(listOf(today), monday, listOf(race))

        assertThat(next?.subject).isEqualTo(CoachNextSubject.Race(race))
        assertThat(next?.isToday).isTrue()
    }

    @Test
    fun `prova no domingo sem sessao na data aparece antes da semana concluida`() {
        val saturday = session(monday.plusDays(5), SessionStatus.PLANNED)
        val race = race(monday.plusDays(6))

        val next = CoachNextResolver.resolve(listOf(saturday), monday.plusDays(5), listOf(race))

        assertThat(next?.subject).isEqualTo(CoachNextSubject.Race(race))
        assertThat(next?.isToday).isFalse()
    }

    @Test
    fun `treino antes de uma prova distante continua sendo o proximo`() {
        val tuesday = session(monday.plusDays(1), SessionStatus.PLANNED)
        val race = race(monday.plusDays(6))

        val next = CoachNextResolver.resolve(listOf(tuesday), monday, listOf(race))

        assertThat(next?.subject).isEqualTo(CoachNextSubject.Training(tuesday))
    }

    @Test
    fun `sem sessao e com prova futura a prova e o proximo`() {
        val race = race(monday.plusDays(3))

        val next = CoachNextResolver.resolve(emptyList(), monday, listOf(race))

        assertThat(next?.subject).isEqualTo(CoachNextSubject.Race(race))
    }

    @Test
    fun `prova encerrada ou no passado nao entra`() {
        val tuesday = session(monday.plusDays(1), SessionStatus.PLANNED)
        val cancelled = race(monday, status = RaceStatus.CANCELLED)
        val past = race(monday.minusDays(1))

        val next = CoachNextResolver.resolve(listOf(tuesday), monday, listOf(cancelled, past))

        assertThat(next?.subject).isEqualTo(CoachNextSubject.Training(tuesday))
    }

    private fun session(date: LocalDate, status: SessionStatus) = PlannedSession(
        date = date,
        type = WorkoutType.EASY,
        title = "Rodagem",
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

    private fun race(date: LocalDate, status: RaceStatus = RaceStatus.PLANNED) = RaceEvent(
        id = "race-1",
        distance = RaceDistance.FIVE_K,
        targetDate = date,
        status = status,
        createdAt = Instant.parse("2026-11-01T12:00:00Z"),
        updatedAt = Instant.parse("2026-11-01T12:00:00Z"),
        resolvedAt = if (status == RaceStatus.PLANNED) null else Instant.parse("2026-11-01T12:00:00Z"),
    )
}
