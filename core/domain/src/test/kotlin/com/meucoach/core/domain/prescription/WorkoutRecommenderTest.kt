package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.HeatLevel
import com.meucoach.core.model.Pace
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WorkoutRecommenderTest {
    private val recommender = WorkoutRecommender()
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `illness or injury recommends rest`() {
        val recommendation = recommender.recommend(
            input(
                readiness = readiness(
                    level = ReadinessLevel.GREEN,
                    flags = setOf(ReadinessFlag.ILLNESS_OR_INJURY, ReadinessFlag.NO_QUALITY),
                ),
            ),
        )

        assertThat(recommendation.type).isEqualTo(WorkoutType.REST)
        assertThat(recommendation.ruleIds).contains("R-SAFE-01", "R-READY-03")
        assertThat(recommendation.confidence).isEqualTo(DataConfidence.HIGH)
    }

    @Test
    fun `red readiness recommends rest`() {
        val recommendation = recommender.recommend(
            input(readiness = readiness(level = ReadinessLevel.RED, score = 20)),
        )

        assertThat(recommendation.type).isEqualTo(WorkoutType.REST)
        assertThat(recommendation.ruleIds).contains("R-READY-03")
    }

    @Test
    fun `amber readiness keeps the day easy`() {
        val recommendation = recommender.recommend(
            input(
                readiness = readiness(
                    level = ReadinessLevel.AMBER,
                    flags = setOf(ReadinessFlag.NO_QUALITY),
                    score = 55,
                ),
            ),
        )

        assertThat(recommendation.type).isEqualTo(WorkoutType.EASY)
        assertThat(recommendation.title).isEqualTo("Fácil, sem pressa")
        assertThat(recommendation.ruleIds).contains("R-READY-03")
        assertThat(recommendation.rationale).contains("sem treino forte")
    }

    @Test
    fun `short sleep blocks quality`() {
        val recommendation = recommender.recommend(
            input(
                readiness = readiness(
                    level = ReadinessLevel.GREEN,
                    flags = setOf(ReadinessFlag.SHORT_SLEEP),
                ),
            ),
        )

        assertThat(recommendation.type).isEqualTo(WorkoutType.EASY)
        assertThat(recommendation.ruleIds).contains("R-READY-04")
        assertThat(recommendation.rationale).contains("Sono abaixo de 6 h")
    }

    @Test
    fun `high ACWR blocks load increases`() {
        val recommendation = recommender.recommend(
            input(
                readiness = readiness(level = ReadinessLevel.GREEN),
                load = knownLoad(acwr = 1.6),
            ),
        )

        assertThat(recommendation.type).isEqualTo(WorkoutType.EASY)
        assertThat(recommendation.ruleIds).contains("R-LOAD-03")
        assertThat(recommendation.rationale).contains("carga subiu rápido")
    }

    @Test
    fun `unknown load keeps the day easy and explains the missing data`() {
        val recommendation = recommender.recommend(
            input(
                readiness = readiness(level = ReadinessLevel.GREEN, confidence = DataConfidence.HIGH),
                load = null,
            ),
        )

        assertThat(recommendation.type).isEqualTo(WorkoutType.EASY)
        assertThat(recommendation.title).isEqualTo("Fácil, sem pressa")
        assertThat(recommendation.ruleIds).contains("R-DATA-01")
        assertThat(recommendation.evidenceIds).contains("EV-IMPELLIZZERI-2020")
        assertThat(recommendation.rationale).contains("carga suficiente", "8 dias")
        assertThat(recommendation.confidence).isEqualTo(DataConfidence.MEDIUM)
    }

    @Test
    fun `stale load keeps the day easy and explains the outdated data`() {
        val recommendation = recommender.recommend(
            input(
                readiness = readiness(level = ReadinessLevel.GREEN, confidence = DataConfidence.HIGH),
                load = knownLoad(acwr = 1.0).copy(
                    coverage = coverage(daysWithLoad = 12, sourceFresh = false),
                ),
            ),
        )

        assertThat(recommendation.type).isEqualTo(WorkoutType.EASY)
        assertThat(recommendation.ruleIds).contains("R-DATA-01")
        assertThat(recommendation.rationale).contains("desatualizados", "atividades recentes")
        assertThat(recommendation.confidence).isEqualTo(DataConfidence.MEDIUM)
    }

    @Test
    fun `calibrated green day gets pace and heart-rate targets`() {
        val recommendation = recommender.recommend(
            input(readiness = readiness(level = ReadinessLevel.GREEN, confidence = DataConfidence.HIGH)),
        )

        assertThat(recommendation.type).isEqualTo(WorkoutType.EASY)
        assertThat(recommendation.title).isEqualTo("Fácil e conversável")
        assertThat(recommendation.pace).isEqualTo(
            PaceTarget(
                fast = Pace.fromSecondsPerKm(450),
                slow = Pace.fromSecondsPerKm(504),
            ),
        )
        assertThat(recommendation.heartRate).isEqualTo(HeartRateTarget(minBpm = 146, maxBpm = 161))
        assertThat(recommendation.durationMin).isEqualTo(45)
        assertThat(recommendation.distanceKm).isEqualTo(5.7)
        assertThat(recommendation.confidence).isEqualTo(DataConfidence.HIGH)
    }

    @Test
    fun `uncalibrated profile gets an effort-based recommendation`() {
        val recommendation = recommender.recommend(
            input(
                profile = profile(threshold = null, lthr = null),
                readiness = readiness(level = ReadinessLevel.GREEN, confidence = DataConfidence.HIGH),
            ),
        )

        assertThat(recommendation.type).isEqualTo(WorkoutType.EASY)
        assertThat(recommendation.pace).isNull()
        assertThat(recommendation.heartRate).isNull()
        assertThat(recommendation.ruleIds).contains("R-CAL-02")
        assertThat(recommendation.confidence).isEqualTo(DataConfidence.LOW)
        assertThat(recommendation.rationale).contains("esforço")
    }

    @Test
    fun `an explicit day without availability recommends rest`() {
        val recommendation = recommender.recommend(
            input(
                profile = profile(
                    availability = WeeklyAvailability(mapOf(DayOfWeek.TUESDAY to 40)),
                ),
                readiness = readiness(level = ReadinessLevel.GREEN),
            ),
        )

        assertThat(recommendation.type).isEqualTo(WorkoutType.REST)
        assertThat(recommendation.rationale).contains("disponibilidade")
    }

    @Test
    fun `empty availability falls back to thirty minutes`() {
        val recommendation = recommender.recommend(
            input(
                profile = profile(availability = WeeklyAvailability()),
                readiness = readiness(level = ReadinessLevel.GREEN),
            ),
        )

        assertThat(recommendation.type).isEqualTo(WorkoutType.EASY)
        assertThat(recommendation.durationMin).isEqualTo(30)
        assertThat(recommendation.confidence).isEqualTo(DataConfidence.LOW)
    }

    @Test
    fun `duration is clamped to a safe easy range`() {
        val short = recommender.recommend(
            input(
                profile = profile(availability = WeeklyAvailability(mapOf(DayOfWeek.MONDAY to 10))),
                readiness = readiness(level = ReadinessLevel.GREEN),
            ),
        )
        val long = recommender.recommend(
            input(
                profile = profile(availability = WeeklyAvailability(mapOf(DayOfWeek.MONDAY to 120))),
                readiness = readiness(level = ReadinessLevel.GREEN),
            ),
        )

        assertThat(short.durationMin).isEqualTo(20)
        assertThat(long.durationMin).isEqualTo(60)
    }

    @Test
    fun `self reported heat slows the easy pace by three percent`() {
        val recommendation = recommender.recommend(
            input(
                readiness = readiness(level = ReadinessLevel.GREEN, confidence = DataConfidence.HIGH),
                heatLevel = HeatLevel.HOT,
            ),
        )

        assertThat(recommendation.type).isEqualTo(WorkoutType.EASY)
        assertThat(recommendation.pace).isEqualTo(
            PaceTarget(
                fast = Pace.fromSecondsPerKm(464),
                slow = Pace.fromSecondsPerKm(519),
            ),
        )
        assertThat(recommendation.heartRate).isEqualTo(HeartRateTarget(minBpm = 146, maxBpm = 161))
        assertThat(recommendation.ruleIds).contains("R-PLAN-07")
        assertThat(recommendation.evidenceIds).contains("EV-ELY-2007")
        assertThat(recommendation.rationale).contains("Calor informado", "3% mais lento")
    }

    @Test
    fun `heat lowers confidence by one level`() {
        val recommendation = recommender.recommend(
            input(
                readiness = readiness(level = ReadinessLevel.GREEN, confidence = DataConfidence.HIGH),
                heatLevel = HeatLevel.HOT,
            ),
        )

        assertThat(recommendation.confidence).isEqualTo(DataConfidence.MEDIUM)
    }

    @Test
    fun `heat without calibration guides by effort instead of a slower pace`() {
        val recommendation = recommender.recommend(
            input(
                profile = profile(threshold = null, lthr = null),
                readiness = readiness(level = ReadinessLevel.GREEN),
                heatLevel = HeatLevel.HOT,
            ),
        )

        assertThat(recommendation.pace).isNull()
        assertThat(recommendation.rationale).contains("reduza o ritmo pelo esforço")
        assertThat(recommendation.rationale).doesNotContain("3% mais lento")
    }

    @Test
    fun `heat with treadmill access cites the alternate modality`() {
        val recommendation = recommender.recommend(
            input(
                profile = profile(treadmillAccess = true),
                readiness = readiness(level = ReadinessLevel.GREEN),
                heatLevel = HeatLevel.HOT,
            ),
        )

        assertThat(recommendation.ruleIds).contains("R-PLAN-07", "R-PLAN-08")
        assertThat(recommendation.rationale).contains("A esteira também é válida hoje")
    }

    @Test
    fun `no heat keeps the baseline pace and confidence`() {
        val recommendation = recommender.recommend(
            input(readiness = readiness(level = ReadinessLevel.GREEN, confidence = DataConfidence.HIGH)),
        )

        assertThat(recommendation.pace?.fast).isEqualTo(Pace.fromSecondsPerKm(450))
        assertThat(recommendation.ruleIds).doesNotContain("R-PLAN-07")
        assertThat(recommendation.confidence).isEqualTo(DataConfidence.HIGH)
    }

    private fun input(
        profile: AthleteProfile = profile(),
        readiness: ReadinessResult,
        load: LoadSnapshot? = knownLoad(acwr = 1.0),
        heatLevel: HeatLevel = HeatLevel.NONE,
    ) = PrescriptionInput(
        profile = profile,
        readiness = readiness,
        load = load,
        today = monday,
        heatLevel = heatLevel,
    )

    private fun readiness(
        level: ReadinessLevel,
        confidence: DataConfidence = DataConfidence.MEDIUM,
        flags: Set<ReadinessFlag> = emptySet(),
        score: Int = when (level) {
            ReadinessLevel.GREEN -> 80
            ReadinessLevel.AMBER -> 55
            ReadinessLevel.RED -> 20
        },
    ) = ReadinessResult(
        score = score,
        level = level,
        confidence = confidence,
        components = mapOf(ReadinessComponent.SUBJECTIVE to score),
        missing = emptySet(),
        flags = flags,
    )

    private fun profile(
        threshold: Pace? = Pace.fromSecondsPerKm(360),
        lthr: Int? = 180,
        availability: WeeklyAvailability = WeeklyAvailability(mapOf(DayOfWeek.MONDAY to 45)),
        treadmillAccess: Boolean = false,
    ) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = lthr,
        thresholdPace = threshold,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = availability,
        treadmillAccess = treadmillAccess,
    )

    private fun knownLoad(acwr: Double) = LoadSnapshot(
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
        missingDays = 16,
        coverage = coverage(daysWithLoad = 12),
    )

    private fun coverage(daysWithLoad: Int, sourceFresh: Boolean = true): LoadCoverage {
        val daysInWindow = 28
        val daysWithoutActivity = if (sourceFresh) daysInWindow - daysWithLoad else 0
        val daysWithoutData = if (sourceFresh) 0 else daysInWindow - daysWithLoad
        return LoadCoverage(
            daysInWindow = daysInWindow,
            daysWithLoad = daysWithLoad,
            daysWithoutActivity = daysWithoutActivity,
            daysWithoutData = daysWithoutData,
            lastActivityAt = if (daysWithLoad > 0) java.time.Instant.parse("2026-10-30T10:00:00Z") else null,
            sourceFresh = sourceFresh,
        )
    }
}
