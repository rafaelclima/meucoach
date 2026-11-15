package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.calibration.CalibrationPerformance
import com.meucoach.core.domain.calibration.CalibrationTier
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * Fatia 4a — ADR 0042: motor de graduação de nível (TDD RED).
 *
 * Contrato: [LevelGraduationEngine.evaluate] devolve `Stay` ou `SuggestUpgrade`
 * de forma pura e determinística. Todos os critérios de `R-LVL-01/02` são
 * obrigatórios; dado ausente nunca aprova; downgrade nunca é automático.
 */
class LevelGraduationEngineTest {
    private val today = LocalDate.of(2026, 11, 15)
    private val monday = LocalDate.of(2026, 11, 9)

    @Test
    fun `3 of 6 strong weeks stays`() {
        val weeks = listOf(
            strongWeek(monday.minusWeeks(6)),
            strongWeek(monday.minusWeeks(5)),
            strongWeek(monday.minusWeeks(4)),
            weakWeek(monday.minusWeeks(3)),
            weakWeek(monday.minusWeeks(2)),
            weakWeek(monday.minusWeeks(1)),
        )
        val result = LevelGraduationEngine.evaluate(baseInput(weeks))

        assertThat(result).isInstanceOf(LevelGraduationResult.Stay::class.java)
        val stay = result as LevelGraduationResult.Stay
        assertThat(stay.missing).contains(LevelGraduationEngine.MISSING_STRONG_WEEKS)
    }

    @Test
    fun `G-LVL-01 six synthetic weeks suggest beginner to intermediate`() {
        val weeks = listOf(
            strongWeek(monday.minusWeeks(6)),
            strongWeek(monday.minusWeeks(5)),
            weakWeek(monday.minusWeeks(4)),
            strongWeek(monday.minusWeeks(3)),
            strongWeek(monday.minusWeeks(2), longRunMin = 65),
            weakWeek(monday.minusWeeks(1)),
        )
        val result = LevelGraduationEngine.evaluate(baseInput(weeks))

        assertThat(result).isInstanceOf(LevelGraduationResult.SuggestUpgrade::class.java)
        val suggest = result as LevelGraduationResult.SuggestUpgrade
        assertThat(suggest.from).isEqualTo(Experience.BEGINNER)
        assertThat(suggest.to).isEqualTo(Experience.INTERMEDIATE)
        assertThat(suggest.ruleIds).containsExactly("R-LVL-01")
        assertThat(suggest.evidenceIds).contains(
            "EV-SEILER-2010",
            "EV-DANIELS-2014",
            "EV-FRIEL-2018",
        )
        assertThat(suggest.confidence).isEqualTo(DataConfidence.MEDIUM)
        assertThat(suggest.reasons).isNotEmpty()
        assertThat(suggest.inputsHash).isNotBlank()
    }

    @Test
    fun `without calibration stays`() {
        val weeks = listOf(
            strongWeek(monday.minusWeeks(6)),
            strongWeek(monday.minusWeeks(5)),
            strongWeek(monday.minusWeeks(4)),
            strongWeek(monday.minusWeeks(3), longRunMin = 65),
        )
        val input = baseInput(weeks).copy(
            calibration = LevelCalibration(tier = null, calibratedAt = null),
        )

        val result = LevelGraduationEngine.evaluate(input)

        assertThat(result).isInstanceOf(LevelGraduationResult.Stay::class.java)
        assertThat((result as LevelGraduationResult.Stay).missing)
            .contains(LevelGraduationEngine.MISSING_CALIBRATION)
    }

    @Test
    fun `recent illness stays`() {
        val weeks = listOf(
            strongWeek(monday.minusWeeks(6)),
            strongWeek(monday.minusWeeks(5)),
            strongWeek(monday.minusWeeks(4)),
            strongWeek(monday.minusWeeks(3), longRunMin = 65),
        )
        val input = baseInput(weeks).copy(
            illnessOrInjuryDates = setOf(today.minusDays(5)),
        )

        val result = LevelGraduationEngine.evaluate(input)

        assertThat(result).isInstanceOf(LevelGraduationResult.Stay::class.java)
        assertThat((result as LevelGraduationResult.Stay).missing)
            .contains(LevelGraduationEngine.MISSING_ILLNESS)
    }

    @Test
    fun `illness closure in recent weeks stays`() {
        val weeks = listOf(
            strongWeek(monday.minusWeeks(5)),
            strongWeek(monday.minusWeeks(4)),
            strongWeek(monday.minusWeeks(3)),
            strongWeek(monday.minusWeeks(2)),
            illnessWeek(monday.minusWeeks(1)),
        )
        val result = LevelGraduationEngine.evaluate(baseInput(weeks))

        assertThat(result).isInstanceOf(LevelGraduationResult.Stay::class.java)
        assertThat((result as LevelGraduationResult.Stay).missing)
            .contains(LevelGraduationEngine.MISSING_ILLNESS)
    }

    @Test
    fun `red readiness stays`() {
        val weeks = listOf(
            strongWeek(monday.minusWeeks(4)),
            strongWeek(monday.minusWeeks(3)),
            strongWeek(monday.minusWeeks(2)),
            strongWeek(monday.minusWeeks(1), longRunMin = 65),
        )
        val input = baseInput(weeks).copy(
            readiness = readiness(ReadinessLevel.RED),
        )

        val result = LevelGraduationEngine.evaluate(input)

        assertThat(result).isInstanceOf(LevelGraduationResult.Stay::class.java)
        assertThat((result as LevelGraduationResult.Stay).missing)
            .contains(LevelGraduationEngine.MISSING_READINESS)
    }

    @Test
    fun `manual and legacy completions do not count`() {
        val weeks = listOf(
            manualWeek(monday.minusWeeks(4)),
            legacyWeek(monday.minusWeeks(3)),
            strongWeek(monday.minusWeeks(2)),
            strongWeek(monday.minusWeeks(1)),
        )
        val result = LevelGraduationEngine.evaluate(baseInput(weeks))

        assertThat(result).isInstanceOf(LevelGraduationResult.Stay::class.java)
        assertThat((result as LevelGraduationResult.Stay).missing)
            .contains(LevelGraduationEngine.MISSING_STRONG_WEEKS)
    }

    @Test
    fun `efforts without heart rate do not count`() {
        val weeks = listOf(
            strongWeek(monday.minusWeeks(4)),
            strongWeek(monday.minusWeeks(3)),
            strongWeek(monday.minusWeeks(2)),
            strongWeek(monday.minusWeeks(1), longRunMin = 65),
        )
        val input = baseInput(weeks).copy(
            efforts = listOf(
                effort(daysAgo = 20, averageHeartRate = null),
                effort(daysAgo = 15, averageHeartRate = null),
                effort(daysAgo = 10, averageHeartRate = null),
            ),
        )

        val result = LevelGraduationEngine.evaluate(input)

        assertThat(result).isInstanceOf(LevelGraduationResult.Stay::class.java)
        assertThat((result as LevelGraduationResult.Stay).missing)
            .contains(LevelGraduationEngine.MISSING_EFFORTS)
    }

    @Test
    fun `evaluation is deterministic with a stable inputs hash`() {
        val weeks = listOf(
            strongWeek(monday.minusWeeks(6)),
            strongWeek(monday.minusWeeks(5)),
            weakWeek(monday.minusWeeks(4)),
            strongWeek(monday.minusWeeks(3)),
            strongWeek(monday.minusWeeks(2), longRunMin = 65),
            weakWeek(monday.minusWeeks(1)),
        )
        val first = LevelGraduationEngine.evaluate(baseInput(weeks))
        val second = LevelGraduationEngine.evaluate(baseInput(weeks))
        val reordered = LevelGraduationEngine.evaluate(baseInput(weeks.reversed()))

        assertThat(second).isEqualTo(first)
        assertThat((reordered as LevelGraduationResult.SuggestUpgrade).inputsHash)
            .isEqualTo((first as LevelGraduationResult.SuggestUpgrade).inputsHash)
    }

    @Test
    fun `advanced profile stays without downgrade`() {
        val weeks = listOf(weakWeek(monday.minusWeeks(2)), weakWeek(monday.minusWeeks(1)))
        val input = baseInput(weeks, experience = Experience.ADVANCED)

        val result = LevelGraduationEngine.evaluate(input)

        assertThat(result).isInstanceOf(LevelGraduationResult.Stay::class.java)
        assertThat((result as LevelGraduationResult.Stay).missing).isEmpty()
    }

    @Test
    fun `intermediate meeting every criterion suggests advanced`() {
        val result = LevelGraduationEngine.evaluate(advancedInput())

        assertThat(result).isInstanceOf(LevelGraduationResult.SuggestUpgrade::class.java)
        val suggest = result as LevelGraduationResult.SuggestUpgrade
        assertThat(suggest.from).isEqualTo(Experience.INTERMEDIATE)
        assertThat(suggest.to).isEqualTo(Experience.ADVANCED)
        assertThat(suggest.ruleIds).containsExactly("R-LVL-02")
        assertThat(suggest.evidenceIds).contains(
            "EV-SEILER-2010",
            "EV-DANIELS-2014",
            "EV-FOSTER-2001",
        )
        assertThat(suggest.confidence).isEqualTo(DataConfidence.MEDIUM)
    }

    @Test
    fun `high monotony blocks advanced`() {
        val input = advancedInput().copy(
            monotonyLast4 = listOf(2.1, 2.3, 2.0, 2.2),
        )

        val result = LevelGraduationEngine.evaluate(input)

        assertThat(result).isInstanceOf(LevelGraduationResult.Stay::class.java)
        assertThat((result as LevelGraduationResult.Stay).missing)
            .contains(LevelGraduationEngine.MISSING_MONOTONY)
    }

    @Test
    fun `declining ctl blocks advanced`() {
        val input = advancedInput().copy(
            ctlLast4 = listOf(48.0, 46.0, 44.0, 41.0),
        )

        val result = LevelGraduationEngine.evaluate(input)

        assertThat(result).isInstanceOf(LevelGraduationResult.Stay::class.java)
        assertThat((result as LevelGraduationResult.Stay).missing)
            .contains(LevelGraduationEngine.MISSING_CTL)
    }

    @Test
    fun `missing race evidence blocks advanced`() {
        val input = advancedInput().copy(
            lastRaceOrTimeTrialDate = null,
            hasValidTimeTrial30Min = false,
        )

        val result = LevelGraduationEngine.evaluate(input)

        assertThat(result).isInstanceOf(LevelGraduationResult.Stay::class.java)
        assertThat((result as LevelGraduationResult.Stay).missing)
            .contains(LevelGraduationEngine.MISSING_RACE)
    }

    @Test
    fun `valid 30 minute time trial satisfies the race criterion`() {
        val input = advancedInput().copy(
            lastRaceOrTimeTrialDate = null,
            hasValidTimeTrial30Min = true,
        )

        val result = LevelGraduationEngine.evaluate(input)

        assertThat(result).isInstanceOf(LevelGraduationResult.SuggestUpgrade::class.java)
        assertThat((result as LevelGraduationResult.SuggestUpgrade).to)
            .isEqualTo(Experience.ADVANCED)
    }

    private fun baseInput(weeks: List<WeeklyPlan>, experience: Experience = Experience.BEGINNER) = LevelGraduationInput(
        profile = profile(experience),
        recentWeeks = weeks,
        efforts = listOf(
            effort(daysAgo = 30),
            effort(daysAgo = 20),
            effort(daysAgo = 10),
        ),
        calibration = LevelCalibration(
            tier = CalibrationTier.A,
            calibratedAt = today.minusDays(10),
        ),
        readiness = readiness(ReadinessLevel.GREEN),
        today = today,
    )

    private fun advancedInput() = LevelGraduationInput(
        profile = profile(Experience.INTERMEDIATE),
        recentWeeks = listOf(
            strongWeek(monday.minusWeeks(6)),
            strongWeek(monday.minusWeeks(5)),
            strongWeek(monday.minusWeeks(4)),
            strongWeek(monday.minusWeeks(3)),
            strongWeek(monday.minusWeeks(2), longRunMin = 95),
            strongWeek(monday.minusWeeks(1)),
        ),
        efforts = listOf(
            effort(daysAgo = 30),
            effort(daysAgo = 20),
            effort(daysAgo = 10),
        ),
        calibration = LevelCalibration(
            tier = CalibrationTier.A,
            calibratedAt = today.minusDays(10),
        ),
        readiness = readiness(ReadinessLevel.GREEN),
        ctlLast4 = listOf(40.0, 42.0, 43.0, 45.0),
        objectiveCtl = true,
        monotonyLast4 = listOf(1.2, 1.4, 1.1, 1.3),
        lastRaceOrTimeTrialDate = today.minusDays(30),
        today = today,
    )

    private fun profile(experience: Experience) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        experience = experience,
    )

    private fun readiness(level: ReadinessLevel) = ReadinessResult(
        score = 80,
        level = level,
        confidence = DataConfidence.HIGH,
        components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        missing = emptySet(),
        flags = emptySet(),
    )

    private fun effort(daysAgo: Long, averageHeartRate: Int? = 170) = CalibrationPerformance(
        distanceM = 5_000.0,
        durationSec = 1_500,
        date = today.minusDays(daysAgo),
        averageHeartRate = averageHeartRate,
    )

    private fun strongWeek(weekStart: LocalDate, longRunMin: Int? = null): WeeklyPlan {
        val sessions = (0 until 3).map { index ->
            doneSession(weekStart.plusDays(index.toLong()), 30, CompletionEvidence.ACTIVITY)
        } + listOf(
            if (longRunMin != null) {
                PlannedSession(
                    date = weekStart.plusDays(5),
                    type = WorkoutType.LONG,
                    title = "Longão",
                    durationMin = longRunMin,
                    rationale = "Longão.",
                    ruleIds = listOf("R-PLAN-03"),
                    evidenceIds = listOf("EV-PFITZINGER-2008"),
                    status = SessionStatus.DONE,
                    completionEvidence = CompletionEvidence.ACTIVITY,
                    activityId = "activity-$weekStart-long",
                )
            } else {
                doneSession(weekStart.plusDays(5), 30, CompletionEvidence.ACTIVITY)
            },
        )
        return WeeklyPlan(
            weekStart = weekStart,
            sessions = sessions,
            weeklyMinutes = sessions.sumOf { it.durationMin ?: 0 },
            longRunMinutes = longRunMin,
            qualityCount = 0,
            rationale = "Semana sintética.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
            createdOn = weekStart,
        )
    }

    private fun weakWeek(weekStart: LocalDate): WeeklyPlan {
        val done = (0 until 2).map { index ->
            doneSession(weekStart.plusDays(index.toLong()), 30, CompletionEvidence.ACTIVITY)
        }
        val pending = (2 until 4).map { index ->
            PlannedSession(
                date = weekStart.plusDays(index.toLong()),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
            )
        }
        val sessions = done + pending
        return WeeklyPlan(
            weekStart = weekStart,
            sessions = sessions,
            weeklyMinutes = sessions.sumOf { it.durationMin ?: 0 },
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Semana sintética.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
            createdOn = weekStart,
        )
    }

    private fun manualWeek(weekStart: LocalDate): WeeklyPlan {
        val sessions = (0 until 4).map { index ->
            doneSession(weekStart.plusDays(index.toLong()), 30, CompletionEvidence.MANUAL)
        }
        return WeeklyPlan(
            weekStart = weekStart,
            sessions = sessions,
            weeklyMinutes = 120,
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Semana sintética.",
            confidence = DataConfidence.LOW,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
            createdOn = weekStart,
        )
    }

    private fun legacyWeek(weekStart: LocalDate): WeeklyPlan {
        val sessions = (0 until 4).map { index ->
            doneSession(weekStart.plusDays(index.toLong()), 30, CompletionEvidence.LEGACY_UNVERIFIED)
        }
        return WeeklyPlan(
            weekStart = weekStart,
            sessions = sessions,
            weeklyMinutes = 120,
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Semana sintética.",
            confidence = DataConfidence.LOW,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
            createdOn = weekStart,
        )
    }

    private fun illnessWeek(weekStart: LocalDate): WeeklyPlan {
        val sessions = (0 until 4).map { index ->
            PlannedSession(
                date = weekStart.plusDays(index.toLong()),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.CLOSED,
                closureReason = SessionClosureReason.ILLNESS_OR_INJURY,
                closedAt = weekStart.plusDays(index.toLong()).atStartOfDay()
                    .atZone(java.time.ZoneId.of("UTC")).toInstant(),
            )
        }
        return WeeklyPlan(
            weekStart = weekStart,
            sessions = sessions,
            weeklyMinutes = 120,
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Semana sintética.",
            confidence = DataConfidence.LOW,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
            createdOn = weekStart,
        )
    }

    private fun doneSession(date: LocalDate, durationMin: Int, evidence: CompletionEvidence) = PlannedSession(
        date = date,
        type = WorkoutType.EASY,
        title = "Fácil",
        durationMin = durationMin,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = SessionStatus.DONE,
        completionEvidence = evidence,
        activityId = if (evidence == CompletionEvidence.ACTIVITY) {
            "activity-$date-$durationMin"
        } else {
            null
        },
    )
}
