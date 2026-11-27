package com.meucoach.core.domain.golden

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.MacroPlanResolver
import com.meucoach.core.domain.prescription.MesoPhase
import com.meucoach.core.domain.prescription.QualityKind
import com.meucoach.core.domain.prescription.RacePaceResolver
import com.meucoach.core.domain.prescription.RacePlan
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.VolumeReference
import com.meucoach.core.domain.prescription.WeeklyPlan
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
import com.meucoach.core.model.VdotSource
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * G-PER-06 (FR-411): the 5K horizon and dynamic race pace are frozen.
 *
 * A 5K 26 weeks out keeps the general rotation (BUILD with a structured
 * quality), the 12-week macro table starts at −12 weeks with a short BASE and
 * one PEAK week, and the PEAK block uses the current prediction. The declared
 * goal only changes the gap copy: an ambitious goal is named, never prescribed.
 */
class PlanHorizonGoldenTest {
    private val generator = WeeklyPlanGenerator()

    @Test
    fun `a 26 week 5K keeps the general rotation with quality`() {
        val input = readGolden("/golden/per-horizon/input.json")
        val expected = readGolden("/golden/per-horizon/expected.json").getJSONObject("general")

        val plan = generator.generate(input.toWeeklyInput())

        assertThat(plan.rationale).contains("Fase ${expected.getString("phase")}")
        assertThat(plan.qualityCount).isEqualTo(expected.getInt("qualityCount"))
        assertThat(plan.weeklyMinutes).isEqualTo(expected.getInt("weeklyMinutes"))
        val contains = expected.getJSONArray("rationaleContains").toStringList()
        assertThat(plan.rationale).contains(*contains.toTypedArray())

        val sessionsExpected = expected.getJSONArray("sessions")
        assertThat(plan.sessions).hasSize(sessionsExpected.length())
        assertSessions(plan, sessionsExpected)

        val forbidden = expected.getJSONArray("forbiddenTitles").toStringList()
        assertThat(plan.sessions.map { it.title }).doesNotContainAnyElementsOf(forbidden)
    }

    @Test
    fun `the specific block starts at twelve weeks and ends with one peak`() {
        val input = readGolden("/golden/per-horizon/input.json")
        val expected = readGolden("/golden/per-horizon/expected.json").getJSONObject("specificBlock")

        val plan = MacroPlanResolver.resolve(
            race = input.toRace(),
            today = LocalDate.parse(expected.getString("today")),
        )

        assertThat(plan.isGeneral).isFalse()
        assertThat(plan.weeks.map { it.phase.name })
            .containsExactlyElementsOf(expected.getJSONArray("phases").toStringList())
        assertThat(plan.weeks.first().weekInPhase).isEqualTo(expected.getInt("firstWeekInPhase"))
        assertThat(plan.weeks.first().phaseWeeks).isEqualTo(expected.getInt("firstPhaseWeeks"))
        assertThat(plan.weeks.count { it.phase == MesoPhase.PEAK })
            .isEqualTo(expected.getInt("peakWeeks"))
        assertThat(plan.weeks.count { it.phase == MesoPhase.TAPER })
            .isEqualTo(expected.getInt("taperWeeks"))
    }

    @Test
    fun `the peak uses the prediction and names the ambitious goal`() {
        val input = readGolden("/golden/per-horizon/input.json")
        val peakExpected = readGolden("/golden/per-horizon/expected.json").getJSONObject("peak")

        val peakInput = input.toWeeklyInput(today = LocalDate.parse(peakExpected.getString("today")))
            .let { it.copy(profile = it.profile.withFreshCalibration(input)) }
        val plan = generator.generate(peakInput)
        val quality = plan.sessions.single { it.type == WorkoutType.QUALITY }

        assertThat(quality.title).isEqualTo(peakExpected.getString("title"))
        assertThat(quality.qualityKind).isEqualTo(QualityKind.valueOf(peakExpected.getString("kind")))
        assertThat(quality.durationMin).isEqualTo(peakExpected.getInt("durationMin"))
        assertThat(quality.targets.primary).isEqualTo(TargetKind.PACE)
        assertThat(quality.targets.pace).isNotNull
        assertThat(quality.targets.rpe).isNull()
        val raceBlock = quality.structure!!.blocks.single {
            it.kind ==
                com.meucoach.core.domain.prescription.BlockKind.RACE_PACE
        }
        assertThat(raceBlock.target).isEqualTo(peakExpected.getString("blockTarget"))

        val contains = peakExpected.getJSONArray("rationaleContains").toStringList()
        assertThat(quality.rationale).contains(*contains.toTypedArray())
        assertThat(quality.rationale).doesNotContain(peakExpected.getString("goalPaceLabel"))

        val prediction = com.meucoach.core.domain.race.RaceTimePredictor.predict(
            peakInput.profile,
            RaceDistance.FIVE_K,
            peakInput.today,
        ) as com.meucoach.core.domain.race.RacePredictionResult.Available
        val gap = RacePaceResolver.goalGap(peakInput.raceTargetTimeSec, prediction.prediction)!!
        assertThat(gap.percent).isEqualTo(peakExpected.getInt("goalPercent"))
        assertThat(gap.alignment.name).isEqualTo(peakExpected.getString("goalAlignment"))
    }

    @Test
    fun `a slower goal only changes the gap copy, never the peak pace`() {
        val input = readGolden("/golden/per-horizon/input.json")
        val expected = readGolden("/golden/per-horizon/expected.json")
        val peakExpected = expected.getJSONObject("peak")
        val peakInput = input.toWeeklyInput(today = LocalDate.parse(peakExpected.getString("today")))
            .let { it.copy(profile = it.profile.withFreshCalibration(input)) }

        val ambitious = generator.generate(peakInput)
        val conservative = generator.generate(
            peakInput.copy(raceTargetTimeSec = expected.getInt("conservativeGoalSec")),
        )
        val ambitiousBlock = ambitious.sessions.single { it.type == WorkoutType.QUALITY }
            .structure!!.blocks.single { it.kind == com.meucoach.core.domain.prescription.BlockKind.RACE_PACE }
        val conservativeBlock = conservative.sessions.single { it.type == WorkoutType.QUALITY }
            .structure!!.blocks.single { it.kind == com.meucoach.core.domain.prescription.BlockKind.RACE_PACE }

        assertThat(conservativeBlock.target).isEqualTo(ambitiousBlock.target)
        assertThat(conservative.sessions.single { it.type == WorkoutType.QUALITY }.rationale)
            .contains("mais lenta")
            .doesNotContain("ambiciosa")
    }

    @Test
    fun `without a race the general mode has quality but no peak or taper`() {
        val input = readGolden("/golden/per-horizon/input.json")
        val expected = readGolden("/golden/per-horizon/expected.json").getJSONObject("noRace")

        val plan = generator.generate(input.toWeeklyInput().copy(race = null, raceTargetTimeSec = null))

        assertThat(plan.qualityCount).isEqualTo(expected.getInt("qualityCount"))
        val forbidden = expected.getJSONArray("forbiddenRationale").toStringList()
        forbidden.forEach { fragment -> assertThat(plan.rationale).doesNotContain(fragment) }
        assertThat(plan.ruleIds).doesNotContain("R-PLAN-05", "R-PLAN-10")
        assertThat(plan.sessions).noneMatch { it.qualityKind == QualityKind.RACE_PACE }
    }

    @Test
    fun `forbidden vocabulary stays out of every horizon block`() {
        val input = readGolden("/golden/per-horizon/input.json")
        val expected = readGolden("/golden/per-horizon/expected.json")

        val general = generator.generate(input.toWeeklyInput())
        val peakExpected = expected.getJSONObject("peak")
        val peakInput = input.toWeeklyInput(today = LocalDate.parse(peakExpected.getString("today")))
            .let { it.copy(profile = it.profile.withFreshCalibration(input)) }
        val peak = generator.generate(peakInput)
        val conservative = generator.generate(
            peakInput.copy(raceTargetTimeSec = expected.getInt("conservativeGoalSec")),
        )
        val noRace = generator.generate(input.toWeeklyInput().copy(race = null, raceTargetTimeSec = null))

        assertGoldenVocabulary(
            expected,
            listOf("general" to general, "peak" to peak, "conservative" to conservative, "noRace" to noRace),
            "fixtures/golden/per-horizon/expected.json",
        )
    }

    private fun assertSessions(plan: WeeklyPlan, sessionsExpected: org.json.JSONArray) {
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
            assertThat(session.structure != null)
                .describedAs("session $index structure")
                .isEqualTo(sessionExpected.getBoolean("hasStructure"))
            if (sessionExpected.has("planDecision")) {
                assertThat(session.planDecision?.reason?.name)
                    .describedAs("session $index plan decision")
                    .isEqualTo(sessionExpected.getString("planDecision"))
            }
            session.structure?.let { structure ->
                assertThat(structure.totalMin)
                    .describedAs("session $index total")
                    .isEqualTo(session.durationMin)
                assertThat(structure.plannedSeconds)
                    .describedAs("session $index planned seconds")
                    .isLessThanOrEqualTo(session.durationMin.orZero() * 60L)
            }
        }
    }

    private fun JSONObject.toWeeklyInput(today: LocalDate? = null): WeeklyPlanInput {
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
            vdot = profileJson.getDouble("vdot"),
            vdotSource = VdotSource.valueOf(profileJson.getString("vdotSource")),
            vdotCalibratedAt = LocalDate.parse(profileJson.getString("vdotCalibratedAt")),
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
                .map(ReadinessFlag::valueOf).toSet(),
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
        val referencesJson = getJSONArray("volumeReferences")
        val references = List(referencesJson.length()) { index ->
            val ref = referencesJson.getJSONObject(index)
            VolumeReference(
                weekStart = LocalDate.parse(ref.getString("weekStart")),
                plannedMinutes = ref.getInt("plannedMinutes"),
                executedMinutes = ref.getInt("executedMinutes"),
                strongMinutes = ref.getInt("strongMinutes"),
                strongSessions = ref.getInt("strongSessions"),
                plannedSessions = ref.getInt("plannedSessions"),
                createdOn = LocalDate.parse(ref.getString("createdOn")),
                specialWeek = ref.getBoolean("specialWeek"),
            )
        }
        return WeeklyPlanInput(
            profile = profile,
            readiness = readiness,
            load = load,
            weekStart = today ?: LocalDate.parse(getString("weekStart")),
            today = today ?: LocalDate.parse(getString("today")),
            race = toRace(),
            raceTargetTimeSec = getInt("raceTargetTimeSec"),
            volumeReferences = references,
        )
    }

    private fun AthleteProfile.withFreshCalibration(input: JSONObject): AthleteProfile = copy(
        vdotCalibratedAt = LocalDate.parse(input.getJSONObject("peak").getString("vdotCalibratedAt")),
    )

    private fun JSONObject.toRace(): RacePlan {
        val race = getJSONObject("race")
        return RacePlan(
            eventId = race.getString("eventId"),
            distance = RaceDistance.valueOf(race.getString("distance")),
            targetDate = LocalDate.parse(race.getString("targetDate")),
        )
    }

    private fun Int?.orZero(): Int = this ?: 0
}
