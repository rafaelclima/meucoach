package com.meucoach.app.ui.today

import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

private const val RECOVERY_MISSING =
    "Dados objetivos de recuperação (sono, FC de repouso ou variabilidade cardíaca)"

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

    @Test
    fun `a low-confidence plan without volume history names the history`() {
        val explanation = buildPlanExplanation(
            plan(ruleIds = listOf("R-PLAN-02"), confidence = DataConfidence.LOW),
        )

        assertThat(explanation.missingData)
            .contains("Histórico de semanas concluídas (para calibrar o volume)")
            .doesNotContain(RECOVERY_MISSING)
    }

    @Test
    fun `a low-confidence plan with volume history names the recovery data`() {
        val explanation = buildPlanExplanation(
            plan(ruleIds = listOf("R-PLAN-01"), confidence = DataConfidence.LOW),
        )

        assertThat(explanation.missingData)
            .contains(RECOVERY_MISSING)
            .doesNotContain("Histórico de semanas concluídas (para calibrar o volume)")
    }

    @Test
    fun `a low-confidence plan with incomplete load names the load`() {
        val explanation = buildPlanExplanation(
            plan(ruleIds = listOf("R-PLAN-01", "R-DATA-01"), confidence = DataConfidence.LOW),
        )

        assertThat(explanation.missingData)
            .contains("Carga recente para liberar treino forte")
            .doesNotContain(RECOVERY_MISSING)
    }

    @Test
    fun `the low-confidence note distinguishes history, load and recovery`() {
        val noHistory = planLowConfidenceNote(
            plan(ruleIds = listOf("R-PLAN-02"), confidence = DataConfidence.LOW),
        )
        val loadIncomplete = planLowConfidenceNote(
            plan(ruleIds = listOf("R-PLAN-01", "R-DATA-01"), confidence = DataConfidence.LOW),
        )
        val recoveryPartial = planLowConfidenceNote(
            plan(ruleIds = listOf("R-PLAN-01"), confidence = DataConfidence.LOW),
        )
        val medium = planLowConfidenceNote(
            plan(ruleIds = listOf("R-PLAN-02"), confidence = DataConfidence.MEDIUM),
        )

        assertThat(noHistory)
            .isEqualTo("Sem histórico de semanas concluídas: a semana segue conservadora.")
        assertThat(loadIncomplete)
            .isEqualTo("Dados de carga incompletos: a semana segue conservadora.")
        assertThat(recoveryPartial)
            .isEqualTo("Dados de recuperação parciais: a semana segue conservadora.")
        assertThat(medium).isNull()
    }

    private fun plan(ruleIds: List<String>, confidence: DataConfidence = DataConfidence.MEDIUM) = WeeklyPlan(
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
        confidence = confidence,
        ruleIds = ruleIds,
        evidenceIds = listOf("EV-SEILER-2010"),
    )
}
