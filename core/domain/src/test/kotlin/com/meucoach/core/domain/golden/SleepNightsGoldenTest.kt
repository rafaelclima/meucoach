package com.meucoach.core.domain.golden

import com.meucoach.core.domain.health.HealthMetricsAggregator
import com.meucoach.core.domain.health.HealthRawData
import com.meucoach.core.domain.health.HealthSleepSession
import com.meucoach.core.domain.health.SleepNightValidator
import com.meucoach.core.domain.load.DailyLoad
import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadMetric
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.PlanDecision
import com.meucoach.core.domain.prescription.PlanDecisionReason
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.QualityGate
import com.meucoach.core.domain.prescription.QualityKind
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.readiness.ReadinessCalculator
import com.meucoach.core.domain.readiness.ReadinessSignals
import com.meucoach.core.domain.readiness.SleepMetrics
import com.meucoach.core.domain.readiness.SubjectiveCheckIn
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * G-DATA-02 (ADR 0049 §2.4, FR-407): an implausible night is absent, never a
 * short night. A 36-min partial session is suspect; a nap does not inflate the
 * night; a valid 5h30 night and a fragmented night keep only the largest block
 * and block quality via SHORT_SLEEP.
 */
class SleepNightsGoldenTest {
    @Test
    fun `sleep plausibility decides the night without inventing short sleep`() {
        val input = readGolden("/golden/sleep-nights/input.json")
        val expected = readGolden("/golden/sleep-nights/expected.json")
        assertThat(expected.getString("scenarioId")).isEqualTo(input.getString("scenarioId"))

        val zone = ZoneId.of(input.getString("zoneId"))
        val checkIn = input.getJSONObject("checkIn").toCheckIn()
        val sessions = input.getJSONArray("cases").flatMap { it.toSessions() }
        val aggregation = HealthMetricsAggregator.aggregateWithDiagnostics(
            HealthRawData(sleepSessions = sessions),
            zone,
            LocalDate.parse(input.getString("from")),
            LocalDate.parse(input.getString("to")),
        )
        val diagnosticsExpected = expected.getJSONObject("diagnostics")
        assertThat(aggregation.suspectSleepNights)
            .isEqualTo(diagnosticsExpected.getInt("suspectSleepNights"))
        assertThat(aggregation.fragmentedSleepNights)
            .isEqualTo(diagnosticsExpected.getInt("fragmentedSleepNights"))

        val load = passingLoad()
        val expectedCases = expected.getJSONArray("cases")
        assertThat(expectedCases.length()).isEqualTo(input.getJSONArray("cases").length())

        repeat(expectedCases.length()) { index ->
            val caseInput = input.getJSONArray("cases").getJSONObject(index)
            val caseExpected = expectedCases.getJSONObject(index)
            assertThat(caseExpected.getString("id")).isEqualTo(caseInput.getString("id"))
            val id = caseExpected.getString("id")
            val date = LocalDate.parse(caseInput.getString("date"))

            val night = SleepNightValidator.validate(date, caseInput.toSessions(), zone)
            val validatorExpected = caseExpected.getJSONObject("validator")
            val minutesExpected =
                if (validatorExpected.isNull("minutes")) null else validatorExpected.getInt("minutes")
            assertThat(night.minutes).`as`("$id minutes").isEqualTo(minutesExpected)
            assertThat(night.fragmented).`as`("$id fragmented")
                .isEqualTo(validatorExpected.getBoolean("fragmented"))
            assertThat(night.suspect).`as`("$id suspect")
                .isEqualTo(validatorExpected.getBoolean("suspect"))

            val persisted = aggregation.days.first { it.date == date }.sleepDurationMin
            assertThat(persisted).`as`("$id persisted").isEqualTo(night.minutes)

            val result = ReadinessCalculator.calculate(
                checkIn = checkIn,
                sleep = night.minutes?.let(::SleepMetrics),
                hrv = null,
                restingHeartRate = null,
            )
            val readinessExpected = caseExpected.getJSONObject("readiness")
            assertThat(result.score).`as`("$id score").isEqualTo(readinessExpected.getInt("score"))
            assertThat(result.level).`as`("$id level")
                .isEqualTo(ReadinessLevel.valueOf(readinessExpected.getString("level")))
            assertThat(result.confidence).`as`("$id confidence")
                .isEqualTo(DataConfidence.valueOf(readinessExpected.getString("confidence")))
            assertThat(result.components.mapKeys { it.key.name })
                .`as`("$id components")
                .isEqualTo(componentsOf(readinessExpected))
            assertThat(result.missing.map { it.name }.toSortedSet())
                .`as`("$id missing")
                .isEqualTo(readinessExpected.getJSONArray("missing").toStringList().toSortedSet())
            assertThat(result.flags.map { it.name }.toSortedSet())
                .`as`("$id flags")
                .isEqualTo(readinessExpected.getJSONArray("flags").toStringList().toSortedSet())
            assertThat(result.qualityEligibleByReadiness)
                .`as`("$id qualityEligibleByReadiness")
                .isEqualTo(readinessExpected.getBoolean("qualityEligibleByReadiness"))

            val gate = QualityGate.evaluate(
                plannedSession = qualitySession(date),
                readiness = result,
                load = load,
                recovery = null,
                profile = profile(),
                date = date,
                phase = null,
                raceWeek = false,
            )
            val gateExpected = caseExpected.getJSONObject("gate")
            assertThat(gate.eligible).`as`("$id gate eligible").isEqualTo(gateExpected.getBoolean("eligible"))
            assertThat(gate.reason?.name).`as`("$id gate reason")
                .isEqualTo(gateExpected.optString("reason", null))
            assertThat(gate.ruleId).`as`("$id gate rule").isEqualTo(gateExpected.getString("ruleId"))
            assertThat(gate.detail).`as`("$id gate detail").isEqualTo(gateExpected.optString("detail", null))
            assertThat(gate.unlockHint).`as`("$id gate hint")
                .isEqualTo(gateExpected.optString("unlockHint", null))
        }
    }

    private fun componentsOf(readinessExpected: JSONObject): Map<String, Int> {
        val components = readinessExpected.getJSONObject("components")
        return components.keys().asSequence().associateWith { components.getInt(it) }
    }

    private fun JSONObject.toSessions(): List<HealthSleepSession> {
        val sessions = getJSONArray("sessions")
        return List(sessions.length()) { index ->
            val session = sessions.getJSONObject(index)
            HealthSleepSession(
                start = Instant.parse(session.getString("start")),
                end = Instant.parse(session.getString("end")),
            )
        }
    }

    private fun JSONArray.flatMap(transform: (JSONObject) -> List<HealthSleepSession>): List<HealthSleepSession> {
        val result = mutableListOf<HealthSleepSession>()
        repeat(length()) { result += transform(getJSONObject(it)) }
        return result
    }

    private fun JSONObject.toCheckIn() = SubjectiveCheckIn(
        sleepQuality = getInt("sleepQuality"),
        fatigue = getInt("fatigue"),
        soreness = getInt("soreness"),
        stress = getInt("stress"),
    )

    /**
     * A load snapshot that passes every load gate (objective, reliable,
     * ACWR 1.0, monotony 1.0, no strain spike) so only sleep decides.
     */
    private fun passingLoad() = LoadSnapshot(
        daily = listOf(DailyLoad(LocalDate.of(2026, 11, 16), 50.0, LoadMetric.RTSS)),
        ctlSeries = listOf(50.0),
        atlSeries = listOf(50.0),
        ctl = 50.0,
        atl = 50.0,
        tsb = 0.0,
        acuteAverage7d = 50.0,
        chronicAverage28d = 50.0,
        acwr = 1.0,
        monotony = 1.0,
        missingDays = 0,
        coverage = LoadCoverage(
            daysInWindow = 28,
            daysWithLoad = 10,
            daysWithoutActivity = 18,
            daysWithoutData = 0,
            lastActivityAt = Instant.parse("2026-11-15T10:00:00Z"),
            sourceFresh = true,
            daysBeforeHistory = 0,
        ),
        subjectiveDaysInWindow = 0,
    )

    private fun qualitySession(date: LocalDate) = PlannedSession(
        date = date,
        type = WorkoutType.QUALITY,
        title = "Ritmo forte",
        durationMin = 40,
        rationale = "Qualidade planejada; o dia confirma.",
        ruleIds = listOf("R-PER-03"),
        evidenceIds = listOf("EV-SEILER-2010"),
        qualityKind = QualityKind.CONTINUOUS,
        planDecision = PlanDecision(PlanDecisionReason.QUALITY_OPPORTUNITY),
    )

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        thresholdPace = Pace.fromSecondsPerKm(360),
        experience = Experience.INTERMEDIATE,
        timezone = "UTC",
    )
}
