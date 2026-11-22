package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * C4-17 — the generator consumes the quality catalog (ADR 0047).
 *
 * The weekly plan must not describe a quality session from a fixed template:
 * the phase/level/distance rotation chooses the stimulus, the session carries
 * its `qualityKind` and an executable `structure` that never exceeds the real
 * duration, and PEAK is specific to the race distance.
 */
class QualityIntegrationTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `build week carries the catalog kind title and executable structure`() {
        val plan = plan(raceDays = 34)

        val quality = plan.sessions.single { it.type == WorkoutType.QUALITY }

        assertThat(quality.title).isEqualTo("Ritmo forte")
        assertThat(quality.qualityKind).isEqualTo(QualityKind.CONTINUOUS)
        assertThat(quality.structure).isNotNull
        assertThat(quality.structure?.totalMin).isEqualTo(quality.durationMin)
        assertThat(quality.structure!!.plannedSeconds).isLessThanOrEqualTo(quality.durationMin.orZero() * 60L)
        assertThat(plan.qualityCount).isEqualTo(1)
    }

    @Test
    fun `rotation advances to a different stimulus in the next build week`() {
        val first = plan(raceDays = 34).sessions.single { it.type == WorkoutType.QUALITY }
        val second = plan(raceDays = 27).sessions.single { it.type == WorkoutType.QUALITY }

        assertThat(first.qualityKind).isEqualTo(QualityKind.CONTINUOUS)
        assertThat(second.qualityKind).isEqualTo(QualityKind.INTERVALS)
        assertThat(second.title).isEqualTo("Intervalado")
        assertThat(second.qualityKind).isNotEqualTo(first.qualityKind)
    }

    @Test
    fun `peak is specific to the race distance and differs from build`() {
        val build = plan(raceDays = 34).sessions.single { it.type == WorkoutType.QUALITY }
        val peak = plan(raceDays = 14).sessions.single { it.type == WorkoutType.QUALITY }

        assertThat(peak.title).isEqualTo("Pace de prova 5K")
        assertThat(peak.qualityKind).isEqualTo(QualityKind.RACE_PACE)
        assertThat(peak.qualityKind).isNotEqualTo(build.qualityKind)
        assertThat(peak.title).isNotEqualTo(build.title)
    }

    @Test
    fun `base keeps the first half without quality and exposes the light fartlek later`() {
        assertThat(plan().qualityCount).isZero()

        val oneLoadWeek = listOf(volumeWeek(monday.minusWeeks(1)))
        assertThat(plan(recentWeeks = oneLoadWeek, previousWeek = oneLoadWeek.last()).qualityCount)
            .isZero()

        val twoLoadWeeks = listOf(
            volumeWeek(monday.minusWeeks(2)),
            volumeWeek(monday.minusWeeks(1)),
        )
        val lightBase = plan(recentWeeks = twoLoadWeeks, previousWeek = twoLoadWeeks.last())

        val quality = lightBase.sessions.single { it.type == WorkoutType.QUALITY }
        assertThat(quality.title).isEqualTo("Fartlek leve")
        assertThat(quality.qualityKind).isEqualTo(QualityKind.FARTLEK)
        assertThat(lightBase.qualityCount).isEqualTo(1)
    }

    @Test
    fun `taper carries the strides micro-dose with kind and structure`() {
        val plan = plan(raceDays = 12, distance = RaceDistance.TEN_K)

        val quality = plan.sessions.single { it.type == WorkoutType.QUALITY }

        assertThat(quality.title).isEqualTo("Acelerações curtas")
        assertThat(quality.qualityKind).isEqualTo(QualityKind.STRIDES_ONLY)
        assertThat(quality.durationMin).isEqualTo(25)
        assertThat(quality.structure).isNotNull
        assertThat(quality.structure!!.plannedSeconds).isLessThanOrEqualTo(25L * 60L)
    }

    @Test
    fun `every planned quality fits its duration and carries a kind`() {
        val cases = buildList {
            Experience.entries.filter { it != Experience.BEGINNER }.forEach { level ->
                listOf(
                    RaceDistance.FIVE_K to 34,
                    RaceDistance.FIVE_K to 14,
                    RaceDistance.TEN_K to 12,
                    RaceDistance.MARATHON to 12,
                ).forEach { (distance, days) ->
                    add(Triple(level, distance, days))
                }
            }
        }

        cases.forEach { (level, distance, days) ->
            val described = "$level/$distance/$days"
            val plan = plan(raceDays = days, distance = distance, level = level)
            plan.sessions.filter { it.type == WorkoutType.QUALITY }.forEach { quality ->
                val duration = quality.durationMin
                assertThat(duration).describedAs(described).isNotNull
                assertThat(quality.qualityKind).describedAs(described).isNotNull
                val structure = quality.structure
                assertThat(structure).describedAs(described).isNotNull
                assertThat(structure!!.totalMin).describedAs(described).isEqualTo(duration)
                assertThat(structure.plannedSeconds)
                    .describedAs(described)
                    .isLessThanOrEqualTo(duration.orZero() * 60L)
            }
        }
    }

    @Test
    fun `beginner keeps the easy strides and never a structured quality`() {
        val plan = plan(raceDays = 34, level = Experience.BEGINNER)

        assertThat(plan.qualityCount).isZero()
        assertThat(plan.sessions).noneMatch { it.type == WorkoutType.QUALITY }
        val strides = plan.sessions.single { it.title == "Acelerações curtas" }
        assertThat(strides.type).isEqualTo(WorkoutType.EASY)
    }

    private fun plan(
        raceDays: Int? = null,
        distance: RaceDistance = RaceDistance.FIVE_K,
        level: Experience = Experience.INTERMEDIATE,
        recentWeeks: List<WeeklyPlan> = emptyList(),
        previousWeek: WeeklyPlan? = null,
    ): WeeklyPlan = generator.generate(
        WeeklyPlanInput(
            profile = profile(level),
            readiness = readiness(),
            load = load(),
            weekStart = monday,
            today = monday,
            previousWeek = previousWeek,
            recentWeeks = recentWeeks,
            race = raceDays?.let { RacePlan("race-1", distance, monday.plusDays(it.toLong())) },
        ),
    )

    private fun profile(level: Experience) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(360),
        experience = level,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(
            mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.SATURDAY to 90,
            ),
        ),
    )

    private fun readiness() = ReadinessResult(
        score = 80,
        level = ReadinessLevel.GREEN,
        confidence = DataConfidence.HIGH,
        components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        missing = emptySet(),
        flags = emptySet(),
    )

    private fun load() = LoadSnapshot(
        daily = emptyList(),
        ctlSeries = emptyList(),
        atlSeries = emptyList(),
        ctl = 0.0,
        atl = 0.0,
        tsb = 0.0,
        acuteAverage7d = 0.0,
        chronicAverage28d = 0.0,
        acwr = 1.0,
        monotony = 1.2,
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
        strain = 400.0,
    )

    private fun volumeWeek(weekStart: LocalDate): WeeklyPlan {
        val sessions = (0 until 4).map { index ->
            PlannedSession(
                date = weekStart.plusDays(index.toLong()),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 40,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.DONE,
                completionEvidence = CompletionEvidence.ACTIVITY,
                activityId = "activity-$weekStart-$index",
            )
        }
        return WeeklyPlan(
            weekStart = weekStart,
            sessions = sessions,
            weeklyMinutes = 160,
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Semana de carga.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
            createdOn = weekStart,
        )
    }

    private fun Int?.orZero(): Int = this ?: 0
}
