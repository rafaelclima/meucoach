package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
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
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * C4-07 — invariants of the structural quality opportunity (ADR 0044 §2.7).
 *
 * The property matrix is deterministic: the same inputs must produce the same
 * week. It covers the hard invariants the plan cannot violate no matter the
 * level, availability or load: one or two opportunities, never adjacent to the
 * long run, only the micro-dose in a taper/race week, none in recovery or for a
 * beginner, and every session carries a kind/structure that fits its duration.
 */
class QualityPlanPropertiesTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `quality invariants hold across levels availability and race phases`() {
        val availabilityCases = listOf(
            mapOf(DayOfWeek.TUESDAY to 40, DayOfWeek.FRIDAY to 40, DayOfWeek.SUNDAY to 40),
            mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.FRIDAY to 40,
                DayOfWeek.SUNDAY to 90,
            ),
            mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.TUESDAY to 40,
                DayOfWeek.THURSDAY to 40,
                DayOfWeek.FRIDAY to 40,
                DayOfWeek.SUNDAY to 90,
            ),
        )
        val raceDaysCases = listOf(40, 14, 12, 5)

        listOf(Experience.INTERMEDIATE, Experience.ADVANCED).forEach { level ->
            availabilityCases.forEach { availability ->
                raceDaysCases.forEach { raceDays ->
                    val plan = plan(level = level, raceDays = raceDays, availability = availability)
                    val described = "$level/$raceDays/$availability"

                    assertCommonInvariants(plan, described)

                    if (raceDays in listOf(12, 5)) {
                        assertThat(plan.sessions.filter { it.type == WorkoutType.QUALITY })
                            .describedAs("$described taper/race week")
                            .allMatch { it.qualityKind == QualityKind.STRIDES_ONLY }
                        assertThat(plan.sessions.filter { it.type == WorkoutType.QUALITY })
                            .allMatch { (it.durationMin ?: 0) <= 25 }
                    }

                    if (raceDays in listOf(40, 14)) {
                        assertThat(plan.qualityCount)
                            .describedAs("$described first opportunity")
                            .isBetween(1, 2)
                    }
                }
            }
        }
    }

    @Test
    fun `the plan is deterministic for equal inputs`() {
        listOf(40, 14, 12, 5).forEach { raceDays ->
            val first = plan(raceDays = raceDays)
            val second = plan(raceDays = raceDays)
            assertThat(second.sessions.map(::fingerprint))
                .describedAs("raceDays=$raceDays")
                .isEqualTo(first.sessions.map(::fingerprint))
            assertThat(second.rationale).isEqualTo(first.rationale)
        }
    }

    @Test
    fun `beginner and full recovery weeks never carry a structured opportunity`() {
        val beginner = plan(level = Experience.BEGINNER, raceDays = 40)
        val recovery = plan(
            raceDays = null,
            recovery = RecoveryWindow(CompletedRace("race-1", RaceDistance.MARATHON, monday.minusDays(2))),
        )

        assertThat(beginner.qualityCount).isZero()
        assertThat(beginner.sessions).noneMatch { it.type == WorkoutType.QUALITY }
        assertThat(recovery.qualityCount).isZero()
        assertThat(recovery.sessions).noneMatch { it.type == WorkoutType.QUALITY }
    }

    @Test
    fun `a three day week never carries two opportunities`() {
        val plan = plan(
            raceDays = 40,
            availability = mapOf(DayOfWeek.TUESDAY to 40, DayOfWeek.THURSDAY to 40, DayOfWeek.SUNDAY to 40),
            load = load(acwr = 1.0),
            previousWeek = null,
        )

        assertThat(plan.qualityCount).isEqualTo(1)
    }

    private fun assertCommonInvariants(plan: WeeklyPlan, described: String) {
        val qualities = plan.sessions.filter { it.type == WorkoutType.QUALITY }
        assertThat(plan.qualityCount).describedAs(described).isEqualTo(qualities.size)
        assertThat(plan.qualityCount).describedAs(described).isBetween(0, 2)

        val longRun = plan.sessions.singleOrNull { it.type == WorkoutType.LONG }
        qualities.forEach { quality ->
            assertThat(quality.qualityKind).describedAs("$described kind").isNotNull
            val structure = quality.structure
            assertThat(structure).describedAs("$described structure").isNotNull
            assertThat(structure!!.totalMin).describedAs("$described total").isEqualTo(quality.durationMin)
            assertThat(structure.plannedSeconds)
                .describedAs("$described planned seconds")
                .isLessThanOrEqualTo((quality.durationMin ?: 0) * 60L)
            if (longRun != null) {
                assertThat(adjacent(quality.date.dayOfWeek, longRun.date.dayOfWeek))
                    .describedAs("$described quality adjacent to long run")
                    .isFalse()
            }
        }

        val qualityDates = qualities.map { it.date.dayOfWeek }
        qualityDates.zipWithNext().forEach { (first, second) ->
            assertThat(adjacent(first, second)).describedAs("$described consecutive qualities").isFalse()
        }
    }

    private fun fingerprint(session: PlannedSession): String =
        "${session.date}/${session.type}/${session.qualityKind}/${session.title}/${session.durationMin}"

    private fun adjacent(first: DayOfWeek, second: DayOfWeek): Boolean {
        val diff = kotlin.math.abs(first.value - second.value)
        return diff == 1 || diff == 6
    }

    private fun plan(
        level: Experience = Experience.INTERMEDIATE,
        raceDays: Int?,
        availability: Map<DayOfWeek, Int> = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SUNDAY to 90,
        ),
        load: LoadSnapshot? = load(acwr = 1.0),
        previousWeek: WeeklyPlan? = null,
        recovery: RecoveryWindow? = null,
    ): WeeklyPlan = generator.generate(
        WeeklyPlanInput(
            profile = AthleteProfile(
                id = "athlete-1",
                birthDate = LocalDate.of(1988, 1, 1),
                sex = Sex.MALE,
                restingHeartRate = 58,
                maxHeartRateObserved = 187,
                lactateThresholdHeartRate = 180,
                thresholdPace = Pace.fromSecondsPerKm(360),
                experience = level,
                timezone = "America/Sao_Paulo",
                weeklyAvailability = WeeklyAvailability(availability),
            ),
            readiness = ReadinessResult(
                score = 80,
                level = ReadinessLevel.GREEN,
                confidence = DataConfidence.HIGH,
                components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
                missing = emptySet(),
                flags = emptySet(),
            ),
            load = load,
            weekStart = monday,
            today = monday,
            previousWeek = previousWeek,
            recovery = recovery,
            race = raceDays?.let { RacePlan("race-1", RaceDistance.FIVE_K, monday.plusDays(it.toLong())) },
        ),
    )

    private fun load(acwr: Double) = LoadSnapshot(
        daily = emptyList(),
        ctlSeries = emptyList(),
        atlSeries = emptyList(),
        ctl = 0.0,
        atl = 0.0,
        tsb = 0.0,
        acuteAverage7d = 0.0,
        chronicAverage28d = 0.0,
        acwr = acwr,
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
}
