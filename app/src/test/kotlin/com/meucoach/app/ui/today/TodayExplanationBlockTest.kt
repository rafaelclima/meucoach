package com.meucoach.app.ui.today

import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.QualityBlockReason
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.readiness.DailyMetricsReadinessBuilder
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ReadinessLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * C4-51 (FR-412): the "Por quê?" sheet names the blocking gate with the
 * failing value and the unlock hint — only when the day downgraded a
 * planned quality.
 */
class TodayExplanationBlockTest {
    private val today = LocalDate.of(2026, 11, 13)

    @Test
    fun `maps a downgraded quality to its gate block`() {
        val explanation = buildExplanation(
            checkIn = null,
            inputs = DailyMetricsReadinessBuilder.build(emptyList(), today),
            result = readiness(),
            prescription = prescription(
                blockReason = QualityBlockReason.SUBJECTIVE_MISSING,
                detail = "check-in subjetivo de hoje ainda não foi respondido",
                hint = "Faça o check-in de hoje para liberar o treino forte.",
            ),
            load = null,
        )

        val block = explanation.qualityBlock
        assertThat(block).isNotNull()
        assertThat(block?.title).isEqualTo("Qualidade a confirmar")
        assertThat(block?.detail).isEqualTo("check-in subjetivo de hoje ainda não foi respondido")
        assertThat(block?.hint).isEqualTo("Faça o check-in de hoje para liberar o treino forte.")
    }

    @Test
    fun `omits the block when the quality was kept`() {
        val explanation = buildExplanation(
            checkIn = null,
            inputs = DailyMetricsReadinessBuilder.build(emptyList(), today),
            result = readiness(),
            prescription = prescription(blockReason = null, detail = null, hint = null),
            load = null,
        )

        assertThat(explanation.qualityBlock).isNull()
    }

    private fun readiness() = ReadinessResult(
        score = 80,
        level = ReadinessLevel.GREEN,
        confidence = DataConfidence.MEDIUM,
        components = emptyMap(),
        missing = emptySet(),
        flags = emptySet(),
    )

    private fun prescription(blockReason: QualityBlockReason?, detail: String?, hint: String?) = DailyPrescription(
        date = today,
        plannedSessionId = "2026-11-09-2026-11-13",
        plannedType = WorkoutType.QUALITY,
        plannedTitle = "Fartlek leve",
        plannedDurationMin = 40,
        prescribedType = WorkoutType.EASY,
        title = "Fácil, sem pressa",
        prescribedDurationMin = 30,
        targets = PrescriptionTargets(),
        adjustment = PrescriptionAdjustment.DOWNGRADE,
        rationale = "Planejado: Fartlek leve (40 min). Hoje: fácil.",
        ruleIds = listOf("R-PRESC-02", "R-READY-06"),
        evidenceIds = listOf("EV-SAW-2016"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = "test",
        inputsHash = "hash",
        qualityBlockReason = blockReason,
        decisionDetail = detail,
        unlockHint = hint,
    )
}
