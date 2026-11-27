package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.HeatLevel
import com.meucoach.core.model.Pace
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class DailyPrescriptionEngineTest {
    private val engine = DailyPrescriptionEngine()
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `green day keeps a planned tempo with the same duration and targets`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(prescription.plannedType).isEqualTo(WorkoutType.QUALITY)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.QUALITY)
        assertThat(prescription.prescribedDurationMin).isEqualTo(quality.durationMin)
        assertThat(prescription.targets).isEqualTo(quality.targets)
        assertThat(prescription.differsFromPlan).isFalse()
        assertThat(prescription.ruleIds).contains("R-PRESC-01", "R-PER-03", "R-PRESC-03")
        assertThat(prescription.qualityBlockReason).isNull()
        assertThat(prescription.decisionDetail).isNull()
        assertThat(prescription.unlockHint).isNull()
    }

    @Test
    fun `missing check-in downgrades a green planned quality through the gate`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                readiness = readiness(
                    ReadinessLevel.GREEN,
                    components = emptyMap(),
                    flags = setOf(ReadinessFlag.SUBJECTIVE_MISSING),
                    score = 80,
                ),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.qualityBlockReason).isEqualTo(QualityBlockReason.SUBJECTIVE_MISSING)
        assertThat(prescription.decisionDetail).contains("check-in")
        assertThat(prescription.unlockHint).contains("check-in")
        assertThat(prescription.rationale).contains("check-in")
        assertThat(prescription.ruleIds).contains("R-READY-06", "R-PRESC-02")
    }

    @Test
    fun `downgraded quality without check-in asks to confirm it`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                readiness = readiness(
                    ReadinessLevel.GREEN,
                    components = emptyMap(),
                    flags = setOf(ReadinessFlag.SUBJECTIVE_MISSING),
                    score = 80,
                ),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(prescription.title).isEqualTo("Qualidade a confirmar")
    }

    @Test
    fun `downgraded quality with high ACWR stays an easy day`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.6),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(prescription.qualityBlockReason).isEqualTo(QualityBlockReason.ACWR_HIGH)
        assertThat(prescription.title).isEqualTo("Hoje é fácil")
    }

    @Test
    fun `downgraded quality without load data is calibrating`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                readiness = readiness(ReadinessLevel.GREEN),
                load = null,
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(prescription.qualityBlockReason).isEqualTo(QualityBlockReason.LOAD_INSUFFICIENT)
        assertThat(prescription.title).isEqualTo("Carga em calibração")
    }

    @Test
    fun `downgraded beginner quality starts with base`() {
        val quality = plannedQuality(QualityKind.CONTINUOUS, 30)

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                profile = profile(experience = Experience.BEGINNER),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(prescription.qualityBlockReason).isEqualTo(QualityBlockReason.BEGINNER_GUARD)
        assertThat(prescription.title).isEqualTo("Base primeiro")
    }

    @Test
    fun `taper blocks a main quality as race week`() {
        val quality = plannedQuality(QualityKind.CONTINUOUS, 30)

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                phase = MesoPhase.TAPER,
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(prescription.qualityBlockReason).isEqualTo(QualityBlockReason.TAPER_WINDOW)
        assertThat(prescription.title).isEqualTo("Semana de prova")
    }

    @Test
    fun `the inputs hash covers the subjective component even without the flag`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        val objectiveOnly = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                readiness = readiness(ReadinessLevel.GREEN, components = emptyMap(), score = 80),
                load = load(acwr = 1.0),
            ),
        )
        val checkedIn = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                readiness = readiness(
                    ReadinessLevel.GREEN,
                    components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
                    score = 80,
                ),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(objectiveOnly.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(checkedIn.prescribedType).isEqualTo(WorkoutType.QUALITY)
        assertThat(objectiveOnly.inputsHash).isNotEqualTo(checkedIn.inputsHash)
    }

    @Test
    fun `the inputs hash covers load values that change the gate`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        val highMonotony = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0, monotony = 2.4),
            ),
        )
        val variedWeek = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0, monotony = 1.2),
            ),
        )

        assertThat(highMonotony.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(highMonotony.qualityBlockReason).isEqualTo(QualityBlockReason.MONOTONY_HIGH)
        assertThat(variedWeek.prescribedType).isEqualTo(WorkoutType.QUALITY)
        assertThat(highMonotony.inputsHash).isNotEqualTo(variedWeek.inputsHash)
    }

    @Test
    fun `taper blocks a main quality but keeps the structural strides micro dose`() {
        val main = plannedQuality(kind = QualityKind.CONTINUOUS, durationMin = 40)
        val mainPrescription = engine.prescribe(
            input(
                planned = main,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                phase = MesoPhase.TAPER,
            ),
        )

        assertThat(mainPrescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(mainPrescription.qualityBlockReason).isEqualTo(QualityBlockReason.TAPER_WINDOW)
        assertThat(mainPrescription.ruleIds).contains("R-PLAN-05", "R-PRESC-02")

        val strides = plannedQuality(kind = QualityKind.STRIDES_ONLY, durationMin = 20)
        val stridesPrescription = engine.prescribe(
            input(
                planned = strides,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                phase = MesoPhase.TAPER,
            ),
        )

        assertThat(stridesPrescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(stridesPrescription.prescribedType).isEqualTo(WorkoutType.QUALITY)
        assertThat(stridesPrescription.qualityBlockReason).isNull()
    }

    @Test
    fun `race week blocks a main quality before load`() {
        val prescription = engine.prescribe(
            input(
                planned = plannedQuality(kind = QualityKind.RACE_PACE, durationMin = 40),
                readiness = readiness(ReadinessLevel.GREEN),
                load = null,
                phase = MesoPhase.TAPER,
                raceWeek = true,
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(prescription.qualityBlockReason).isEqualTo(QualityBlockReason.RACE_WEEK)
        assertThat(prescription.ruleIds).contains("R-PLAN-10", "R-PRESC-02")
    }

    @Test
    fun `done quality is history and is not re-evaluated by the gate`() {
        val quality = planWithQuality().sessions.first { it.type == WorkoutType.QUALITY }
        val done = quality.copy(
            status = SessionStatus.DONE,
            completionEvidence = com.meucoach.core.model.CompletionEvidence.MANUAL,
        )

        val prescription = engine.prescribe(
            input(
                date = done.date,
                planned = done,
                readiness = readiness(
                    ReadinessLevel.GREEN,
                    components = emptyMap(),
                    flags = setOf(ReadinessFlag.SUBJECTIVE_MISSING),
                ),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.QUALITY)
        assertThat(prescription.qualityBlockReason).isNull()
        assertThat(prescription.ruleIds).doesNotContain("R-READY-06")
        assertThat(prescription.rationale).contains("Histórico")
    }

    @Test
    fun `closed quality is history and is not re-evaluated by the gate`() {
        val quality = planWithQuality().sessions.first { it.type == WorkoutType.QUALITY }
        val closed = quality.copy(
            status = SessionStatus.CLOSED,
            closureReason = SessionClosureReason.ILLNESS_OR_INJURY,
            closedAt = java.time.Instant.EPOCH,
        )

        val prescription = engine.prescribe(
            input(
                date = closed.date,
                planned = closed,
                readiness = readiness(
                    ReadinessLevel.RED,
                    components = emptyMap(),
                    flags = setOf(ReadinessFlag.ILLNESS_OR_INJURY),
                    score = 20,
                ),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.QUALITY)
        assertThat(prescription.qualityBlockReason).isNull()
        assertThat(prescription.ruleIds).doesNotContain("R-SAFE-01", "R-READY-03")
    }

    @Test
    fun `input hash changes with the plan phase because the gate depends on it`() {
        val quality = plannedQuality(kind = QualityKind.CONTINUOUS, durationMin = 40)
        val building = engine.prescribe(
            input(
                planned = quality,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                phase = MesoPhase.BUILD,
            ),
        )
        val tapering = engine.prescribe(
            input(
                planned = quality,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                phase = MesoPhase.TAPER,
            ),
        )

        assertThat(tapering.inputsHash).isNotEqualTo(building.inputsHash)
    }

    @Test
    fun `beginner planned quality today is downgraded with the beginner guard`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        val beginner = profile(experience = Experience.BEGINNER)

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                profile = beginner,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(prescription.plannedType).isEqualTo(WorkoutType.QUALITY)
        assertThat(prescription.plannedDurationMin).isEqualTo(quality.durationMin)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.prescribedDurationMin).isLessThanOrEqualTo(quality.durationMin!!)
        assertThat(prescription.rationale).contains("iniciante")
        assertThat(prescription.rationale).contains("Planejado:")
        assertThat(prescription.ruleIds).contains("R-PRESC-04", "R-PRESC-02")
        assertThat(prescription.evidenceIds).isNotEmpty
        assertThat(prescription.confidence).isEqualTo(DataConfidence.LOW)
        assertThat(ExplanationCatalog.hasRule("R-PRESC-04")).isTrue()
    }

    @Test
    fun `subjective-only load downgrades planned quality citing perceived effort (ADR 0039)`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                profile = profile(experience = Experience.INTERMEDIATE),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0, subjectiveDaysInWindow = 12),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.rationale).contains("esforço percebido")
        assertThat(prescription.ruleIds).contains("R-LOAD-04")
        assertThat(prescription.ruleIds).contains("R-DATA-01")
        assertThat(ExplanationCatalog.hasRule("R-LOAD-04")).isTrue()
    }

    @Test
    fun `an easy day with subjective-only load cites the perceived-effort rule (ADR 0039)`() {
        val prescription = engine.prescribe(
            input(
                planned = easySession(durationMin = 40),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0, subjectiveDaysInWindow = 12),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.ruleIds).contains("R-LOAD-04")
    }

    @Test
    fun `non beginner planned quality today keeps the strong session`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        val intermediate = profile(experience = Experience.INTERMEDIATE)

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                profile = intermediate,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.QUALITY)
        assertThat(prescription.ruleIds).doesNotContain("R-PRESC-04")
    }

    @Test
    fun `a reliable acwr one point four keeps the planned quality`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                profile = profile(experience = Experience.INTERMEDIATE),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.4),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.QUALITY)
        assertThat(prescription.rationale).doesNotContain("carga subiu rápido")
    }

    @Test
    fun `a reliable acwr one point five keeps the planned quality`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                profile = profile(experience = Experience.INTERMEDIATE),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.5),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.QUALITY)
    }

    @Test
    fun `a reliable acwr above one point five downgrades the planned quality`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                profile = profile(experience = Experience.INTERMEDIATE),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.6),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.rationale).contains("carga dos últimos 7 dias").contains("1,60").contains("1,50")
        assertThat(prescription.ruleIds).contains("R-LOAD-03")
        assertThat(prescription.qualityBlockReason).isEqualTo(QualityBlockReason.ACWR_HIGH)
        assertThat(prescription.decisionDetail).contains("carga dos últimos 7 dias 1,60×, acima de 1,50×")
    }

    @Test
    fun `an unreliable acwr above one point five does not downgrade the quality`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                profile = profile(experience = Experience.INTERMEDIATE),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.6, daysWithoutData = 2),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.QUALITY)
        assertThat(prescription.qualityBlockReason).isNull()
        assertThat(prescription.rationale).doesNotContain("ACWR")
        assertThat(prescription.ruleIds).doesNotContain("R-LOAD-03")
    }

    @Test
    fun `a done quality is not re-downgraded by the beginner guard`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        val done = quality.copy(
            status = SessionStatus.DONE,
            completionEvidence = com.meucoach.core.model.CompletionEvidence.MANUAL,
        )

        val prescription = engine.prescribe(
            input(
                date = done.date,
                planned = done,
                plan = plan,
                profile = profile(experience = Experience.BEGINNER),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.QUALITY)
        assertThat(prescription.ruleIds).doesNotContain("R-PRESC-04")
        assertThat(prescription.rationale).doesNotContain("iniciante")
    }

    @Test
    fun `a closed quality is not re-downgraded by the beginner guard`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        val closed = quality.copy(
            status = SessionStatus.CLOSED,
            closureReason = SessionClosureReason.ILLNESS_OR_INJURY,
            closedAt = java.time.Instant.EPOCH,
        )

        val prescription = engine.prescribe(
            input(
                date = closed.date,
                planned = closed,
                plan = plan,
                profile = profile(experience = Experience.BEGINNER),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.QUALITY)
        assertThat(prescription.ruleIds).doesNotContain("R-PRESC-04")
        assertThat(prescription.rationale).doesNotContain("iniciante")
    }

    @Test
    fun `red readiness turns a planned quality into rest`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                readiness = readiness(ReadinessLevel.RED, score = 20),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.REST)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.REST)
        assertThat(prescription.isRest).isTrue()
        assertThat(prescription.prescribedDurationMin).isNull()
        assertThat(prescription.ruleIds).contains("R-READY-03", "R-PRESC-02")
        assertThat(prescription.rationale).contains("Prontidão baixa")
    }

    @Test
    fun `illness turns a planned quality into rest and cites the safety rule`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                readiness = readiness(
                    ReadinessLevel.GREEN,
                    flags = setOf(ReadinessFlag.ILLNESS_OR_INJURY),
                ),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.REST)
        assertThat(prescription.ruleIds).contains("R-SAFE-01", "R-PRESC-02")
        assertThat(prescription.rationale).contains("doença ou lesão")
    }

    @Test
    fun `amber readiness downgrades a planned quality to a shorter easy day`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                readiness = readiness(
                    ReadinessLevel.AMBER,
                    flags = setOf(ReadinessFlag.NO_QUALITY),
                    score = 55,
                ),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.prescribedDurationMin).isLessThan(quality.durationMin!!)
        assertThat(prescription.rationale).contains("Planejado:")
        assertThat(prescription.rationale).doesNotContain("Plano mantido")
        assertThat(prescription.ruleIds).contains("R-READY-03", "R-PRESC-02")
        assertThat(prescription.qualityBlockReason).isEqualTo(QualityBlockReason.LOW_READINESS)
        assertThat(prescription.decisionDetail).contains("moderada").contains("55")
        assertThat(prescription.unlockHint).isNotBlank()
    }

    @Test
    fun `amber readiness downgrades a long run to a shorter easy day`() {
        val plan = planWithQuality()
        val longRun = plan.sessions.first { it.type == WorkoutType.LONG }

        val prescription = engine.prescribe(
            input(
                date = longRun.date,
                planned = longRun,
                plan = plan,
                readiness = readiness(
                    ReadinessLevel.AMBER,
                    flags = setOf(ReadinessFlag.NO_QUALITY),
                    score = 55,
                ),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(prescription.prescribedDurationMin).isLessThan(longRun.durationMin!!)
        assertThat(prescription.rationale).contains("longão")
    }

    @Test
    fun `short sleep reduces a planned easy day`() {
        val session = easySession(durationMin = 50)

        val prescription = engine.prescribe(
            input(
                planned = session,
                readiness = readiness(
                    ReadinessLevel.GREEN,
                    flags = setOf(ReadinessFlag.SHORT_SLEEP),
                ),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.REDUCE)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.prescribedDurationMin).isEqualTo(40)
        assertThat(prescription.ruleIds).contains("R-READY-04")
    }

    @Test
    fun `unknown load blocks quality and cites the data gap`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                readiness = readiness(ReadinessLevel.GREEN),
                load = null,
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.ruleIds).contains("R-DATA-01")
        assertThat(prescription.evidenceIds).contains("EV-IMPELLIZZERI-2020")
        assertThat(prescription.confidence).isEqualTo(DataConfidence.LOW)
    }

    @Test
    fun `no plan gives a conservative easy day with low confidence`() {
        val prescription = engine.prescribe(
            input(
                planned = null,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.NO_PLAN)
        assertThat(prescription.plannedType).isNull()
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.confidence).isEqualTo(DataConfidence.LOW)
        assertThat(prescription.ruleIds).contains("R-PRESC-03")
        assertThat(prescription.rationale).contains("Sem plano")
    }

    @Test
    fun `no plan below the easy minimum returns rest with the availability reason`() {
        val prescription = engine.prescribe(
            input(
                planned = null,
                profile = profile(availability = mapOf(DayOfWeek.MONDAY to 10)),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.NO_PLAN)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.REST)
        assertThat(prescription.prescribedDurationMin).isNull()
        assertThat(prescription.targets.isEmpty).isTrue()
        assertThat(prescription.rationale).contains("Sem plano", "abaixo do mínimo", "20 min")
    }

    @Test
    fun `no plan at twenty minutes keeps a twenty minute easy day`() {
        val prescription = engine.prescribe(
            input(
                planned = null,
                profile = profile(availability = mapOf(DayOfWeek.MONDAY to 20)),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.NO_PLAN)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.prescribedDurationMin).isEqualTo(20)
    }

    @Test
    fun `no plan without check-in stays conservative and asks for check-in`() {
        val prescription = engine.prescribe(
            input(
                planned = null,
                readiness = readiness(
                    ReadinessLevel.GREEN,
                    components = emptyMap(),
                    flags = setOf(ReadinessFlag.SUBJECTIVE_MISSING),
                    score = 80,
                ),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.NO_PLAN)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.ruleIds).contains("R-READY-06", "R-PRESC-03")
        assertThat(prescription.rationale).contains("check-in")
    }

    @Test
    fun `no plan never invents a quality even with check-in and trusted load`() {
        val prescription = engine.prescribe(
            input(
                planned = null,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.NO_PLAN)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.ruleIds).doesNotContain("R-READY-06")
    }

    @Test
    fun `without calibration the easy day prescribes RPE and never invents pace`() {
        val prescription = engine.prescribe(
            input(
                planned = easySession(durationMin = 40),
                profile = profile(calibrated = false),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.targets.pace).isNull()
        assertThat(prescription.targets.heartRate).isNull()
        assertThat(prescription.targets.rpe).isEqualTo(4)
        assertThat(prescription.ruleIds).contains("R-CAL-02")
        assertThat(prescription.evidenceIds).contains("EV-FRIEL-2018")
    }

    @Test
    fun `heat slows the pace target without increasing duration`() {
        val session = easySession(durationMin = 40)
        val neutral = engine.prescribe(
            input(
                planned = session,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
            ),
        )
        val hot = engine.prescribe(
            input(
                planned = session,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                heat = HeatLevel.HOT,
            ),
        )

        assertThat(hot.prescribedDurationMin).isEqualTo(neutral.prescribedDurationMin)
        val hotPace = requireNotNull(hot.targets.pace)
        val neutralPace = requireNotNull(neutral.targets.pace)
        assertThat(hotPace.fast.secondsPerKm).isGreaterThan(neutralPace.fast.secondsPerKm)
        assertThat(hotPace.slow.secondsPerKm).isGreaterThan(neutralPace.slow.secondsPerKm)
        assertThat(hot.ruleIds).contains("R-PLAN-07")
        assertThat(hot.confidence).isEqualTo(DataConfidence.LOW)
    }

    @Test
    fun `never prescribes more duration than the plan`() {
        val cases = listOf(
            ReadinessLevel.GREEN to emptySet<ReadinessFlag>(),
            ReadinessLevel.GREEN to setOf(ReadinessFlag.SHORT_SLEEP),
            ReadinessLevel.AMBER to setOf(ReadinessFlag.NO_QUALITY),
            ReadinessLevel.RED to emptySet(),
        )
        cases.forEach { (level, flags) ->
            val session = easySession(durationMin = 30)
            val prescription = engine.prescribe(
                input(
                    planned = session,
                    readiness = readiness(level, flags = flags),
                    load = load(acwr = 1.0),
                ),
            )
            assertThat(prescription.prescribedDurationMin ?: 0)
                .describedAs("$level $flags")
                .isLessThanOrEqualTo(session.durationMin!!)
        }
    }

    @Test
    fun `input hash is stable for equal inputs and changes with readiness`() {
        val session = easySession(durationMin = 40)
        val first = engine.prescribe(
            input(planned = session, readiness = readiness(ReadinessLevel.GREEN), load = load(acwr = 1.0)),
        )
        val second = engine.prescribe(
            input(planned = session, readiness = readiness(ReadinessLevel.GREEN), load = load(acwr = 1.0)),
        )
        val changed = engine.prescribe(
            input(planned = session, readiness = readiness(ReadinessLevel.AMBER, score = 55), load = load(acwr = 1.0)),
        )

        assertThat(first.inputsHash).isEqualTo(second.inputsHash)
        assertThat(changed.inputsHash).isNotEqualTo(first.inputsHash)
        assertThat(first.engineVersion).isEqualTo(PrescriptionPolicy.ENGINE_VERSION)
    }

    @Test
    fun `daily engine version is daily-prescription-v9`() {
        // C4-53/ADR 0048 §2.4: a new day contract; the stored v5 is superseded
        // by GetTodayPrescription, never overwritten.
        assertThat(PrescriptionPolicy.ENGINE_VERSION).isEqualTo("daily-prescription-v9")
    }

    @Test
    fun `every emitted rule and evidence is catalogued`() {
        val plan = planWithQuality()
        val sessions = plan.sessions
        sessions.forEach { session ->
            listOf(
                ReadinessLevel.GREEN,
                ReadinessLevel.AMBER,
                ReadinessLevel.RED,
            ).forEach { level ->
                val prescription = engine.prescribe(
                    input(
                        date = session.date,
                        planned = session,
                        plan = plan,
                        readiness = readiness(level, score = if (level == ReadinessLevel.RED) 20 else 70),
                        load = load(acwr = if (level == ReadinessLevel.GREEN) 1.0 else 1.4),
                    ),
                )
                prescription.ruleIds.forEach { id ->
                    assertThat(ExplanationCatalog.hasRule(id)).describedAs(id).isTrue()
                }
                prescription.evidenceIds.forEach { id ->
                    assertThat(ExplanationCatalog.hasEvidence(id)).describedAs(id).isTrue()
                }
                assertThat(prescription.ruleIds).isNotEmpty
                assertThat(prescription.evidenceIds).isNotEmpty
            }
        }
    }

    @Test
    fun `missing recovery data keeps the easy duration and only lowers confidence`() {
        val session = easySession(durationMin = 45)
        val noData = ReadinessResult(
            score = 50,
            level = ReadinessLevel.AMBER,
            confidence = DataConfidence.LOW,
            components = emptyMap(),
            missing = ReadinessComponent.entries.toSet(),
            flags = setOf(ReadinessFlag.INSUFFICIENT_DATA, ReadinessFlag.NO_QUALITY),
        )

        val prescription = engine.prescribe(
            input(
                planned = session,
                readiness = noData,
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(prescription.prescribedDurationMin).isEqualTo(45)
        assertThat(prescription.confidence).isEqualTo(DataConfidence.LOW)
        assertThat(prescription.rationale).contains("Plano mantido")
        assertThat(prescription.ruleIds).doesNotContain("R-READY-03")
    }

    @Test
    fun `missing recovery data still blocks a planned quality`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        val noData = ReadinessResult(
            score = 50,
            level = ReadinessLevel.AMBER,
            confidence = DataConfidence.LOW,
            components = emptyMap(),
            missing = ReadinessComponent.entries.toSet(),
            flags = setOf(ReadinessFlag.INSUFFICIENT_DATA, ReadinessFlag.NO_QUALITY),
        )

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                readiness = noData,
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.rationale).contains("check-in")
        assertThat(prescription.ruleIds).contains("R-READY-06", "R-PRESC-02")
        assertThat(prescription.qualityBlockReason).isEqualTo(QualityBlockReason.SUBJECTIVE_MISSING)
    }

    @Test
    fun `a planned rest day stays rest`() {
        val rest = PlannedSession(
            date = monday,
            type = WorkoutType.REST,
            title = "Descanso",
            durationMin = null,
            rationale = "Descanso planejado.",
            ruleIds = listOf("R-PLAN-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
        )

        val prescription = engine.prescribe(
            input(
                planned = rest,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.REST)
        assertThat(prescription.prescribedDurationMin).isNull()
        assertThat(prescription.targets).isEqualTo(PrescriptionTargets(primary = TargetKind.RPE))
    }

    @Test
    fun `downgrades a planned quality during post-race recovery`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        val recovery = RecoveryWindow(
            CompletedRace("race-1", RaceDistance.TEN_K, monday.minusDays(3)),
        )

        val prescription = engine.prescribe(
            input(
                date = quality.date,
                planned = quality,
                plan = plan,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                recovery = recovery,
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.title).isEqualTo("Recuperação: fácil e curto")
        assertThat(prescription.prescribedDurationMin).isEqualTo(28)
        assertThat(prescription.ruleIds).contains("R-RACE-04", "R-PRESC-02")
        assertThat(prescription.rationale).contains("recuperação após a prova")
    }

    @Test
    fun `caps an easy session above the recovery ceiling`() {
        val recovery = RecoveryWindow(
            CompletedRace("race-1", RaceDistance.HALF_MARATHON, monday.minusDays(1)),
        )
        val easy = easySession(durationMin = 50)

        val prescription = engine.prescribe(
            input(
                date = monday,
                planned = easy,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                recovery = recovery,
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.REDUCE)
        assertThat(prescription.prescribedDurationMin).isEqualTo(30)
        assertThat(prescription.title).isEqualTo("Recuperação: fácil e curto")
        assertThat(prescription.ruleIds).contains("R-RACE-04")
    }

    @Test
    fun `downgrades the long run during recovery`() {
        val recovery = RecoveryWindow(
            CompletedRace("race-1", RaceDistance.HALF_MARATHON, monday.minusDays(5)),
        )
        val longRun = PlannedSession(
            date = monday,
            type = WorkoutType.LONG,
            title = "Longão",
            durationMin = 90,
            rationale = "Longão.",
            ruleIds = listOf("R-PLAN-03"),
            evidenceIds = listOf("EV-PFITZINGER-2008"),
        )

        val prescription = engine.prescribe(
            input(
                date = monday,
                planned = longRun,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                recovery = recovery,
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(prescription.prescribedDurationMin).isEqualTo(30)
        assertThat(prescription.ruleIds).contains("R-RACE-04")
        assertThat(prescription.rationale).contains("O longão fica para quando a recuperação permitir")
    }

    @Test
    fun `keeps an easy session already inside the cap`() {
        val recovery = RecoveryWindow(
            CompletedRace("race-1", RaceDistance.TEN_K, monday.minusDays(1)),
        )
        val easy = easySession(durationMin = 25)

        val prescription = engine.prescribe(
            input(
                date = monday,
                planned = easy,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                recovery = recovery,
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(prescription.prescribedDurationMin).isEqualTo(25)
    }

    @Test
    fun `ignores recovery outside the window`() {
        val recovery = RecoveryWindow(
            CompletedRace("race-1", RaceDistance.FIVE_K, monday.minusDays(4)),
        )
        val easy = easySession(durationMin = 50)

        val prescription = engine.prescribe(
            input(
                date = monday,
                planned = easy,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                recovery = recovery,
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(prescription.prescribedDurationMin).isEqualTo(50)
        assertThat(prescription.ruleIds).doesNotContain("R-RACE-04")
    }

    @Test
    fun `a done quality is not downgraded by recovery`() {
        val plan = planWithQuality()
        val quality = plan.sessions.first { it.type == WorkoutType.QUALITY }
        val done = quality.copy(
            status = SessionStatus.DONE,
            completionEvidence = com.meucoach.core.model.CompletionEvidence.MANUAL,
        )
        val recovery = RecoveryWindow(
            CompletedRace("race-1", RaceDistance.TEN_K, monday.minusDays(3)),
        )

        val prescription = engine.prescribe(
            input(
                date = done.date,
                planned = done,
                plan = plan,
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                recovery = recovery,
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.QUALITY)
        assertThat(prescription.ruleIds).doesNotContain("R-RACE-04")
    }

    private fun input(
        date: LocalDate = monday,
        planned: PlannedSession?,
        plan: WeeklyPlan? = null,
        profile: AthleteProfile = profile(),
        readiness: ReadinessResult,
        load: LoadSnapshot?,
        heat: HeatLevel = HeatLevel.NONE,
        recovery: RecoveryWindow? = null,
        phase: MesoPhase? = null,
        raceWeek: Boolean = false,
    ) = DailyPrescriptionInput(
        date = date,
        profile = profile,
        readiness = readiness,
        load = load,
        heatLevel = heat,
        plannedSession = planned,
        plannedSessionId = planned?.let { "${plan?.weekStart}-${it.date}" },
        planConfidence = plan?.confidence,
        recovery = recovery,
        phase = phase,
        raceWeek = raceWeek,
    )

    private fun planWithQuality(): WeeklyPlan = WeeklyPlanGenerator().generate(
        WeeklyPlanInput(
            profile = profile(
                experience = Experience.INTERMEDIATE,
                availability = mapOf(
                    DayOfWeek.MONDAY to 40,
                    DayOfWeek.WEDNESDAY to 40,
                    DayOfWeek.FRIDAY to 40,
                    DayOfWeek.SATURDAY to 90,
                ),
            ),
            readiness = readiness(ReadinessLevel.GREEN),
            load = load(acwr = 1.0),
            weekStart = monday,
            today = monday,
            race = RacePlan("race-1", RaceDistance.FIVE_K, monday.plusDays(34)),
        ),
    )

    private fun easySession(durationMin: Int) = PlannedSession(
        date = monday,
        type = WorkoutType.EASY,
        title = "Rodagem",
        durationMin = durationMin,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
    )

    private fun plannedQuality(kind: QualityKind, durationMin: Int) = PlannedSession(
        date = monday,
        type = WorkoutType.QUALITY,
        title = "Ritmo forte",
        durationMin = durationMin,
        rationale = "Qualidade planejada.",
        ruleIds = listOf("R-PER-02", "R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        qualityKind = kind,
        planDecision = PlanDecision(PlanDecisionReason.QUALITY_OPPORTUNITY),
    )

    private fun readiness(
        level: ReadinessLevel,
        flags: Set<ReadinessFlag> = emptySet(),
        confidence: DataConfidence = DataConfidence.HIGH,
        score: Int = when (level) {
            ReadinessLevel.GREEN -> 80
            ReadinessLevel.AMBER -> 55
            ReadinessLevel.RED -> 20
        },
        components: Map<ReadinessComponent, Int> = mapOf(ReadinessComponent.SUBJECTIVE to 80),
    ) = ReadinessResult(
        score = score,
        level = level,
        confidence = confidence,
        components = components,
        missing = ReadinessComponent.entries.toSet() - components.keys,
        flags = flags,
    )

    private fun profile(
        experience: Experience = Experience.INTERMEDIATE,
        availability: Map<DayOfWeek, Int> = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SATURDAY to 90,
        ),
        calibrated: Boolean = true,
    ) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = if (calibrated) 180 else null,
        thresholdPace = if (calibrated) Pace.fromSecondsPerKm(360) else null,
        experience = experience,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(availability),
    )

    private fun load(
        acwr: Double?,
        daysWithLoad: Int = 12,
        sourceFresh: Boolean = true,
        subjectiveDaysInWindow: Int = 0,
        monotony: Double? = 1.2,
        strain: Double? = 400.0,
        daysWithoutData: Int = 0,
    ): LoadSnapshot {
        val withoutData = if (sourceFresh) daysWithoutData else 28 - daysWithLoad
        val daysWithoutActivity = if (sourceFresh) 28 - daysWithLoad - withoutData else 0
        return LoadSnapshot(
            daily = emptyList(),
            ctlSeries = emptyList(),
            atlSeries = emptyList(),
            ctl = 0.0,
            atl = 0.0,
            tsb = 0.0,
            acuteAverage7d = 0.0,
            chronicAverage28d = 0.0,
            acwr = acwr,
            monotony = monotony,
            missingDays = 28 - daysWithLoad,
            coverage = LoadCoverage(
                daysInWindow = 28,
                daysWithLoad = daysWithLoad,
                daysWithoutActivity = daysWithoutActivity,
                daysWithoutData = daysWithoutData,
                lastActivityAt = null,
                sourceFresh = sourceFresh,
            ),
            subjectiveDaysInWindow = subjectiveDaysInWindow,
            strain = strain,
        )
    }
}
