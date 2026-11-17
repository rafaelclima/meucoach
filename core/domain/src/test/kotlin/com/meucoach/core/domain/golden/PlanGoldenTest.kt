package com.meucoach.core.domain.golden

import com.meucoach.core.domain.prescription.QualityKind
import com.meucoach.core.domain.prescription.RacePlan
import com.meucoach.core.domain.prescription.VolumeReference
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WeeklyPlanInput
import com.meucoach.core.domain.prescription.WorkoutType
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
import org.json.JSONObject
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * G-PER-03 (ADR 0044 §6, FR-401): the first quality opportunity is structural.
 *
 * Three 40-minute days, an intermediate athlete and two load weeks are enough
 * for one opportunity even without a check-in or load. The longest day is
 * honest about the 40-minute agenda limit, and the easy Friday keeps its own
 * duration.
 */
class PlanGoldenTest {
    @Test
    fun `matches the structural opportunity scenario`() {
        val input = readGolden("/golden/per-opportunity/input.json")
        val expected = readGolden("/golden/per-opportunity/expected.json")

        val plan = WeeklyPlanGenerator().generate(
            WeeklyPlanInput(
                profile = input.getJSONObject("profile").toProfile(),
                readiness = input.getJSONObject("readiness").toReadiness(),
                weekStart = LocalDate.parse(input.getString("weekStart")),
                today = LocalDate.parse(input.getString("today")),
                race = input.optJSONObject("race")?.toRace(),
                volumeReferences = input.optJSONArray("volumeReferences")?.let { references ->
                    List(references.length()) { index -> references.getJSONObject(index).toVolumeReference() }
                },
            ),
        )

        assertThat(plan.qualityCount).isEqualTo(expected.getInt("qualityCount"))
        assertThat(plan.weeklyMinutes).isEqualTo(expected.getInt("weeklyMinutes"))
        assertThat(plan.rationale).contains("Fase ${expected.getString("phase")}", "o dia confirma")

        val qualityExpected = expected.getJSONObject("quality")
        val quality = plan.sessions.single { it.type == WorkoutType.QUALITY }
        assertThat(quality.date.dayOfWeek.name).isEqualTo(qualityExpected.getString("dayOfWeek"))
        assertThat(quality.title).isEqualTo(qualityExpected.getString("title"))
        assertThat(quality.qualityKind).isEqualTo(QualityKind.valueOf(qualityExpected.getString("kind")))
        assertThat(quality.durationMin).isEqualTo(qualityExpected.getInt("durationMin"))
        assertThat(quality.planDecision?.reason?.name).isEqualTo(qualityExpected.getString("planDecision"))
        assertThat(quality.structure).isNotNull
        assertThat(quality.structure!!.totalMin).isEqualTo(quality.durationMin)

        val easyExpected = expected.getJSONObject("easy")
        val easy = plan.sessions.single { it.date.dayOfWeek.name == easyExpected.getString("dayOfWeek") }
        assertThat(easy.type).isEqualTo(WorkoutType.EASY)
        assertThat(easy.title).isEqualTo(easyExpected.getString("title"))
        assertThat(easy.durationMin).isEqualTo(easyExpected.getInt("durationMin"))

        val longExpected = expected.getJSONObject("longRun")
        val longRun = plan.sessions.single { it.type == WorkoutType.LONG }
        assertThat(longRun.date.dayOfWeek.name).isEqualTo(longExpected.getString("dayOfWeek"))
        assertThat(longRun.title).isEqualTo(longExpected.getString("title"))
        assertThat(longRun.durationMin).isEqualTo(longExpected.getInt("durationMin"))
        assertThat(longRun.planDecision?.reason?.name).isEqualTo(longExpected.getString("planDecision"))

        val forbidden = expected.getJSONArray("forbiddenTitles").toStringList()
        assertThat(plan.sessions.map { it.title }).doesNotContainAnyElementsOf(forbidden)
    }

    private fun JSONObject.toProfile(): AthleteProfile {
        val availabilityJson = getJSONObject("availability")
        val availability = availabilityJson.keys().asSequence().associate { key ->
            DayOfWeek.valueOf(key) to availabilityJson.getInt(key)
        }
        return AthleteProfile(
            id = getString("id"),
            birthDate = LocalDate.parse(getString("birthDate")),
            sex = Sex.valueOf(getString("sex")),
            restingHeartRate = getInt("restingHeartRate"),
            maxHeartRateObserved = getInt("maxHeartRateObserved"),
            lactateThresholdHeartRate = getInt("lactateThresholdHeartRate"),
            thresholdPace = Pace.fromSecondsPerKm(getInt("thresholdPaceSecPerKm")),
            experience = Experience.valueOf(getString("experience")),
            timezone = getString("timezone"),
            weeklyAvailability = WeeklyAvailability(availability),
        )
    }

    private fun JSONObject.toReadiness(): ReadinessResult {
        val componentsJson = getJSONObject("components")
        val components = componentsJson.keys().asSequence().associate { key ->
            ReadinessComponent.valueOf(key) to componentsJson.getInt(key)
        }
        return ReadinessResult(
            score = getInt("score"),
            level = ReadinessLevel.valueOf(getString("level")),
            confidence = DataConfidence.valueOf(getString("confidence")),
            components = components,
            missing = getJSONArray("missing").toStringList().map(ReadinessComponent::valueOf).toSet(),
            flags = getJSONArray("flags").toStringList().map(ReadinessFlag::valueOf).toSet(),
        )
    }

    private fun JSONObject.toVolumeReference(): VolumeReference = VolumeReference(
        weekStart = LocalDate.parse(getString("weekStart")),
        plannedMinutes = getInt("plannedMinutes"),
        executedMinutes = getInt("executedMinutes"),
        strongMinutes = getInt("strongMinutes"),
        strongSessions = getInt("strongSessions"),
        plannedSessions = getInt("plannedSessions"),
        createdOn = LocalDate.parse(getString("createdOn")),
        specialWeek = getBoolean("specialWeek"),
    )

    private fun JSONObject.toRace(): RacePlan = RacePlan(
        eventId = getString("eventId"),
        distance = RaceDistance.valueOf(getString("distance")),
        targetDate = LocalDate.parse(getString("targetDate")),
    )
}
