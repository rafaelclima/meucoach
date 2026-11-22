package com.meucoach.core.domain.coach

import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class CoachSessionAnswerTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val tuesday = LocalDate.of(2026, 11, 17)
    private val sunday = LocalDate.of(2026, 11, 22)

    private fun easySession(date: LocalDate) = PlannedSession(
        date = date,
        type = WorkoutType.EASY,
        title = "Rodagem",
        durationMin = 40,
        rationale = "Base aeróbica",
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = SessionStatus.PLANNED,
    )

    @Test
    fun `resolve dia da semana dentro da semana vigente`() {
        assertEquals(tuesday, CoachSessionAnswer.resolveDayInWeek(monday, DayOfWeek.TUESDAY))
        assertEquals(monday, CoachSessionAnswer.resolveDayInWeek(monday, DayOfWeek.MONDAY))
        assertEquals(sunday, CoachSessionAnswer.resolveDayInWeek(monday, DayOfWeek.SUNDAY))
    }

    @Test
    fun `sessao futura e planejada com tarefa WHAT`() {
        val answer = CoachSessionAnswer.build(
            today = monday,
            target = tuesday,
            sessions = listOf(easySession(tuesday)),
            weeklyMinutes = 120,
            task = SessionTask.WHAT,
        )
        assertTrue(answer.hasSession)
        assertTrue(answer.isPlannedOnly)
        assertEquals("Amanhã", answer.dayLabel)
        assertTrue(answer.allowedMinutes.contains(40))
        assertTrue(answer.allowedMinutes.contains(120))
        assertTrue(answer.engineContext.contains("Planejado"))
        assertTrue(answer.engineContext.contains("planejados"))
        assertTrue(answer.engineContext.contains("Rodagem"))
        assertFalse(answer.engineContext.contains("6:10"))
    }

    @Test
    fun `como correr traz estrategia em palavras sem numero novo`() {
        val answer = CoachSessionAnswer.build(
            today = monday,
            target = tuesday,
            sessions = listOf(easySession(tuesday)),
            weeklyMinutes = 120,
            task = SessionTask.HOW_TO,
        )
        assertTrue(answer.engineContext.contains("Como executar"))
        assertTrue(answer.engineContext.contains("comece soltinho"))
        assertTrue(answer.engineContext.contains("dê para conversar"))
        assertFalse(answer.engineContext.contains("reduza"))
        assertFalse(answer.engineContext.contains("bpm"))
    }

    @Test
    fun `hoje com prescricao mostra prescrito e sinais do dia`() {
        val prescription = DailyPrescription(
            date = monday,
            plannedSessionId = null,
            plannedType = WorkoutType.EASY,
            plannedTitle = "Rodagem",
            plannedDurationMin = 40,
            prescribedType = WorkoutType.EASY,
            title = "Rodagem leve",
            prescribedDurationMin = 30,
            targets = PrescriptionTargets(primary = TargetKind.RPE, rpe = 4),
            adjustment = PrescriptionAdjustment.REDUCE,
            rationale = "Sono curto, reduz sem culpa",
            ruleIds = listOf("R-PRESC-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
            confidence = DataConfidence.MEDIUM,
            engineVersion = "daily-prescription-v6",
            inputsHash = "hash",
        )
        val answer = CoachSessionAnswer.build(
            today = monday,
            target = monday,
            sessions = listOf(easySession(monday)),
            weeklyMinutes = 120,
            task = SessionTask.HOW_TO,
            prescription = prescription,
            shortSleep = true,
            hasCheckIn = false,
        )
        assertEquals("Hoje", answer.dayLabel)
        assertFalse(answer.isPlannedOnly)
        assertTrue(answer.allowedMinutes.contains(30))
        assertTrue(answer.engineContext.contains("Reduzido para o dia"))
        assertTrue(answer.engineContext.contains("Sono curto"))
        assertTrue(answer.engineContext.contains("Sem check-in ainda"))
    }

    @Test
    fun `sono normal nao entra no contexto`() {
        val prescription = DailyPrescription(
            date = monday,
            plannedSessionId = null,
            plannedType = WorkoutType.EASY,
            plannedTitle = "Rodagem",
            plannedDurationMin = 40,
            prescribedType = WorkoutType.EASY,
            title = "Rodagem leve",
            prescribedDurationMin = 30,
            targets = PrescriptionTargets(primary = TargetKind.RPE, rpe = 4),
            adjustment = PrescriptionAdjustment.KEEP,
            rationale = "Dia verde",
            ruleIds = listOf("R-PRESC-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
            confidence = DataConfidence.MEDIUM,
            engineVersion = "daily-prescription-v6",
            inputsHash = "hash",
        )
        val answer = CoachSessionAnswer.build(
            today = monday,
            target = monday,
            sessions = listOf(easySession(monday)),
            weeklyMinutes = 120,
            task = SessionTask.HOW_TO,
            prescription = prescription,
            shortSleep = false,
            hasCheckIn = true,
        )
        assertFalse(answer.engineContext.contains("Sono"))
        assertTrue(answer.engineContext.contains("Check-in feito"))
    }

    @Test
    fun `dia sem sessao e ausencia honesta`() {
        val answer = CoachSessionAnswer.build(
            today = monday,
            target = tuesday,
            sessions = emptyList(),
            weeklyMinutes = 120,
            task = SessionTask.WHAT,
        )
        assertFalse(answer.hasSession)
        assertTrue(answer.engineContext.contains("Sem sessão planejada"))
        assertTrue(answer.allowedMinutes.contains(120))
    }

    @Test
    fun `distancia do plano entra no permitido`() {
        val session = easySession(tuesday).copy(
            targets = PrescriptionTargets(primary = TargetKind.RPE, rpe = 4, distanceKm = 8.0),
        )
        val answer = CoachSessionAnswer.build(
            today = monday,
            target = tuesday,
            sessions = listOf(session),
            weeklyMinutes = 120,
            task = SessionTask.WHAT,
        )
        assertTrue(answer.allowedDistancesKm.contains(8.0))
    }
}
