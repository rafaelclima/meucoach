package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.briefing.SessionBriefingEngine
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.HeatLevel
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * C4-57 (FR-410, ADR 0047 §2.8): the pace × HR precedence is explicit.
 * Easy/long is led by HR/RPE with pace as reference; quality is led by the
 * block pace with HR as guardrail; without calibration RPE leads.
 */
class TargetPrecedenceTest {
    private val date = LocalDate.of(2026, 11, 10)

    @Test
    fun `calibrated easy is led by heart rate`() {
        val targets = PrescriptionPolicy.targets(WorkoutType.EASY, calibrated(), 40)

        assertThat(targets.primary).isEqualTo(TargetKind.HEART_RATE)
        assertThat(targets.pace).isNotNull
        assertThat(targets.heartRate).isNotNull
        assertThat(targets.rpe).isNull()
    }

    @Test
    fun `uncalibrated easy is led by RPE`() {
        val targets = PrescriptionPolicy.targets(WorkoutType.EASY, uncalibrated(), 40)

        assertThat(targets.primary).isEqualTo(TargetKind.RPE)
        assertThat(targets.pace).isNull()
        assertThat(targets.heartRate).isNull()
        assertThat(targets.rpe).isEqualTo(4)
    }

    @Test
    fun `easy without LTHR is led by RPE with pace as reference`() {
        val targets = PrescriptionPolicy.targets(WorkoutType.EASY, paceOnly(), 40)

        assertThat(targets.primary).isEqualTo(TargetKind.RPE)
        assertThat(targets.heartRate).isNull()
        assertThat(targets.pace).isNotNull
        assertThat(targets.rpe).isEqualTo(4)
    }

    @Test
    fun `calibrated long is led by heart rate`() {
        val targets = PrescriptionPolicy.targets(WorkoutType.LONG, calibrated(), 60)

        assertThat(targets.primary).isEqualTo(TargetKind.HEART_RATE)
        assertThat(targets.heartRate).isNotNull
    }

    @Test
    fun `calibrated quality is led by pace`() {
        val targets = PrescriptionPolicy.targets(WorkoutType.QUALITY, calibrated(), 40)

        assertThat(targets.primary).isEqualTo(TargetKind.PACE)
        assertThat(targets.pace).isNotNull
        assertThat(targets.heartRate).isNotNull
    }

    @Test
    fun `quality with heart rate only is mixed`() {
        val targets = PrescriptionPolicy.targets(WorkoutType.QUALITY, heartRateOnly(), 40)

        assertThat(targets.primary).isEqualTo(TargetKind.MIXED)
        assertThat(targets.pace).isNull()
        assertThat(targets.heartRate).isNotNull
    }

    @Test
    fun `continuous quality keeps the threshold pace and Z3-Z4 guardrail`() {
        val targets = PrescriptionPolicy.targets(
            WorkoutType.QUALITY,
            calibrated(),
            40,
            qualityKind = QualityKind.CONTINUOUS,
        )

        assertThat(targets.primary).isEqualTo(TargetKind.PACE)
        assertThat(targets.pace?.fast?.secondsPerKm).isEqualTo(360)
        assertThat(targets.pace?.slow?.secondsPerKm).isEqualTo(371)
        assertThat(targets.heartRate?.minBpm).isEqualTo(162)
        assertThat(targets.heartRate?.maxBpm).isEqualTo(179)
        assertThat(targets.zone).isEqualTo("Z3–Z4")
    }

    @Test
    fun `intervals use I pace and a Z5 lower bound, never the T target`() {
        val targets = PrescriptionPolicy.targets(
            WorkoutType.QUALITY,
            calibrated(),
            40,
            qualityKind = QualityKind.INTERVALS,
        )

        assertThat(targets.primary).isEqualTo(TargetKind.PACE)
        assertThat(targets.pace?.fast?.secondsPerKm).isEqualTo(331)
        assertThat(targets.pace?.slow?.secondsPerKm).isEqualTo(342)
        assertThat(targets.heartRate?.minBpm).isEqualTo(180)
        assertThat(targets.heartRate?.maxBpm).isNull()
        assertThat(targets.zone).isEqualTo("Z5")
        assertThat(targets.rpe).isNull()
    }

    @Test
    fun `fartlek hills and strides are effort led`() {
        listOf(QualityKind.FARTLEK, QualityKind.HILLS, QualityKind.STRIDES_ONLY).forEach { kind ->
            val targets = PrescriptionPolicy.targets(
                WorkoutType.QUALITY,
                calibrated(),
                40,
                qualityKind = kind,
            )

            assertThat(targets.primary).describedAs(kind.name).isEqualTo(TargetKind.RPE)
            assertThat(targets.rpe).describedAs(kind.name).isEqualTo(8)
            assertThat(targets.pace).describedAs(kind.name).isNull()
            assertThat(targets.heartRate).describedAs(kind.name).isNull()
            assertThat(targets.zone).describedAs(kind.name).isNull()
        }
    }

    @Test
    fun `race pace without a prediction falls back to RPE 8`() {
        val targets = PrescriptionPolicy.targets(
            WorkoutType.QUALITY,
            calibrated(),
            40,
            qualityKind = QualityKind.RACE_PACE,
        )

        assertThat(targets.primary).isEqualTo(TargetKind.RPE)
        assertThat(targets.rpe).isEqualTo(8)
        assertThat(targets.pace).isNull()
        assertThat(targets.heartRate).isNull()
    }

    @Test
    fun `uncalibrated intervals fall back to RPE 8, not an invented I pace`() {
        val targets = PrescriptionPolicy.targets(
            WorkoutType.QUALITY,
            uncalibrated(),
            40,
            qualityKind = QualityKind.INTERVALS,
        )

        assertThat(targets.primary).isEqualTo(TargetKind.RPE)
        assertThat(targets.rpe).isEqualTo(8)
        assertThat(targets.pace).isNull()
        assertThat(targets.heartRate).isNull()
        assertThat(targets.zone).isNull()
    }

    @Test
    fun `uncalibrated quality is led by RPE`() {
        val targets = PrescriptionPolicy.targets(WorkoutType.QUALITY, uncalibrated(), 40)

        assertThat(targets.primary).isEqualTo(TargetKind.RPE)
        assertThat(targets.rpe).isEqualTo(7)
    }

    @Test
    fun `heat keeps the precedence while slowing the pace`() {
        val targets = PrescriptionPolicy.targets(WorkoutType.EASY, calibrated(), 40, heat = HeatLevel.HOT)

        assertThat(targets.primary).isEqualTo(TargetKind.HEART_RATE)
        assertThat(targets.pace).isNotNull
    }

    @Test
    fun `easy briefing leads with heart rate and keeps pace as reference`() {
        val briefing = brief(
            type = WorkoutType.EASY,
            targets = PrescriptionTargets(
                pace = PaceTarget(Pace.of(7, 40), Pace.of(8, 20)),
                heartRate = HeartRateTarget(minBpm = 146, maxBpm = 161),
                zone = "Z2",
                primary = TargetKind.HEART_RATE,
            ),
        )

        assertThat(briefing.howToRun).contains("Guie pelo esforço e pela FC")
        assertThat(briefing.howToRun).contains("o pace é referência")
        assertThat(briefing.howToRun).contains("146")
        assertThat(briefing.howToRun).doesNotContain("Se usar")
    }

    @Test
    fun `quality briefing leads with the block pace and keeps heart rate as guardrail`() {
        val briefing = brief(
            type = WorkoutType.QUALITY,
            targets = PrescriptionTargets(
                pace = PaceTarget(Pace.of(5, 20), Pace.of(5, 40)),
                heartRate = HeartRateTarget(minBpm = 165, maxBpm = 178),
                zone = "Z3–Z4",
                primary = TargetKind.PACE,
            ),
        )

        assertThat(briefing.howToRun).contains("Use o pace do bloco")
        assertThat(briefing.howToRun).contains("a FC confirma")
        assertThat(briefing.howToRun).contains("5:20")
        assertThat(briefing.howToRun).doesNotContain("Se usar")
    }

    @Test
    fun `RPE briefing keeps pace as reference when it exists`() {
        val briefing = brief(
            type = WorkoutType.EASY,
            targets = PrescriptionTargets(
                pace = PaceTarget(Pace.of(7, 40), Pace.of(8, 20)),
                rpe = 4,
                primary = TargetKind.RPE,
            ),
        )

        assertThat(briefing.howToRun).contains("esforço percebido 4/10")
        assertThat(briefing.howToRun).contains("o pace é referência")
    }

    private fun brief(type: WorkoutType, targets: PrescriptionTargets) = SessionBriefingEngine().brief(
        DailyPrescription(
            date = date,
            plannedSessionId = "plan-$date",
            plannedType = type,
            plannedTitle = "Treino",
            plannedDurationMin = 40,
            prescribedType = type,
            title = "Treino",
            prescribedDurationMin = 40,
            targets = targets,
            adjustment = PrescriptionAdjustment.KEEP,
            rationale = "Plano mantido.",
            ruleIds = listOf("R-PRESC-01", "R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010", "EV-DANIELS-2014"),
            confidence = DataConfidence.MEDIUM,
            engineVersion = "daily-prescription-v5",
            inputsHash = "inputs-hash",
        ),
        SessionStatus.PLANNED,
    )!!

    private fun calibrated() = profile(thresholdPace = Pace.fromSecondsPerKm(360), lthr = 180)

    private fun uncalibrated() = profile(thresholdPace = null, lthr = null)

    private fun paceOnly() = profile(thresholdPace = Pace.fromSecondsPerKm(360), lthr = null)

    private fun heartRateOnly() = profile(thresholdPace = null, lthr = 180)

    private fun profile(thresholdPace: Pace?, lthr: Int?) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = lthr,
        thresholdPace = thresholdPace,
        experience = Experience.INTERMEDIATE,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(mapOf(DayOfWeek.TUESDAY to 40)),
    )
}
