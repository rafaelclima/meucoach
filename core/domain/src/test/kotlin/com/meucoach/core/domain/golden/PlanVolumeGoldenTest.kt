package com.meucoach.core.domain.golden

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.VolumeReference
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WeeklyPlanInput
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * G-VOL-02 (ADR 0050 §2.1–§2.2, FR-408): extra training counts for volume,
 * never for adherence.
 *
 * Three planned 38-minute sessions done with strong evidence (114, 3/3) plus
 * one unplanned 28-minute run give a real base of 142 and a +6% target of 151.
 * The rationale credits the extra run without inflating adherence.
 */
class PlanVolumeGoldenTest {
    @Test
    fun `extra training grows the base without inflating adherence`() {
        val input = readGolden("/golden/vol-02/input.json")
        val expected = readGolden("/golden/vol-02/expected.json")

        val plan = WeeklyPlanGenerator().generate(input.toWeeklyInput())

        assertThat(plan.weeklyMinutes).isEqualTo(expected.getInt("weeklyMinutes"))
        assertThat(plan.qualityCount).isEqualTo(expected.getInt("qualityCount"))
        assertThat(plan.ruleIds).contains("R-PLAN-01")

        val adherence = expected.getString("adherence")
        val extra = expected.getInt("extraMinutes")
        assertThat(plan.rationale).contains("base de ${expected.getInt("baseMinutes")} min")
        assertThat(plan.rationale).contains("+${expected.getInt("growthPercent")}%")
        assertThat(plan.rationale).contains("alvo de ${expected.getInt("targetMinutes")} min")
        assertThat(plan.rationale).contains("treino extra contou")
        assertThat(plan.rationale).contains("+$extra min")
        assertThat(plan.rationale).contains(adherence)
    }

    @Test
    fun `a legacy completion never inflates the real base`() {
        val monday = LocalDate.of(2026, 10, 26)
        val strong = listOf(
            done(monday, 38, CompletionEvidence.ACTIVITY),
            done(monday.plusDays(2), 38, CompletionEvidence.ACTIVITY),
            done(monday.plusDays(4), 38, CompletionEvidence.ACTIVITY),
        )
        val legacy = done(monday.plusDays(6), 33, CompletionEvidence.LEGACY_UNVERIFIED)

        assertThat(legacy.completionEvidence?.isStrong).isFalse()
        assertThat(strong.map { it.durationMin }).containsExactly(38, 38, 38)
        assertThat(strong.sumOf { it.durationMin ?: 0 }).isEqualTo(114)
    }

    private fun done(date: LocalDate, minutes: Int, evidence: CompletionEvidence) = PlannedSession(
        date = date,
        type = WorkoutType.EASY,
        title = "Fácil",
        durationMin = minutes,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = SessionStatus.DONE,
        completionEvidence = evidence,
        activityId = if (evidence.isStrong) "act-$date" else null,
    )

    private fun JSONObject.toWeeklyInput(): WeeklyPlanInput {
        val profileJson = getJSONObject("profile")
        val availability = profileJson.getJSONObject("availability")
            .keys().asSequence().associate { key ->
                DayOfWeek.valueOf(key) to profileJson.getJSONObject("availability").getInt(key)
            }
        val profile = AthleteProfile(
            id = profileJson.getString("id"),
            birthDate = LocalDate.parse(profileJson.getString("birthDate")),
            sex = Sex.valueOf(profileJson.getString("sex")),
            restingHeartRate = profileJson.getInt("restingHeartRate"),
            maxHeartRateObserved = profileJson.getInt("maxHeartRateObserved"),
            lactateThresholdHeartRate = profileJson.getInt("lactateThresholdHeartRate"),
            thresholdPace = Pace.fromSecondsPerKm(profileJson.getInt("thresholdPaceSecPerKm")),
            experience = Experience.valueOf(profileJson.getString("experience")),
            timezone = profileJson.getString("timezone"),
            weeklyAvailability = WeeklyAvailability(availability),
        )
        val readinessJson = getJSONObject("readiness")
        val components = readinessJson.getJSONObject("components")
            .keys().asSequence().associate { key ->
                ReadinessComponent.valueOf(key) to readinessJson.getJSONObject("components").getInt(key)
            }
        val readiness = ReadinessResult(
            score = readinessJson.getInt("score"),
            level = ReadinessLevel.valueOf(readinessJson.getString("level")),
            confidence = DataConfidence.valueOf(readinessJson.getString("confidence")),
            components = components,
            missing = readinessJson.getJSONArray("missing").toStringList()
                .map(ReadinessComponent::valueOf).toSet(),
            flags = readinessJson.getJSONArray("flags").toStringList()
                .map(com.meucoach.core.domain.readiness.ReadinessFlag::valueOf).toSet(),
        )
        val loadJson = getJSONObject("load")
        val load = LoadSnapshot(
            daily = emptyList(),
            ctlSeries = emptyList(),
            atlSeries = emptyList(),
            ctl = 0.0,
            atl = 0.0,
            tsb = 0.0,
            acuteAverage7d = 0.0,
            chronicAverage28d = 0.0,
            acwr = loadJson.getDouble("acwr"),
            monotony = loadJson.getDouble("monotony"),
            missingDays = 16,
            coverage = LoadCoverage(
                daysInWindow = 28,
                daysWithLoad = 12,
                daysWithoutActivity = 16,
                daysWithoutData = 0,
                lastActivityAt = java.time.Instant.parse("2026-10-30T10:00:00Z"),
                sourceFresh = true,
            ),
            subjectiveDaysInWindow = 0,
            strain = loadJson.getDouble("strain"),
        )
        val previousJson = getJSONObject("previousWeek")
        val previousSessions = List(previousJson.getJSONArray("sessions").length()) { index ->
            val sessionJson = previousJson.getJSONArray("sessions").getJSONObject(index)
            PlannedSession(
                date = LocalDate.parse(sessionJson.getString("date")),
                type = WorkoutType.valueOf(sessionJson.getString("type")),
                title = sessionJson.getString("title"),
                durationMin = sessionJson.getInt("durationMin"),
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.valueOf(sessionJson.getString("status")),
                completionEvidence = sessionJson.optString("completionEvidence", null)
                    ?.let(CompletionEvidence::valueOf),
                activityId = sessionJson.optString("activityId", null),
            )
        }
        val previousWeek = WeeklyPlan(
            weekStart = LocalDate.parse(previousJson.getString("weekStart")),
            sessions = previousSessions,
            weeklyMinutes = previousSessions.sumOf { it.durationMin ?: 0 },
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Semana anterior.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
            createdOn = LocalDate.parse(previousJson.getString("createdOn")),
        )
        val references = List(getJSONArray("volumeReferences").length()) { index ->
            val referenceJson = getJSONArray("volumeReferences").getJSONObject(index)
            VolumeReference(
                weekStart = LocalDate.parse(referenceJson.getString("weekStart")),
                plannedMinutes = referenceJson.getInt("plannedMinutes"),
                executedMinutes = referenceJson.getInt("executedMinutes"),
                strongMinutes = referenceJson.getInt("strongMinutes"),
                strongSessions = referenceJson.getInt("strongSessions"),
                plannedSessions = referenceJson.getInt("plannedSessions"),
                createdOn = LocalDate.parse(referenceJson.getString("createdOn")),
                specialWeek = referenceJson.getBoolean("specialWeek"),
            )
        }
        val race = optJSONObject("race")?.let {
            com.meucoach.core.domain.prescription.RacePlan(
                eventId = it.getString("eventId"),
                distance = com.meucoach.core.model.RaceDistance.valueOf(it.getString("distance")),
                targetDate = LocalDate.parse(it.getString("targetDate")),
            )
        }
        return WeeklyPlanInput(
            profile = profile,
            readiness = readiness,
            load = load,
            weekStart = LocalDate.parse(getString("weekStart")),
            today = LocalDate.parse(getString("today")),
            previousWeek = previousWeek,
            recentWeeks = listOf(previousWeek),
            volumeReferences = references,
            race = race,
        )
    }
}
