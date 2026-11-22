package com.meucoach.core.domain.golden

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.QualityKind
import com.meucoach.core.domain.prescription.RacePlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WeeklyPlanInput
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.readiness.ReadinessComponent
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
 * G-PER-05 (ADR 0047 §6, FR-403/FR-404): the 5K catalog week is frozen.
 *
 * An intermediate athlete with a 5K 34 days out trains BUILD: the rotation
 * deals a structured continuous quality, an EASY strides day carries its own
 * fitted structure, and the Sunday long run keeps the 45-minute floor. PEAK
 * must be race-specific (never a copy of BUILD) and a beginner with the same
 * agenda must get no structured quality at all.
 */
class PlanQualityGoldenTest {
    @Test
    fun `matches the 5K build catalog scenario`() {
        val input = readGolden("/golden/per-quality/input.json")
        val expected = readGolden("/golden/per-quality/expected.json")

        val plan = WeeklyPlanGenerator().generate(input.toWeeklyInput())

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
        assertThat(quality.structure!!.plannedSeconds)
            .isLessThanOrEqualTo(quality.durationMin.orZero() * 60L)

        val stridesExpected = expected.getJSONObject("strides")
        val strides = plan.sessions.single { it.title == stridesExpected.getString("title") }
        assertThat(strides.date.dayOfWeek.name).isEqualTo(stridesExpected.getString("dayOfWeek"))
        assertThat(strides.type).isEqualTo(WorkoutType.EASY)
        assertThat(strides.durationMin).isEqualTo(stridesExpected.getInt("durationMin"))
        assertThat(strides.structure).isNotNull
        assertThat(strides.structure!!.totalMin).isEqualTo(strides.durationMin)

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

        val sessionsExpected = expected.getJSONArray("sessions")
        assertThat(plan.sessions).hasSize(sessionsExpected.length())
        repeat(sessionsExpected.length()) { index ->
            val sessionExpected = sessionsExpected.getJSONObject(index)
            val session = plan.sessions.sortedBy { it.date }[index]
            assertThat(session.date.dayOfWeek.name)
                .describedAs("session $index day")
                .isEqualTo(sessionExpected.getString("dayOfWeek"))
            assertThat(session.type.name)
                .describedAs("session $index type")
                .isEqualTo(sessionExpected.getString("type"))
            assertThat(session.title)
                .describedAs("session $index title")
                .isEqualTo(sessionExpected.getString("title"))
            assertThat(session.durationMin)
                .describedAs("session $index duration")
                .isEqualTo(sessionExpected.getInt("durationMin"))
            assertThat(session.qualityKind?.name)
                .describedAs("session $index kind")
                .isEqualTo(if (sessionExpected.isNull("kind")) null else sessionExpected.getString("kind"))
            assertThat((session.structure != null))
                .describedAs("session $index structure")
                .isEqualTo(sessionExpected.getBoolean("hasStructure"))
            session.structure?.let { structure ->
                assertThat(structure.totalMin)
                    .describedAs("session $index total")
                    .isEqualTo(session.durationMin)
                assertThat(structure.plannedSeconds)
                    .describedAs("session $index planned seconds")
                    .isLessThanOrEqualTo(session.durationMin.orZero() * 60L)
            }
        }

        val forbidden = expected.getJSONArray("forbiddenTitles").toStringList()
        assertThat(plan.sessions.map { it.title }).doesNotContainAnyElementsOf(forbidden)
    }

    @Test
    fun `peak is race specific and differs from the frozen build`() {
        val input = readGolden("/golden/per-quality/input.json")
        val base = input.toWeeklyInput()

        val build = WeeklyPlanGenerator().generate(base)
        val peak = WeeklyPlanGenerator().generate(
            base.copy(race = RacePlan("race-1", RaceDistance.FIVE_K, base.weekStart.plusDays(14))),
        )

        val buildQuality = build.sessions.single { it.type == WorkoutType.QUALITY }
        val peakQuality = peak.sessions.single { it.type == WorkoutType.QUALITY }

        assertThat(peakQuality.title).isEqualTo("Pace de prova 5K")
        assertThat(peakQuality.qualityKind).isEqualTo(QualityKind.RACE_PACE)
        assertThat(peakQuality.qualityKind).isNotEqualTo(buildQuality.qualityKind)
        assertThat(peakQuality.title).isNotEqualTo(buildQuality.title)
        assertThat(peakQuality.structure).isNotNull
        assertThat(peakQuality.structure!!.totalMin).isEqualTo(peakQuality.durationMin)
        assertThat(peakQuality.structure!!.plannedSeconds)
            .isLessThanOrEqualTo(peakQuality.durationMin.orZero() * 60L)
    }

    @Test
    fun `beginner with the same agenda gets easy strides and no structured quality`() {
        val input = readGolden("/golden/per-quality/input.json")
        val beginner = input.toWeeklyInput().copy(
            profile = input.getJSONObject("profile").toProfile().copy(experience = Experience.BEGINNER),
        )

        val plan = WeeklyPlanGenerator().generate(beginner)

        assertThat(plan.qualityCount).isZero()
        assertThat(plan.sessions).noneMatch { it.type == WorkoutType.QUALITY }
        val strides = plan.sessions.single { it.title == "Acelerações curtas" }
        assertThat(strides.type).isEqualTo(WorkoutType.EASY)
        assertThat(strides.qualityKind).isNull()
        assertThat(strides.structure).isNotNull
        assertThat(strides.structure!!.totalMin).isEqualTo(strides.durationMin)
    }

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
        return WeeklyPlanInput(
            profile = profile,
            readiness = readiness,
            load = load,
            weekStart = LocalDate.parse(getString("weekStart")),
            today = LocalDate.parse(getString("today")),
            race = RacePlan(
                eventId = getJSONObject("race").getString("eventId"),
                distance = RaceDistance.valueOf(getJSONObject("race").getString("distance")),
                targetDate = LocalDate.parse(getJSONObject("race").getString("targetDate")),
            ),
        )
    }

    private fun JSONObject.toProfile(): AthleteProfile {
        val availability = getJSONObject("availability")
            .keys().asSequence().associate { key ->
                DayOfWeek.valueOf(key) to getJSONObject("availability").getInt(key)
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

    private fun Int?.orZero(): Int = this ?: 0
}
