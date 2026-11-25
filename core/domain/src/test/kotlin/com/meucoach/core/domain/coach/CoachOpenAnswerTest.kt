package com.meucoach.core.domain.coach

import com.meucoach.core.domain.feedback.ComparisonPlan
import com.meucoach.core.domain.feedback.SessionComparisonCalculator
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Distance
import com.meucoach.core.model.Pace
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CoachOpenAnswerTest {
    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun `facts carry the day authority and never internal codes`() {
        val answer = CoachOpenAnswer.build(
            question = "por que o treino de hoje mudou?",
            context = CoachOpenContext(
                today = today,
                plan = plan(),
                prescription = prescription(),
                readiness = ReadinessResult(
                    score = 55,
                    level = ReadinessLevel.AMBER,
                    confidence = DataConfidence.MEDIUM,
                    components = mapOf(com.meucoach.core.domain.readiness.ReadinessComponent.SUBJECTIVE to 55),
                    missing = emptySet(),
                    flags = setOf(com.meucoach.core.domain.readiness.ReadinessFlag.SHORT_SLEEP),
                ),
                hasCheckIn = true,
                sleepMinutes = 340,
            ),
        )

        assertThat(answer.engineContext).contains(
            "PERGUNTA DO ATLETA: por que o treino de hoje mudou?",
            "PRESCRIÇÃO DE HOJE",
            "MOTIVO DE HOJE",
            "prontidão",
            "SONO: 340 min",
            "CHECK-IN: feito",
        )
        assertThat(answer.engineContext).doesNotContain("R-PRESC", "EV-")
        assertThat(answer.allowedMinutes).contains(40, 28, 340)
        assertThat(answer.allowedCounts).contains(7, 1, 55)
    }

    @Test
    fun `progress counts and percentages are the only extras the answer may cite`() {
        val answer = CoachOpenAnswer.build(
            question = "como está minha evolução?",
            context = CoachOpenContext(
                today = today,
                plan = plan(),
                progress = CoachProgressSummary(
                    availableWeeks = 4,
                    plannedSessions = 16,
                    doneSessions = 13,
                    adherencePercent = 81,
                    plannedMinutes = 640,
                    executedMinutes = 520,
                    extraMinutes = 20,
                    volumePercent = 81,
                    avgReadiness = 70,
                    redDays = 2,
                    avgSleepMinutes = 390,
                    shortNights = 3,
                    easyPercent28d = 82,
                ),
            ),
        )

        assertThat(answer.allowedCounts).contains(4, 16, 13, 2, 3, 80, 20)
        assertThat(answer.allowedPercents).contains(81, 82, 80)
        assertThat(answer.engineContext).contains("ÚLTIMAS 4 SEMANAS", "13 de 16 treinos", "fácil em 82%")
    }

    @Test
    fun `executed facts carry duration distance rpe and comparison verdicts`() {
        val prescription = prescription()
        val comparison = SessionComparisonCalculator.compare(
            linkedPlan = ComparisonPlan.from(prescription),
            activity = ActivitySummary(
                id = "activity-1",
                source = ActivitySource.FIT,
                sport = Sport.RUNNING,
                startTime = Instant.parse("2026-11-18T10:00:00Z"),
                durationSec = 1_692,
                distance = Distance.ofKilometers(4.1),
                averageHeartRate = 151,
                averagePace = Pace.of(6, 53),
            ),
            rpe = 6,
        )

        val answer = CoachOpenAnswer.build(
            question = "avalie meu treino",
            context = CoachOpenContext(
                today = today,
                plan = plan(),
                prescription = prescription,
                execution = CoachExecution(
                    durationMin = 28,
                    distanceKm = 4.1,
                    rpe = 6,
                    evidence = CompletionEvidence.ACTIVITY,
                    comparison = comparison,
                ),
            ),
        )

        assertThat(answer.engineContext).contains(
            "EXECUTADO: 28 min",
            "4.1 km",
            "RPE 6",
            "evidência atividade importada",
            "COMPARAÇÃO COM O PLANO",
            "duração dentro do alvo",
            "ritmo sem alvo comparável",
        )
        assertThat(answer.engineContext).doesNotContain("6:53", "151 bpm")
        assertThat(answer.allowedMinutes).contains(28)
        assertThat(answer.allowedDistancesKm).contains(4.1)
        assertThat(answer.allowedCounts).contains(6)
        assertThat(answer.allowedPercents).contains(comparison.durationPercentOfPlan)
    }

    @Test
    fun `missing data stays missing instead of becoming zero`() {
        val answer = CoachOpenAnswer.build("o que é fartlek?", CoachOpenContext(today = today))

        assertThat(answer.engineContext).contains("ainda sem plano gerado", "CHECK-IN: pendente")
        assertThat(answer.allowedMinutes).isEmpty()
        assertThat(answer.allowedPercents).isEmpty()
    }

    private fun plan() = WeeklyPlan(
        weekStart = LocalDate.of(2026, 11, 16),
        sessions = listOf(
            PlannedSession(
                date = today,
                type = WorkoutType.QUALITY,
                title = "Intervalado",
                durationMin = 40,
                rationale = "Plano forte.",
                ruleIds = listOf("R-PLAN-02"),
                evidenceIds = listOf("EV-SEILER-2010"),
                targets = PrescriptionTargets(primary = TargetKind.RPE, rpe = 8),
            ),
        ),
        weeklyMinutes = 40,
        longRunMinutes = null,
        qualityCount = 1,
        rationale = "Semana de qualidade.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        createdOn = LocalDate.of(2026, 11, 16),
    )

    private fun prescription() = DailyPrescription(
        date = today,
        plannedSessionId = "session-1",
        plannedType = WorkoutType.QUALITY,
        plannedTitle = "Intervalado",
        plannedDurationMin = 40,
        prescribedType = WorkoutType.EASY,
        title = "Fácil — prontidão moderada",
        prescribedDurationMin = 28,
        targets = PrescriptionTargets(primary = TargetKind.RPE, rpe = 4),
        adjustment = PrescriptionAdjustment.DOWNGRADE,
        rationale = "Planejado: Intervalado (40 min). Hoje: fácil, 28 min. Motivo: prontidão moderada.",
        ruleIds = listOf("R-PRESC-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = "daily-prescription-v6",
        inputsHash = "hash",
    )
}
