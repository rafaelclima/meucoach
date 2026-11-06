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
        assertThat(prescription.ruleIds).contains("R-PRESC-01", "R-PRESC-03")
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
        assertThat(prescription.rationale).contains("faltam dados de recuperação")
        assertThat(prescription.ruleIds).contains("R-PRESC-02")
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
        assertThat(prescription.targets).isEqualTo(PrescriptionTargets())
    }

    private fun input(
        date: LocalDate = monday,
        planned: PlannedSession?,
        plan: WeeklyPlan? = null,
        profile: AthleteProfile = profile(),
        readiness: ReadinessResult,
        load: LoadSnapshot?,
        heat: HeatLevel = HeatLevel.NONE,
    ) = DailyPrescriptionInput(
        date = date,
        profile = profile,
        readiness = readiness,
        load = load,
        heatLevel = heat,
        plannedSession = planned,
        plannedSessionId = planned?.let { "${plan?.weekStart}-${it.date}" },
        planConfidence = plan?.confidence,
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
            previousWeek = completedWeek(),
        ),
    )

    private fun easySession(durationMin: Int) = PlannedSession(
        date = monday,
        type = WorkoutType.EASY,
        title = "Fácil e conversável",
        durationMin = durationMin,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
    )

    private fun completedWeek(): WeeklyPlan {
        val previousMonday = monday.minusWeeks(1)
        val sessions = (0..3).map { index ->
            easySession(30).copy(
                date = previousMonday.plusDays(index.toLong()),
                status = SessionStatus.DONE,
            )
        }
        return WeeklyPlan(
            weekStart = previousMonday,
            sessions = sessions,
            weeklyMinutes = 120,
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Semana anterior.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
        )
    }

    private fun readiness(
        level: ReadinessLevel,
        flags: Set<ReadinessFlag> = emptySet(),
        confidence: DataConfidence = DataConfidence.HIGH,
        score: Int = when (level) {
            ReadinessLevel.GREEN -> 80
            ReadinessLevel.AMBER -> 55
            ReadinessLevel.RED -> 20
        },
    ) = ReadinessResult(
        score = score,
        level = level,
        confidence = confidence,
        components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        missing = emptySet(),
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

    private fun load(acwr: Double?, daysWithLoad: Int = 12, sourceFresh: Boolean = true): LoadSnapshot {
        val daysWithoutActivity = if (sourceFresh) 28 - daysWithLoad else 0
        val daysWithoutData = if (sourceFresh) 0 else 28 - daysWithLoad
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
            monotony = null,
            missingDays = 28 - daysWithLoad,
            coverage = LoadCoverage(
                daysInWindow = 28,
                daysWithLoad = daysWithLoad,
                daysWithoutActivity = daysWithoutActivity,
                daysWithoutData = daysWithoutData,
                lastActivityAt = null,
                sourceFresh = sourceFresh,
            ),
        )
    }
}
