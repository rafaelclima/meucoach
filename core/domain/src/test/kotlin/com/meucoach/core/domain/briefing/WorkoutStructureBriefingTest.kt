package com.meucoach.core.domain.briefing

import com.meucoach.core.domain.prescription.BlockKind
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.WorkoutBlock
import com.meucoach.core.domain.prescription.WorkoutStructure
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * C4-19 — estrutura no briefing (ADR 0047 §2.1/§2.8, FR-403/FR-410).
 */
class WorkoutStructureBriefingTest {
    private val date = LocalDate.of(2026, 11, 10)

    private fun structure() = WorkoutStructure(
        warmupMin = 10,
        blocks = listOf(
            WorkoutBlock(
                kind = BlockKind.WORK,
                repetitions = 5,
                workSec = 180,
                recoverySec = 120,
                target = "I",
                note = "3 min fortes, 2 min leves",
            ),
        ),
        cooldownMin = 5,
        totalMin = 40,
    )

    private fun prescription(structure: WorkoutStructure?) = DailyPrescription(
        date = date,
        plannedSessionId = "plan-$date",
        plannedType = WorkoutType.QUALITY,
        plannedTitle = "Intervalado",
        plannedDurationMin = 40,
        prescribedType = WorkoutType.QUALITY,
        title = "Intervalado",
        prescribedDurationMin = 40,
        targets = PrescriptionTargets(primary = TargetKind.RPE),
        adjustment = PrescriptionAdjustment.KEEP,
        rationale = "Plano mantido: Intervalos, 40 min.",
        ruleIds = listOf("R-PRESC-01"),
        evidenceIds = listOf("EV-DANIELS-2014"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = "daily-prescription-v5",
        inputsHash = "inputs-hash",
        structure = structure,
    )

    @Test
    fun `kept quality carries the executable structure without promising extra time`() {
        val briefing = SessionBriefingEngine().brief(prescription(structure()), SessionStatus.PLANNED)!!
        val briefingStructure = requireNotNull(briefing.structure)

        assertThat(briefing.structure).isEqualTo(structure())
        assertThat(briefing.steps).isNotEmpty
        assertThat(briefingStructure.totalMin).isEqualTo(briefing.durationMin)
        assertThat(briefingStructure.plannedSeconds).isLessThanOrEqualTo(40L * 60L)
    }

    @Test
    fun `easy day without structure has no steps`() {
        val briefing = SessionBriefingEngine().brief(prescription(null), SessionStatus.PLANNED)!!

        assertThat(briefing.structure).isNull()
        assertThat(briefing.steps).isEmpty()
    }
}
