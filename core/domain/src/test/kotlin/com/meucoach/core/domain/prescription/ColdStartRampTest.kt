package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import com.meucoach.core.model.VdotSource
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * ADR 0053 — cold-start ramp: a calibrated intermediate keeps one short
 * quality (≤30 min) without 8 objective days. Safety gates still win;
 * BEGINNER, long sessions and stale calibration stay blocked.
 */
class ColdStartRampTest {
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `intermediate with recent VDOT keeps short quality without load`() {
        val eligibility = QualityGate.evaluate(
            plannedSession = session(durationMin = 25),
            readiness = readiness(),
            load = null,
            recovery = null,
            profile = profile(Experience.INTERMEDIATE),
            date = monday,
            coldStart = allowance(valid = true),
        )

        assertThat(eligibility.eligible).isTrue()
        assertThat(eligibility.ruleId).isEqualTo("R-COLD-01")
    }

    @Test
    fun `intermediate with insufficient load keeps short quality with recent VDOT`() {
        val eligibility = QualityGate.evaluate(
            plannedSession = session(durationMin = 30),
            readiness = readiness(),
            load = load(daysWithLoad = 2),
            recovery = null,
            profile = profile(Experience.INTERMEDIATE),
            date = monday,
            coldStart = allowance(valid = true),
        )

        assertThat(eligibility.eligible).isTrue()
        assertThat(eligibility.ruleId).isEqualTo("R-COLD-01")
    }

    @Test
    fun `beginner stays blocked even with recent VDOT`() {
        val eligibility = QualityGate.evaluate(
            plannedSession = session(durationMin = 25),
            readiness = readiness(),
            load = null,
            recovery = null,
            profile = profile(Experience.BEGINNER),
            date = monday,
            coldStart = allowance(valid = true),
        )

        assertThat(eligibility.eligible).isFalse()
        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.BEGINNER_GUARD)
    }

    @Test
    fun `long quality stays blocked on cold-start`() {
        val eligibility = QualityGate.evaluate(
            plannedSession = session(durationMin = 40),
            readiness = readiness(),
            load = null,
            recovery = null,
            profile = profile(Experience.INTERMEDIATE),
            date = monday,
            coldStart = allowance(valid = true),
        )

        assertThat(eligibility.eligible).isFalse()
        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.LOAD_INSUFFICIENT)
    }

    @Test
    fun `stale calibration stays blocked`() {
        val eligibility = QualityGate.evaluate(
            plannedSession = session(durationMin = 25),
            readiness = readiness(),
            load = null,
            recovery = null,
            profile = profile(Experience.INTERMEDIATE),
            date = monday,
            coldStart = allowance(valid = false),
        )

        assertThat(eligibility.eligible).isFalse()
        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.LOAD_INSUFFICIENT)
    }

    @Test
    fun `missing check-in still wins over cold-start`() {
        val eligibility = QualityGate.evaluate(
            plannedSession = session(durationMin = 25),
            readiness = readiness(components = emptyMap()),
            load = null,
            recovery = null,
            profile = profile(Experience.INTERMEDIATE),
            date = monday,
            coldStart = allowance(valid = true),
        )

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.SUBJECTIVE_MISSING)
    }

    @Test
    fun `short sleep still wins over cold-start`() {
        val eligibility = QualityGate.evaluate(
            plannedSession = session(durationMin = 25),
            readiness = readiness(flags = setOf(ReadinessFlag.SHORT_SLEEP)),
            load = null,
            recovery = null,
            profile = profile(Experience.INTERMEDIATE),
            date = monday,
            coldStart = allowance(valid = true),
        )

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.SHORT_SLEEP)
    }

    @Test
    fun `prescription keeps short quality on cold-start with low confidence`() {
        val prescription = DailyPrescriptionEngine().prescribe(
            DailyPrescriptionInput(
                date = monday,
                profile = profile(Experience.INTERMEDIATE),
                readiness = readiness(),
                load = null,
                plannedSession = session(durationMin = 25),
                coldStart = allowance(valid = true),
            ),
        )

        assertThat(prescription.prescribedType).isEqualTo(WorkoutType.QUALITY)
        assertThat(prescription.ruleIds).contains("R-COLD-01")
        assertThat(prescription.rationale).contains("Rampa de entrada")
    }

    @Test
    fun `prescription hash changes when cold-start becomes valid`() {
        val base = DailyPrescriptionInput(
            date = monday,
            profile = profile(Experience.INTERMEDIATE),
            readiness = readiness(),
            load = null,
            plannedSession = session(durationMin = 25),
            coldStart = allowance(valid = false),
        )
        val ramped = base.copy(coldStart = allowance(valid = true))

        val first = DailyPrescriptionEngine().prescribe(base)
        val second = DailyPrescriptionEngine().prescribe(ramped)

        assertThat(second.inputsHash).isNotEqualTo(first.inputsHash)
        assertThat(first.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(second.prescribedType).isEqualTo(WorkoutType.QUALITY)
    }

    private fun allowance(valid: Boolean) = if (valid) {
        ColdStartAllowance(
            vdot = 31.0,
            vdotSource = VdotSource.PERFORMANCE,
            calibratedAt = monday.minusDays(10),
            today = monday,
        )
    } else {
        ColdStartAllowance(
            vdot = null,
            vdotSource = null,
            calibratedAt = null,
            today = monday,
        )
    }

    private fun session(durationMin: Int) = PlannedSession(
        date = monday,
        type = WorkoutType.QUALITY,
        title = "Ritmo forte",
        durationMin = durationMin,
        rationale = "rampa de entrada",
        ruleIds = listOf("R-PER-03"),
        evidenceIds = listOf("EV-SEILER-2010"),
        qualityKind = QualityKind.CONTINUOUS,
        planDecision = PlanDecision(PlanDecisionReason.QUALITY_OPPORTUNITY),
    )

    private fun readiness(
        components: Map<ReadinessComponent, Int> = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        flags: Set<ReadinessFlag> = emptySet(),
    ) = ReadinessResult(
        score = 80,
        level = ReadinessLevel.GREEN,
        confidence = DataConfidence.HIGH,
        components = components,
        missing = ReadinessComponent.entries.toSet() - components.keys,
        flags = flags,
    )

    private fun profile(experience: Experience) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        experience = experience,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(
            mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.FRIDAY to 40,
                DayOfWeek.SATURDAY to 90,
            ),
        ),
        vdot = 31.0,
        vdotSource = VdotSource.PERFORMANCE,
        vdotCalibratedAt = monday.minusDays(10),
        vdotReferenceDistanceM = 5_000.0,
        vdotReferenceDurationSec = 1_859,
        thresholdPace = Pace.fromSecondsPerKm(378),
        lactateThresholdHeartRate = 180,
    )

    private fun load(daysWithLoad: Int): LoadSnapshot {
        val withoutData = 28 - daysWithLoad
        return LoadSnapshot(
            daily = emptyList(),
            ctlSeries = emptyList(),
            atlSeries = emptyList(),
            ctl = 0.0,
            atl = 0.0,
            tsb = 0.0,
            acuteAverage7d = 0.0,
            chronicAverage28d = 0.0,
            acwr = null,
            monotony = null,
            missingDays = withoutData,
            coverage = LoadCoverage(
                daysInWindow = 28,
                daysWithLoad = daysWithLoad,
                daysWithoutActivity = 0,
                daysWithoutData = withoutData,
                lastActivityAt = null,
                sourceFresh = true,
            ),
            subjectiveDaysInWindow = 0,
            strain = null,
        )
    }
}
