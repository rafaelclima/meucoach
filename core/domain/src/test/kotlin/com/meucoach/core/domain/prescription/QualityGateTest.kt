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
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * C4-03 — pure day-level quality gate (ADR 0044 §2.4, ADR 0049 §2.5).
 *
 * The gate never looks at Monday's readiness: it evaluates the day it is
 * given, first failing gate wins, and every block carries a reason, the value
 * that failed, a rule/evidence pair and an unlock hint.
 */
class QualityGateTest {
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `green intermediate with objective load keeps the opportunity eligible`() {
        val eligibility = evaluate()

        assertThat(eligibility.eligible).isTrue()
        assertThat(eligibility.reason).isNull()
        assertThat(eligibility.detail).isNull()
        assertThat(eligibility.ruleId).isEqualTo("R-PER-03")
        assertThat(eligibility.evidenceIds).contains("EV-SEILER-2010", "EV-DANIELS-2014")
        assertThat(eligibility.unlockHint).isNull()
    }

    @Test
    fun `illness blocks before beginner, missing check-in and short sleep`() {
        val eligibility = evaluate(
            profile = profile(experience = Experience.BEGINNER),
            readiness = readiness(
                components = emptyMap(),
                flags = setOf(ReadinessFlag.ILLNESS_OR_INJURY, ReadinessFlag.SHORT_SLEEP),
            ),
            load = null,
        )

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.ILLNESS_OR_RED_READINESS)
        assertThat(eligibility.ruleId).isEqualTo("R-SAFE-01")
        assertThat(eligibility.detail).contains("doença ou lesão")
        assertThat(eligibility.unlockHint).isNotBlank()
    }

    @Test
    fun `red readiness blocks with the readiness rule`() {
        val eligibility = evaluate(
            readiness = readiness(level = ReadinessLevel.RED, score = 20),
        )

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.ILLNESS_OR_RED_READINESS)
        assertThat(eligibility.ruleId).isEqualTo("R-READY-03")
        assertThat(eligibility.detail).contains("vermelha").contains("20")
    }

    @Test
    fun `beginner guard blocks before missing check-in and short sleep`() {
        val eligibility = evaluate(
            profile = profile(experience = Experience.BEGINNER),
            readiness = readiness(
                components = emptyMap(),
                flags = setOf(ReadinessFlag.SHORT_SLEEP),
            ),
            load = null,
        )

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.BEGINNER_GUARD)
        assertThat(eligibility.ruleId).isEqualTo("R-PRESC-04")
        assertThat(eligibility.unlockHint).contains("graduação")
    }

    @Test
    fun `missing check-in blocks before short sleep`() {
        val eligibility = evaluate(
            readiness = readiness(
                components = mapOf(ReadinessComponent.SLEEP to 80),
                flags = setOf(ReadinessFlag.SHORT_SLEEP),
            ),
        )

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.SUBJECTIVE_MISSING)
        assertThat(eligibility.ruleId).isEqualTo("R-READY-06")
        assertThat(eligibility.detail).contains("check-in")
        assertThat(eligibility.unlockHint).contains("check-in")
    }

    @Test
    fun `short sleep blocks before recovery and load`() {
        val eligibility = evaluate(
            readiness = readiness(flags = setOf(ReadinessFlag.SHORT_SLEEP)),
            recovery = recovery(),
            load = null,
        )

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.SHORT_SLEEP)
        assertThat(eligibility.ruleId).isEqualTo("R-READY-04")
        assertThat(eligibility.evidenceIds).contains("EV-WALSH-2021", "EV-WATSON-2015")
    }

    @Test
    fun `post race recovery window blocks before load`() {
        val eligibility = evaluate(recovery = recovery(), load = null)

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.RECOVERY_WINDOW)
        assertThat(eligibility.ruleId).isEqualTo("R-RACE-04")
        assertThat(eligibility.detail).contains("dia 2 de 3")
    }

    @Test
    fun `race week blocks before taper and load`() {
        val eligibility = evaluate(
            phase = MesoPhase.TAPER,
            raceWeek = true,
            load = null,
        )

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.RACE_WEEK)
        assertThat(eligibility.ruleId).isEqualTo("R-PLAN-10")
        assertThat(eligibility.unlockHint).isNotBlank()
    }

    @Test
    fun `taper window blocks before load`() {
        val eligibility = evaluate(phase = MesoPhase.TAPER, load = null)

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.TAPER_WINDOW)
        assertThat(eligibility.ruleId).isEqualTo("R-PLAN-05")
    }

    @Test
    fun `taper micro dose is structural and not blocked by the taper itself`() {
        val eligibility = evaluate(
            plannedSession = session(kind = QualityKind.STRIDES_ONLY),
            phase = MesoPhase.TAPER,
        )

        assertThat(eligibility.eligible).isTrue()
    }

    @Test
    fun `recovery mesocycle blocks before load`() {
        val eligibility = evaluate(phase = MesoPhase.RECOVERY, load = null)

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.RECOVERY_WINDOW)
        assertThat(eligibility.detail).contains("recuperação")
    }

    @Test
    fun `missing load blocks as insufficient`() {
        val eligibility = evaluate(load = null)

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.LOAD_INSUFFICIENT)
        assertThat(eligibility.ruleId).isEqualTo("R-DATA-01")
        assertThat(eligibility.detail).contains("sem dados")
    }

    @Test
    fun `stale source blocks as stale`() {
        val eligibility = evaluate(load = load(sourceFresh = false))

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.LOAD_STALE)
        assertThat(eligibility.ruleId).isEqualTo("R-DATA-01")
        assertThat(eligibility.evidenceIds).contains("EV-IMPELLIZZERI-2020")
    }

    @Test
    fun `fewer than eight days with load blocks as insufficient`() {
        val eligibility = evaluate(load = load(daysWithLoad = 7))

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.LOAD_INSUFFICIENT)
        assertThat(eligibility.detail).contains("7 de 28")
    }

    @Test
    fun `subjective only load blocks as insufficient objective load`() {
        val eligibility = evaluate(
            load = load(daysWithLoad = 14, subjectiveDaysInWindow = 12),
        )

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.LOAD_INSUFFICIENT)
        assertThat(eligibility.detail).contains("objetiva")
    }

    @Test
    fun `monotony at two or above blocks`() {
        val eligibility = evaluate(load = load(monotony = 2.1))

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.MONOTONY_HIGH)
        assertThat(eligibility.ruleId).isEqualTo("R-LOAD-03")
        assertThat(eligibility.detail).contains("2,10")
        assertThat(eligibility.evidenceIds).contains("EV-FOSTER-2001")
    }

    @Test
    fun `missing monotony blocks conservatively`() {
        val eligibility = evaluate(load = load(monotony = null))

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.MONOTONY_HIGH)
        assertThat(eligibility.detail).contains("sem dados")
    }

    @Test
    fun `inactive recovery window does not block`() {
        val race = CompletedRace(
            eventId = "race-1",
            distance = RaceDistance.FIVE_K,
            raceDate = monday.minusDays(5),
        )

        val eligibility = evaluate(recovery = RecoveryWindow(race))

        assertThat(eligibility.eligible).isTrue()
    }

    @Test
    fun `known objective load without acwr stays eligible`() {
        val eligibility = evaluate(
            load = load(acwr = null, daysWithLoad = 14, subjectiveDaysInWindow = 1),
        )

        assertThat(eligibility.eligible).isTrue()
    }

    @Test
    fun `reliable strain spike blocks`() {
        val eligibility = evaluate(
            load = load(strain = 1_500.0, chronicAverage28d = 100.0),
        )

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.STRAIN_SPIKE)
        assertThat(eligibility.detail).contains("strain")
    }

    @Test
    fun `acwr above one point five blocks only when reliable`() {
        val eligibility = evaluate(load = load(acwr = 1.6))

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.ACWR_HIGH)
        assertThat(eligibility.ruleId).isEqualTo("R-LOAD-03")
        assertThat(eligibility.detail).contains("1,60").contains("1,50")
        assertThat(eligibility.evidenceIds).contains("EV-GABBETT-2016")
    }

    @Test
    fun `acwr one point four keeps the opportunity eligible`() {
        val eligibility = evaluate(load = load(acwr = 1.4))

        assertThat(eligibility.eligible).isTrue()
    }

    @Test
    fun `unreliable acwr one point six does not block`() {
        val eligibility = evaluate(
            load = load(acwr = 1.6, daysWithLoad = 12, daysWithoutData = 2),
        )

        assertThat(eligibility.eligible).isTrue()
    }

    @Test
    fun `pre-history acwr one point six does not block`() {
        val eligibility = evaluate(
            load = load(acwr = 1.6, daysWithLoad = 14, daysBeforeHistory = 13),
        )

        assertThat(eligibility.eligible).isTrue()
    }

    @Test
    fun `first failing gate wins in a deterministic order`() {
        val eligibility = evaluate(
            profile = profile(experience = Experience.BEGINNER),
            readiness = readiness(components = emptyMap()),
            load = null,
        )

        assertThat(eligibility.reason).isEqualTo(QualityBlockReason.BEGINNER_GUARD)
    }

    @Test
    fun `same inputs produce the same eligibility`() {
        val first = evaluate(load = load(acwr = 1.6))
        val second = evaluate(load = load(acwr = 1.6))

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `gate rejects a session that is not a quality opportunity`() {
        assertThatThrownBy {
            QualityGate.evaluate(
                plannedSession = session(type = WorkoutType.EASY, kind = null),
                readiness = readiness(),
                load = load(),
                recovery = null,
                profile = profile(),
                date = monday,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("QUALITY")
    }

    @Test
    fun `gate rejects a date that does not match the planned session`() {
        assertThatThrownBy {
            QualityGate.evaluate(
                plannedSession = session(),
                readiness = readiness(),
                load = load(),
                recovery = null,
                profile = profile(),
                date = monday.plusDays(1),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("date")
    }

    private fun evaluate(
        plannedSession: PlannedSession = session(),
        readiness: ReadinessResult = readiness(),
        load: LoadSnapshot? = load(),
        recovery: RecoveryWindow? = null,
        profile: AthleteProfile = profile(),
        phase: MesoPhase? = null,
        raceWeek: Boolean = false,
    ) = QualityGate.evaluate(
        plannedSession = plannedSession,
        readiness = readiness,
        load = load,
        recovery = recovery,
        profile = profile,
        date = monday,
        phase = phase,
        raceWeek = raceWeek,
    )

    private fun session(type: WorkoutType = WorkoutType.QUALITY, kind: QualityKind? = QualityKind.CONTINUOUS) =
        PlannedSession(
            date = monday,
            type = type,
            title = "Ritmo forte",
            durationMin = 40,
            rationale = "Ritmo forte com 3 min fortes",
            ruleIds = listOf("R-PER-03"),
            evidenceIds = listOf("EV-SEILER-2010"),
            qualityKind = kind,
            planDecision = PlanDecision(PlanDecisionReason.QUALITY_OPPORTUNITY),
        )

    private fun readiness(
        level: ReadinessLevel = ReadinessLevel.GREEN,
        components: Map<ReadinessComponent, Int> = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        flags: Set<ReadinessFlag> = emptySet(),
        score: Int = when (level) {
            ReadinessLevel.GREEN -> 80
            ReadinessLevel.AMBER -> 55
            ReadinessLevel.RED -> 20
        },
    ) = ReadinessResult(
        score = score,
        level = level,
        confidence = DataConfidence.HIGH,
        components = components,
        missing = ReadinessComponent.entries.toSet() - components.keys,
        flags = flags,
    )

    private fun profile(experience: Experience = Experience.INTERMEDIATE) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(360),
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
    )

    private fun recovery() = RecoveryWindow(
        CompletedRace(
            eventId = "race-1",
            distance = RaceDistance.FIVE_K,
            raceDate = monday.minusDays(2),
        ),
    )

    private fun load(
        acwr: Double? = 1.0,
        daysWithLoad: Int = 14,
        sourceFresh: Boolean = true,
        subjectiveDaysInWindow: Int = 0,
        monotony: Double? = 1.2,
        strain: Double? = 400.0,
        chronicAverage28d: Double = 0.0,
        daysWithoutData: Int = 0,
        daysBeforeHistory: Int = 0,
    ): LoadSnapshot {
        val withoutData = if (sourceFresh) daysWithoutData + daysBeforeHistory else 28 - daysWithLoad
        val withoutActivity = if (sourceFresh) 28 - daysWithLoad - withoutData else 0
        return LoadSnapshot(
            daily = emptyList(),
            ctlSeries = emptyList(),
            atlSeries = emptyList(),
            ctl = 0.0,
            atl = 0.0,
            tsb = 0.0,
            acuteAverage7d = 0.0,
            chronicAverage28d = chronicAverage28d,
            acwr = acwr,
            monotony = monotony,
            missingDays = withoutData + withoutActivity,
            coverage = LoadCoverage(
                daysInWindow = 28,
                daysWithLoad = daysWithLoad,
                daysWithoutActivity = withoutActivity,
                daysWithoutData = withoutData,
                lastActivityAt = null,
                sourceFresh = sourceFresh,
                daysBeforeHistory = daysBeforeHistory,
            ),
            subjectiveDaysInWindow = subjectiveDaysInWindow,
            strain = strain,
        )
    }
}
