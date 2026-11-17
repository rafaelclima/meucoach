package com.meucoach.core.domain.golden

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
import com.meucoach.core.domain.readiness.HrvMetrics
import com.meucoach.core.domain.readiness.ReadinessCalculator
import com.meucoach.core.domain.readiness.ReadinessSignals
import com.meucoach.core.domain.readiness.RestingHeartRateMetrics
import com.meucoach.core.domain.readiness.SleepMetrics
import com.meucoach.core.domain.readiness.SubjectiveCheckIn
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * G-READY-05 (ADR 0049 §2.1, FR-406): the check-in matrix on a green
 * objective base. Without a check-in the score may stay green but quality
 * is blocked; 3/3/3/3 is amber; 4/2/2/2 and 5/1/1/1 keep the quality.
 *
 * (Numbered G-READY-05 because G-READY-03 already names the illness
 * fixture, which predates the PRD; the renumbering is recorded in C4-32
 * and goes to the ADR/PRD in C4-33.)
 */
class ReadinessCheckinGoldenTest {
    @Test
    fun `check-in matrix decides quality on a green objective base`() {
        val input = readGolden("/golden/readiness-checkin/input.json")
        val expected = readGolden("/golden/readiness-checkin/expected.json")
        assertThat(expected.getString("scenarioId")).isEqualTo(input.getString("scenarioId"))

        val today = LocalDate.parse(input.getString("today"))
        val objectives = input.getJSONObject("objectives")
        val load = passingLoad(today)
        val expectedCases = expected.getJSONArray("cases")
        assertThat(expectedCases.length()).isEqualTo(input.getJSONArray("cases").length())

        repeat(expectedCases.length()) { index ->
            val caseInput = input.getJSONArray("cases").getJSONObject(index)
            val caseExpected = expectedCases.getJSONObject(index)
            assertThat(caseExpected.getString("id")).isEqualTo(caseInput.getString("id"))

            val result = ReadinessCalculator.calculate(
                checkIn = caseInput.optJSONObject("checkIn")?.toCheckIn(),
                sleep = objectives.getJSONObject("sleep").let { SleepMetrics(it.getInt("durationMin")) },
                hrv = objectives.getJSONObject("hrv").toHrv(),
                restingHeartRate = objectives.getJSONObject("restingHeartRate").toRestingHeartRate(),
                signals = objectives.getJSONObject("signals").toSignals(),
            )

            val id = caseExpected.getString("id")
            assertThat(result.score).`as`("$id score").isEqualTo(caseExpected.getInt("score"))
            assertThat(result.level).`as`("$id level")
                .isEqualTo(ReadinessLevel.valueOf(caseExpected.getString("level")))
            assertThat(result.confidence).`as`("$id confidence")
                .isEqualTo(DataConfidence.valueOf(caseExpected.getString("confidence")))
            assertThat(result.components.mapKeys { it.key.name })
                .`as`("$id components")
                .isEqualTo(componentsOf(caseExpected))
            assertThat(result.missing.map { it.name }.toSortedSet())
                .`as`("$id missing")
                .isEqualTo(caseExpected.getJSONArray("missing").toStringList().toSortedSet())
            assertThat(result.flags.map { it.name }.toSortedSet())
                .`as`("$id flags")
                .isEqualTo(caseExpected.getJSONArray("flags").toStringList().toSortedSet())
            assertThat(result.qualityEligibleByReadiness)
                .`as`("$id qualityEligibleByReadiness")
                .isEqualTo(caseExpected.getBoolean("qualityEligibleByReadiness"))

            val gate = QualityGate.evaluate(
                plannedSession = qualitySession(today),
                readiness = result,
                load = load,
                recovery = null,
                profile = profile(),
                date = today,
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

    private fun componentsOf(caseExpected: JSONObject): Map<String, Int> {
        val components = caseExpected.getJSONObject("components")
        return components.keys().asSequence().associateWith { components.getInt(it) }
    }

    private fun JSONObject.toCheckIn() = SubjectiveCheckIn(
        sleepQuality = getInt("sleepQuality"),
        fatigue = getInt("fatigue"),
        soreness = getInt("soreness"),
        stress = getInt("stress"),
    )

    private fun JSONObject.toHrv() = HrvMetrics(
        rmssd7dMs = getDouble("rmssd7dMs"),
        baseline60dMs = getDouble("baseline60dMs"),
        meanLog7d = getDouble("meanLog7d"),
        meanLog60d = getDouble("meanLog60d"),
        sdLnMs = getDouble("sdLnMs"),
        validNights = getInt("validNights"),
    )

    private fun JSONObject.toRestingHeartRate() = RestingHeartRateMetrics(
        average7dBpm = getDouble("average7dBpm"),
        baseline30dBpm = getDouble("baseline30dBpm"),
    )

    private fun JSONObject.toSignals() = ReadinessSignals(
        illnessOrInjury = optBoolean("illnessOrInjury", false),
        restingHeartRateElevatedTwoDays = optBoolean("restingHeartRateElevatedTwoDays", false),
    )

    /**
     * A load snapshot that passes every load gate (objective, reliable,
     * ACWR 1.0, monotony 1.0, no strain spike) so only the check-in decides.
     */
    private fun passingLoad(today: LocalDate) = LoadSnapshot(
        daily = List(28) { offset -> DailyLoad(today.minusDays((27 - offset).toLong()), 50.0, LoadMetric.RTSS) },
        ctlSeries = List(28) { 50.0 },
        atlSeries = List(28) { 50.0 },
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
            lastActivityAt = Instant.parse("2026-11-13T10:00:00Z"),
            sourceFresh = true,
            daysBeforeHistory = 0,
        ),
        subjectiveDaysInWindow = 0,
    )

    private fun qualitySession(today: LocalDate) = PlannedSession(
        date = today,
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
