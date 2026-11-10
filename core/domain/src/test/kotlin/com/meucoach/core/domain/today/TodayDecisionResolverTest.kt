package com.meucoach.core.domain.today

import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.RaceStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TodayDecisionResolverTest {
    private val date = LocalDate.of(2026, 12, 6)
    private val at = Instant.parse("2026-11-10T12:00:00Z")

    @Test
    fun `training is the branch when there is no event on the date`() {
        val decision = TodayDecisionResolver.resolve(date = date, event = null, prescription = prescription())

        assertThat(decision).isInstanceOf(TodayDecision.Training::class.java)
        assertThat((decision as TodayDecision.Training).prescription.title).isEqualTo("Fácil e conversável")
    }

    @Test
    fun `competition wins even when a legacy prescription exists`() {
        val event = event()

        val decision = TodayDecisionResolver.resolve(date, event, prescription())

        assertThat(decision).isInstanceOf(TodayDecision.Competition::class.java)
        assertThat((decision as TodayDecision.Competition).event).isEqualTo(event)
    }

    @Test
    fun `a terminal event on the date still owns the day without starting recovery`() {
        for (status in listOf(RaceStatus.COMPLETED, RaceStatus.CANCELLED, RaceStatus.NO_SHOW)) {
            val decision = TodayDecisionResolver.resolve(
                date,
                event(status = status, resolvedAt = at),
                prescription(),
            )

            assertThat(decision).isInstanceOf(TodayDecision.Competition::class.java)
            assertThat((decision as TodayDecision.Competition).event.status).isEqualTo(status)
        }
    }

    @Test
    fun `there is no decision without an event or a prescription`() {
        assertThat(TodayDecisionResolver.resolve(date, event = null, prescription = null)).isNull()
    }

    @Test
    fun `an event from another date cannot decide today`() {
        assertThatThrownBy {
            TodayDecisionResolver.resolve(
                date = date,
                event = event(targetDate = date.plusDays(1)),
                prescription = null,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("date")
    }

    private fun event(
        status: RaceStatus = RaceStatus.PLANNED,
        resolvedAt: Instant? = null,
        targetDate: LocalDate = date,
    ) = RaceEvent(
        id = "race-1",
        distance = RaceDistance.TEN_K,
        targetDate = targetDate,
        status = status,
        createdAt = at,
        updatedAt = at,
        resolvedAt = resolvedAt,
    )

    private fun prescription() = DailyPrescription(
        date = date,
        plannedSessionId = "plan-$date",
        plannedType = WorkoutType.EASY,
        plannedTitle = "Fácil e conversável",
        plannedDurationMin = 30,
        prescribedType = WorkoutType.EASY,
        title = "Fácil e conversável",
        prescribedDurationMin = 30,
        targets = PrescriptionTargets(rpe = 4),
        adjustment = PrescriptionAdjustment.KEEP,
        rationale = "Plano mantido: fácil, 30 min.",
        ruleIds = listOf("R-PRESC-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = "daily-prescription-v2",
        inputsHash = "hash",
    )
}
