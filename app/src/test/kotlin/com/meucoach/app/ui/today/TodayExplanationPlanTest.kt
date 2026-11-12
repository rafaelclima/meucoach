package com.meucoach.app.ui.today

import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class TodayExplanationPlanTest {
    @Test
    fun `a race goal without a date is listed as missing data`() {
        val explanation = buildPlanExplanation(plan(ruleIds = listOf("R-PLAN-11")))

        assertThat(explanation.missingData)
            .contains("Data da prova (a meta é de prova, mas está sem data)")
        assertThat(explanation.rules.map { it.title })
            .contains("Meta de prova sem data não reduz o volume antes da prova")
    }

    @Test
    fun `a plan with a race date does not list it as missing`() {
        val explanation = buildPlanExplanation(plan(ruleIds = listOf("R-PLAN-02")))

        assertThat(explanation.missingData)
            .doesNotContain("Data da prova (a meta é de prova, mas está sem data)")
    }

    private fun plan(ruleIds: List<String>) = WeeklyPlan(
        weekStart = LocalDate.of(2026, 11, 2),
        sessions = listOf(
            PlannedSession(
                date = LocalDate.of(2026, 11, 2),
                type = WorkoutType.EASY,
                title = "Fácil e conversável",
                durationMin = 40,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
            ),
        ),
        weeklyMinutes = 40,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Sua meta é uma prova, mas está sem data: o plano não reduz o volume.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = ruleIds,
        evidenceIds = listOf("EV-SEILER-2010"),
    )
}
