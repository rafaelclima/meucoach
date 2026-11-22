package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * C4-19 — estrutura no dia (ADR 0047 §2.1).
 *
 * KEEP copia e ajusta a estrutura; DOWNGRADE de qualidade derruba a forma
 * forte; EASY reduzido reajusta sem prometer tempo extra.
 */
class DailyStructureTest {
    private val engine = DailyPrescriptionEngine()
    private val date = LocalDate.of(2026, 11, 10)

    private fun qualityStructure() = WorkoutStructure(
        warmupMin = 10,
        blocks = listOf(
            WorkoutBlock(
                kind = BlockKind.WORK,
                repetitions = 5,
                workSec = 180,
                recoverySec = 120,
                target = "I",
            ),
        ),
        cooldownMin = 5,
        totalMin = 40,
    )

    private fun stridesStructure() = WorkoutStructure(
        warmupMin = 8,
        blocks = listOf(
            WorkoutBlock(
                kind = BlockKind.STRIDES,
                repetitions = 6,
                workSec = 20,
                recoverySec = 60,
                target = "RPE 8",
            ),
        ),
        cooldownMin = 9,
        totalMin = 25,
    )

    private fun plannedQuality() = PlannedSession(
        date = date,
        type = WorkoutType.QUALITY,
        title = "Intervalado",
        durationMin = 40,
        rationale = "Qualidade.",
        ruleIds = listOf("R-PER-02"),
        evidenceIds = listOf("EV-DANIELS-2014"),
        qualityKind = QualityKind.INTERVALS,
        structure = qualityStructure(),
    )

    private fun plannedStrides(duration: Int = 25) = PlannedSession(
        date = date,
        type = WorkoutType.EASY,
        title = "Acelerações curtas",
        durationMin = duration,
        rationale = "Strides.",
        ruleIds = listOf("R-PER-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        structure = stridesStructure().fittedTo(duration),
    )

    @Test
    fun `kept quality carries the fitted structure`() {
        val prescription = engine.prescribe(
            DailyPrescriptionInput(
                date = date,
                profile = profile(),
                readiness = readiness(),
                load = load(),
                plannedSession = plannedQuality(),
                plannedSessionId = "plan-$date",
                planConfidence = DataConfidence.MEDIUM,
                sessionStatus = SessionStatus.PLANNED,
            ),
        )

        val structure = requireNotNull(prescription.structure) { "Kept quality must carry structure" }
        assertThat(structure.totalMin).isEqualTo(prescription.prescribedDurationMin)
        assertThat(structure.plannedSeconds).isLessThanOrEqualTo(40L * 60L)
    }

    @Test
    fun `downgraded quality drops the strong structure`() {
        val prescription = engine.prescribe(
            DailyPrescriptionInput(
                date = date,
                profile = profile(),
                readiness = readiness(
                    components = emptyMap(),
                    flags = setOf(com.meucoach.core.domain.readiness.ReadinessFlag.SUBJECTIVE_MISSING),
                ),
                load = load(),
                plannedSession = plannedQuality(),
                plannedSessionId = "plan-$date",
                planConfidence = DataConfidence.MEDIUM,
                sessionStatus = SessionStatus.PLANNED,
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(prescription.structure).isNull()
    }

    @Test
    fun `kept easy strides carry the structure`() {
        val prescription = engine.prescribe(
            DailyPrescriptionInput(
                date = date,
                profile = profile(),
                readiness = readiness(),
                load = load(),
                plannedSession = plannedStrides(),
                plannedSessionId = "plan-$date",
                planConfidence = DataConfidence.MEDIUM,
                sessionStatus = SessionStatus.PLANNED,
            ),
        )

        assertThat(prescription.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        val structure = requireNotNull(prescription.structure) { "Easy strides must carry structure" }
        assertThat(structure.totalMin).isEqualTo(prescription.prescribedDurationMin)
    }

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(360),
        experience = Experience.INTERMEDIATE,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(mapOf(DayOfWeek.TUESDAY to 40)),
    )

    private fun readiness(
        components: Map<ReadinessComponent, Int> = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        flags: Set<com.meucoach.core.domain.readiness.ReadinessFlag> = emptySet(),
    ) = ReadinessResult(
        score = 82,
        level = ReadinessLevel.GREEN,
        confidence = DataConfidence.HIGH,
        components = components,
        missing = emptySet(),
        flags = flags,
    )

    private fun load() = LoadSnapshot(
        daily = emptyList(),
        ctlSeries = emptyList(),
        atlSeries = emptyList(),
        ctl = 20.0,
        atl = 21.0,
        tsb = -1.0,
        acuteAverage7d = 21.0,
        chronicAverage28d = 20.0,
        acwr = 1.05,
        monotony = 1.2,
        missingDays = 16,
        coverage = LoadCoverage(
            daysInWindow = 28,
            daysWithLoad = 12,
            daysWithoutActivity = 16,
            daysWithoutData = 0,
            lastActivityAt = null,
            sourceFresh = true,
        ),
        strain = 180.0,
    )
}
