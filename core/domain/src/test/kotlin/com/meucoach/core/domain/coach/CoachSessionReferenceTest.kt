package com.meucoach.core.domain.coach

import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Pace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoachSessionReferenceTest {
    private val monday = LocalDate.of(2026, 11, 16)

    private fun easySession() = PlannedSession(
        date = monday,
        type = WorkoutType.EASY,
        title = "Rodagem",
        durationMin = 40,
        rationale = "Base",
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = SessionStatus.PLANNED,
    )

    private fun prescription(targets: PrescriptionTargets) = DailyPrescription(
        date = monday,
        plannedSessionId = null,
        plannedType = WorkoutType.EASY,
        plannedTitle = "Rodagem",
        plannedDurationMin = 40,
        prescribedType = WorkoutType.EASY,
        title = "Rodagem",
        prescribedDurationMin = 40,
        targets = targets,
        adjustment = PrescriptionAdjustment.KEEP,
        rationale = "Base",
        ruleIds = listOf("R-PRESC-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = "daily-prescription-v6",
        inputsHash = "hash",
    )

    @Test
    fun `ritmo e FC virem linhas de referencia`() {
        val lines = CoachSessionReference.lines(
            easySession(),
            prescription(
                PrescriptionTargets(
                    pace = com.meucoach.core.domain.prescription.PaceTarget(
                        fast = Pace.fromSecondsPerKm(364),
                        slow = Pace.fromSecondsPerKm(392),
                    ),
                    heartRate = com.meucoach.core.domain.prescription.HeartRateTarget(minBpm = 140, maxBpm = 155),
                    primary = TargetKind.HEART_RATE,
                ),
            ),
        )

        assertEquals(
            listOf("Ritmo: entre 6:04 e 6:32 por km", "FC: até 155 bpm"),
            lines,
        )
    }

    @Test
    fun `FC sem teto vira a partir de`() {
        val lines = CoachSessionReference.lines(
            easySession(),
            prescription(
                PrescriptionTargets(
                    heartRate = com.meucoach.core.domain.prescription.HeartRateTarget(minBpm = 140, maxBpm = null),
                    primary = TargetKind.MIXED,
                ),
            ),
        )

        assertEquals(listOf("FC: a partir de 140 bpm"), lines)
    }

    @Test
    fun `sem calibracao vira zona e RPE`() {
        val lines = CoachSessionReference.lines(
            easySession().copy(
                targets = PrescriptionTargets(zone = "Z2", rpe = 4, primary = TargetKind.RPE),
            ),
            null,
        )

        assertEquals(listOf("Zona Z2", "Esforço: 4/10"), lines)
    }

    @Test
    fun `descanso nao tem referencia`() {
        val rest = easySession().copy(type = WorkoutType.REST, title = "Descanso")

        assertTrue(CoachSessionReference.lines(rest, null).isEmpty())
    }
}
