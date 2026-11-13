package com.meucoach.app.ui.today

import com.meucoach.core.domain.load.DailyLoad
import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadMetric
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.readiness.DailyMetricsReadinessBuilder
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ReadinessLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * Fatia 3 do ADR 0039: o sheet "Por que este treino?" declara quando a carga
 * vem do esforço percebido e o que falta para medi-la objetivamente.
 */
class TodayExplanationLoadTest {
    private val today = LocalDate.of(2026, 11, 13)

    @Test
    fun `declares subjective load and the missing objective metric`() {
        val explanation = buildExplanation(
            checkIn = null,
            inputs = DailyMetricsReadinessBuilder.build(emptyList(), today),
            result = readiness(),
            prescription = prescription(ruleIds = listOf("R-ZONE-01", "R-DATA-01", "R-LOAD-04")),
            load = subjectiveLoad(),
        )

        assertThat(explanation.usedData).contains("Carga das últimas semanas", "Esforço percebido (RPE)")
        assertThat(explanation.missingData)
            .contains("FC ou ritmo para medir a carga objetivamente")
        val rule = explanation.rules.first { it.id == "R-LOAD-04" }
        assertThat(rule.title).contains("esforço percebido")
    }

    @Test
    fun `does not claim subjective load when it is objective`() {
        val explanation = buildExplanation(
            checkIn = null,
            inputs = DailyMetricsReadinessBuilder.build(emptyList(), today),
            result = readiness(),
            prescription = prescription(ruleIds = listOf("R-ZONE-01")),
            load = subjectiveLoad(subjectiveDays = 0),
        )

        assertThat(explanation.usedData).doesNotContain("Esforço percebido (RPE)")
        assertThat(explanation.missingData)
            .doesNotContain("FC ou ritmo para medir a carga objetivamente")
    }

    private fun subjectiveLoad(subjectiveDays: Int = 10) = LoadSnapshot(
        daily = listOf(DailyLoad(today, 250.0, LoadMetric.SRPE)),
        ctlSeries = listOf(10.0),
        atlSeries = listOf(12.0),
        ctl = 10.0,
        atl = 12.0,
        tsb = -2.0,
        acuteAverage7d = 250.0,
        chronicAverage28d = 62.5,
        acwr = 1.0,
        monotony = null,
        missingDays = 18,
        coverage = LoadCoverage(
            daysInWindow = 28,
            daysWithLoad = subjectiveDays,
            daysWithoutActivity = 28 - subjectiveDays,
            daysWithoutData = 0,
            lastActivityAt = null,
            sourceFresh = true,
        ),
        subjectiveDaysInWindow = subjectiveDays,
    )

    private fun readiness() = ReadinessResult(
        score = 80,
        level = ReadinessLevel.GREEN,
        confidence = DataConfidence.HIGH,
        components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        missing = emptySet(),
        flags = emptySet(),
    )

    private fun prescription(ruleIds: List<String>) = DailyPrescription(
        date = today,
        plannedSessionId = "2026-11-09-2026-11-13",
        plannedType = WorkoutType.EASY,
        plannedTitle = "Fácil",
        plannedDurationMin = 40,
        prescribedType = WorkoutType.EASY,
        title = "Fácil",
        prescribedDurationMin = 40,
        targets = PrescriptionTargets(),
        adjustment = PrescriptionAdjustment.KEEP,
        rationale = "Plano mantido.",
        ruleIds = ruleIds,
        evidenceIds = listOf("EV-SEILER-2010"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = "test",
        inputsHash = "hash",
    )
}
