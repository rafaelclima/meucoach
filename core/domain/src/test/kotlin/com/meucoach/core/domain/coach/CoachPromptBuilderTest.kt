package com.meucoach.core.domain.coach

import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoachPromptBuilderTest {
    private fun plan(): WeeklyPlan = WeeklyPlan(
        weekStart = LocalDate.of(2026, 11, 16),
        sessions = listOf(
            PlannedSession(
                date = LocalDate.of(2026, 11, 17),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.PLANNED,
            ),
        ),
        weeklyMinutes = 30,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Semana base",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        createdOn = LocalDate.of(2026, 11, 16),
        engineVersion = WeeklyPlanGenerator.ENGINE_VERSION,
    )

    @Test
    fun `minimiza payload sem vazar identificadores`() {
        val prompt = CoachPromptBuilder.build(plan(), inputsHash = "abc123")
        assertEquals(30, prompt.weeklyMinutes)
        assertEquals("2026-11-17", prompt.sessions.single().date)
        assertTrue(prompt.sessions.single().type == "EASY")
    }

    @Test
    fun `preserva nulos sem inventar`() {
        val prompt = CoachPromptBuilder.build(plan(), inputsHash = "abc123")
        assertEquals(null, prompt.executedMinutes)
        assertEquals(null, prompt.adherenceRatio)
    }

    @Test
    fun `deterministico para mesmo plano`() {
        val first = CoachPromptBuilder.build(plan(), inputsHash = "abc123")
        val second = CoachPromptBuilder.build(plan(), inputsHash = "abc123")
        assertEquals(first, second)
    }
}
