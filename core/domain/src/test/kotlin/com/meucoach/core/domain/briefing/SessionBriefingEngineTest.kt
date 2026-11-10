package com.meucoach.core.domain.briefing

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.DailyPrescriptionEngine
import com.meucoach.core.domain.prescription.DailyPrescriptionInput
import com.meucoach.core.domain.prescription.HeartRateTarget
import com.meucoach.core.domain.prescription.PaceTarget
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WorkoutType
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

class SessionBriefingEngineTest {
    private val engine = SessionBriefingEngine()
    private val date = LocalDate.of(2026, 11, 10)

    @Test
    fun `copies every number from the persisted prescription`() {
        val prescription = prescription(
            durationMin = 30,
            targets = PrescriptionTargets(
                pace = PaceTarget(Pace.of(7, 40), Pace.of(8, 20)),
                heartRate = HeartRateTarget(minBpm = 146, maxBpm = 161),
                zone = "Z2",
                distanceKm = 6.0,
            ),
        )

        val briefing = engine.brief(prescription, SessionStatus.PLANNED)

        assertThat(briefing).isNotNull
        val result = briefing!!
        assertThat(result.date).isEqualTo(prescription.date)
        assertThat(result.title).isEqualTo(prescription.title)
        assertThat(result.workoutType).isEqualTo(prescription.prescribedType)
        assertThat(result.durationMin).isEqualTo(prescription.prescribedDurationMin)
        assertThat(result.targets).isEqualTo(prescription.targets)
        assertThat(result.rationale).isEqualTo(prescription.rationale)
        assertThat(result.confidence).isEqualTo(prescription.confidence)
        assertThat(result.engineVersion).isEqualTo(SessionBriefingEngine.ENGINE_VERSION)
    }

    @Test
    fun `is deterministic for the same prescription and engine version`() {
        val prescription = prescription()

        val first = engine.brief(prescription, SessionStatus.PLANNED)
        val second = engine.brief(prescription, SessionStatus.PLANNED)

        assertThat(first).isEqualTo(second)
        assertThat(first!!.engineVersion).isEqualTo(SessionBriefingEngine.ENGINE_VERSION)
    }

    @Test
    fun `without calibration keeps the existing RPE and never computes pace or heart rate`() {
        val prescription = prescription(
            targets = PrescriptionTargets(zone = "Z2", rpe = 4),
        )

        val briefing = engine.brief(prescription, SessionStatus.PLANNED)!!

        assertThat(briefing.targets.rpe).isEqualTo(4)
        assertThat(briefing.targets.pace).isNull()
        assertThat(briefing.targets.heartRate).isNull()
        assertThat(briefing.targets.distanceKm).isNull()
        assertThat(briefing.howToRun).contains("esforço percebido 4/10")
        assertThat(briefing.howToRun).doesNotContain("/km")
        assertThat(briefing.howToRun).doesNotContain("bpm")
    }

    @Test
    fun `uses only the pace and heart rate already present in the prescription`() {
        val prescription = prescription(
            targets = PrescriptionTargets(
                pace = PaceTarget(Pace.of(7, 40), Pace.of(8, 20)),
                heartRate = HeartRateTarget(minBpm = 146, maxBpm = 161),
                zone = "Z2",
            ),
        )

        val briefing = engine.brief(prescription, SessionStatus.PLANNED)!!

        assertThat(briefing.howToRun).contains("7:40")
        assertThat(briefing.howToRun).contains("8:20")
        assertThat(briefing.howToRun).contains("146")
        assertThat(briefing.howToRun).contains("161")
    }

    @Test
    fun `never invents blocks, intervals, warm-ups or alternatives`() {
        val prescription = prescription(
            targets = PrescriptionTargets(
                pace = PaceTarget(Pace.of(7, 40), Pace.of(8, 20)),
                heartRate = HeartRateTarget(minBpm = 146, maxBpm = 161),
                zone = "Z2",
            ),
        )

        val briefing = engine.brief(prescription, SessionStatus.PLANNED)!!
        val text = "${briefing.howToRun} ${briefing.ifHard}".lowercase()

        assertThat(text).doesNotContain("aquec")
        assertThat(text).doesNotContain("desaquec")
        assertThat(text).doesNotContain("interval")
        assertThat(text).doesNotContain("série")
        assertThat(text).doesNotContain("bloco")
        assertThat(text).doesNotContain("alternativ")
        assertThat(text).doesNotContain("repeti")
    }

    @Test
    fun `has no retrospective briefing for a done or closed session`() {
        val prescription = prescription()

        assertThat(engine.brief(prescription, SessionStatus.DONE)).isNull()
        assertThat(engine.brief(prescription, SessionStatus.CLOSED)).isNull()
        assertThat(engine.brief(prescription, SessionStatus.PLANNED)).isNotNull
        assertThat(engine.brief(prescription, sessionStatus = null)).isNotNull
    }

    @Test
    fun `reflects the beginner quality to easy downgrade from the persisted prescription`() {
        val plannedQuality = PlannedSession(
            date = date,
            type = WorkoutType.QUALITY,
            title = "Tempo",
            durationMin = 40,
            rationale = "Treino forte planejado.",
            ruleIds = listOf("R-PLAN-02"),
            evidenceIds = listOf("EV-DANIELS-2014"),
        )

        val kept = DailyPrescriptionEngine().prescribe(
            DailyPrescriptionInput(
                date = date,
                profile = profile(Experience.INTERMEDIATE),
                readiness = readiness(),
                load = knownLoad(),
                plannedSession = plannedQuality,
                plannedSessionId = "plan-$date",
                planConfidence = DataConfidence.MEDIUM,
                sessionStatus = SessionStatus.PLANNED,
            ),
        )
        val downgraded = DailyPrescriptionEngine().prescribe(
            DailyPrescriptionInput(
                date = date,
                profile = profile(Experience.BEGINNER),
                readiness = readiness(),
                load = knownLoad(),
                plannedSession = plannedQuality,
                plannedSessionId = "plan-$date",
                planConfidence = DataConfidence.MEDIUM,
                sessionStatus = SessionStatus.PLANNED,
            ),
        )

        assertThat(kept.adjustment).isEqualTo(PrescriptionAdjustment.KEEP)
        assertThat(kept.prescribedType).isEqualTo(WorkoutType.QUALITY)
        assertThat(downgraded.adjustment).isEqualTo(PrescriptionAdjustment.DOWNGRADE)
        assertThat(downgraded.prescribedType).isEqualTo(WorkoutType.EASY)

        val keptBriefing = engine.brief(kept, SessionStatus.PLANNED)!!
        val downgradedBriefing = engine.brief(downgraded, SessionStatus.PLANNED)!!

        assertThat(keptBriefing.workoutType).isEqualTo(WorkoutType.QUALITY)
        assertThat(keptBriefing.title).isEqualTo(kept.title)
        assertThat(downgradedBriefing.workoutType).isEqualTo(WorkoutType.EASY)
        assertThat(downgradedBriefing.title).isEqualTo(downgraded.title)
        assertThat(downgradedBriefing.durationMin).isEqualTo(downgraded.prescribedDurationMin)
        assertThat(downgradedBriefing.targets).isEqualTo(downgraded.targets)
        assertThat(downgradedBriefing.rationale).isEqualTo(downgraded.rationale)
        assertThat(downgradedBriefing.howToRun).contains("esforço fácil")
        assertThat(downgradedBriefing.howToRun).doesNotContain("treino forte")
    }

    @Test
    fun `preserves the persisted rule and evidence IDs for audit and cites the stop rule`() {
        val prescription = prescription()

        val briefing = engine.brief(prescription, SessionStatus.PLANNED)!!

        assertThat(briefing.ruleIds).containsAll(prescription.ruleIds)
        assertThat(briefing.ruleIds).contains("R-SAFE-01")
        assertThat(briefing.evidenceIds).containsExactlyElementsOf(prescription.evidenceIds)
        assertThat(briefing.howToRun).doesNotContain("R-")
        assertThat(briefing.ifHard).doesNotContain("R-")
        assertThat(briefing.ifHard).contains("Reduza o esforço")
        assertThat(briefing.ifHard).contains("procurar avaliação")
    }

    @Test
    fun `describes an existing rest prescription without inventing a duration or target`() {
        val prescription = prescription(
            type = WorkoutType.REST,
            title = "Descanso",
            durationMin = null,
            targets = PrescriptionTargets(),
            adjustment = PrescriptionAdjustment.REST,
        )

        val briefing = engine.brief(prescription, SessionStatus.PLANNED)!!

        assertThat(briefing.workoutType).isEqualTo(WorkoutType.REST)
        assertThat(briefing.durationMin).isNull()
        assertThat(briefing.targets.isEmpty).isTrue()
        assertThat(briefing.howToRun).contains("descanso")
        assertThat(briefing.howToRun).doesNotContain("/km")
        assertThat(briefing.howToRun).doesNotContain("bpm")
    }

    @Test
    fun `rest with the stop rule explains why there is no training today`() {
        val prescription = prescription(
            type = WorkoutType.REST,
            title = "Descanso",
            durationMin = null,
            targets = PrescriptionTargets(),
            adjustment = PrescriptionAdjustment.REST,
        ).copy(ruleIds = listOf("R-SAFE-01", "R-READY-03", "R-PRESC-02"))

        val briefing = engine.brief(prescription, SessionStatus.PLANNED)!!

        assertThat(briefing.howToRun).contains("Não treine hoje")
        assertThat(briefing.ifHard).contains("mantenha o descanso")
        assertThat(briefing.ruleIds).contains("R-SAFE-01")
    }

    @Test
    fun `open ended heart rate keeps the persisted minimum`() {
        val prescription = prescription(
            targets = PrescriptionTargets(
                heartRate = HeartRateTarget(minBpm = 150, maxBpm = null),
                zone = "Z2",
            ),
        )

        val briefing = engine.brief(prescription, SessionStatus.PLANNED)!!

        assertThat(briefing.howToRun).contains("a partir de 150 bpm")
        assertThat(briefing.targets.heartRate).isEqualTo(prescription.targets.heartRate)
    }

    private fun prescription(
        type: WorkoutType = WorkoutType.EASY,
        title: String = "Fácil e conversável",
        durationMin: Int? = 30,
        targets: PrescriptionTargets = PrescriptionTargets(),
        adjustment: PrescriptionAdjustment = PrescriptionAdjustment.KEEP,
    ) = DailyPrescription(
        date = date,
        plannedSessionId = "plan-$date",
        plannedType = type,
        plannedTitle = title,
        plannedDurationMin = durationMin,
        prescribedType = type,
        title = title,
        prescribedDurationMin = durationMin,
        targets = targets,
        adjustment = adjustment,
        rationale = "Plano mantido: $title, em ritmo fácil e conversável.",
        ruleIds = listOf("R-PRESC-01", "R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010", "EV-DANIELS-2014"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = "daily-prescription-v2",
        inputsHash = "inputs-hash",
    )

    private fun readiness() = ReadinessResult(
        score = 82,
        level = ReadinessLevel.GREEN,
        confidence = DataConfidence.HIGH,
        components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        missing = emptySet(),
        flags = emptySet(),
    )

    private fun profile(experience: Experience) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(360),
        experience = experience,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(mapOf(DayOfWeek.TUESDAY to 40)),
    )

    private fun knownLoad() = LoadSnapshot(
        daily = emptyList(),
        ctlSeries = emptyList(),
        atlSeries = emptyList(),
        ctl = 20.0,
        atl = 21.0,
        tsb = -1.0,
        acuteAverage7d = 21.0,
        chronicAverage28d = 20.0,
        acwr = 1.05,
        monotony = null,
        missingDays = 16,
        coverage = LoadCoverage(
            daysInWindow = 28,
            daysWithLoad = 12,
            daysWithoutActivity = 16,
            daysWithoutData = 0,
            lastActivityAt = null,
            sourceFresh = true,
        ),
    )
}
