package com.meucoach.core.domain.cycle

import com.meucoach.core.domain.prescription.CompletedRace
import com.meucoach.core.domain.prescription.MesoPhase
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.RacePlan
import com.meucoach.core.domain.prescription.RecoveryWindow
import com.meucoach.core.domain.prescription.VolumeReference
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.RaceDistance
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * ADR 0072: the cycle overview is a read-only projection of the macro table.
 * Future weeks never carry session-level data; the deload is confirmed by a
 * persisted plan or shown as an explicit conditional scenario.
 */
class CycleOverviewCalculatorTest {
    private val monday = LocalDate.of(2026, 11, 9)

    @Test
    fun `six week 5K shows phases, reduction and race`() {
        val overview = calculate(race(RaceDistance.FIVE_K, 5), weeks = 6)

        assertThat(overview.weeks).hasSize(6)
        assertThat(overview.weeks.map { it.phase }).containsExactly(
            MesoPhase.BASE,
            MesoPhase.BUILD,
            MesoPhase.BUILD,
            MesoPhase.PEAK,
            MesoPhase.TAPER,
            MesoPhase.TAPER,
        )
        assertThat(overview.weeks.first().isCurrent).isTrue()
        assertThat(overview.weeks[1].isNext).isTrue()
        assertThat(overview.weeks.last().state).isEqualTo(CycleWeekState.RACE)
        assertThat(overview.milestones.map { it.kind }).contains(
            CycleMilestoneKind.TAPER_START,
            CycleMilestoneKind.RACE,
        )
        assertThat(overview.race?.weeksToRace).isEqualTo(5)
    }

    @Test
    fun `future weeks never carry minutes or strong days`() {
        val overview = calculate(
            race = race(RaceDistance.FIVE_K, 5),
            currentPlan = plan(monday, minutes = 120, qualityDate = monday.plusDays(2)),
            weeks = 6,
        )

        assertThat(overview.weeks.first().plannedMinutes).isEqualTo(120)
        assertThat(overview.weeks.first().strongDays).containsExactly(monday.plusDays(2))
        overview.weeks.drop(2).forEach { week ->
            assertThat(week.plannedMinutes).isNull()
            assertThat(week.strongDays).isEmpty()
        }
    }

    @Test
    fun `twelve week 5K shows six weeks and a taper milestone beyond the list`() {
        val overview = calculate(race(RaceDistance.FIVE_K, 11), weeks = 6)

        assertThat(overview.weeks).hasSize(6)
        assertThat(overview.weeks).allSatisfy { week -> assertThat(week.phase).isNotNull() }
        val taper = overview.milestones.first { it.kind == CycleMilestoneKind.TAPER_START }
        val race = overview.milestones.first { it.kind == CycleMilestoneKind.RACE }
        assertThat(taper.weekStart).isEqualTo(monday.plusWeeks(10))
        assertThat(race.weekStart).isEqualTo(monday.plusWeeks(11))
    }

    @Test
    fun `ten K beyond the horizon starts general and names the specific preparation`() {
        val overview = calculate(race(RaceDistance.TEN_K, 10), weeks = 6)

        assertThat(overview.preparationStart).isEqualTo(monday.plusWeeks(3))
        assertThat(
            overview.milestones.first { it.kind == CycleMilestoneKind.PREPARATION_START }
                .weekStart,
        ).isEqualTo(monday.plusWeeks(3))
        assertThat(overview.weeks.take(3).map { it.phase }).containsOnlyNulls()
        assertThat(overview.weeks.drop(3).map { it.phase }).containsOnly(MesoPhase.BASE)
    }

    @Test
    fun `no race returns an honest empty overview`() {
        val overview = calculate(race = null, weeks = 6)

        assertThat(overview.race).isNull()
        assertThat(overview.weeks).isEmpty()
        assertThat(overview.milestones).isEmpty()
        assertThat(overview.confidence).isEqualTo(DataConfidence.LOW)
    }

    @Test
    fun `past race is not projected`() {
        val overview = calculate(
            race = RacePlan("race", RaceDistance.FIVE_K, monday.minusDays(1)),
            weeks = 6,
        )

        assertThat(overview.race).isNull()
        assertThat(overview.weeks).isEmpty()
    }

    @Test
    fun `partial current week keeps only the persisted minutes`() {
        val overview = calculate(
            race = race(RaceDistance.FIVE_K, 0),
            currentPlan = plan(monday, minutes = 60, createdOn = monday.plusDays(2)),
            weeks = 6,
        )

        val current = overview.weeks.first()
        assertThat(current.partialWeek).isTrue()
        assertThat(current.plannedMinutes).isEqualTo(60)
    }

    @Test
    fun `confirmed deload comes from the persisted plan`() {
        val overview = calculate(
            race = race(RaceDistance.FIVE_K, 5),
            currentPlan = plan(monday, deload = true),
            nextWeekPlan = plan(monday.plusWeeks(1), deload = true),
            weeks = 6,
        )

        assertThat(overview.weeks.first().deload).isEqualTo(CycleDeload.CONFIRMED)
        assertThat(overview.weeks[1].deload).isEqualTo(CycleDeload.CONFIRMED)
        assertThat(overview.weeks.first().volume).isEqualTo(CycleVolumeIntent.REDUCE)
        assertThat(overview.milestones.map { it.kind }).contains(CycleMilestoneKind.DELOAD)
    }

    @Test
    fun `conditional deload uses real history and says how many load weeks are needed`() {
        val history = listOf(
            reference(monday.minusWeeks(2)),
            reference(monday.minusWeeks(1)),
        )
        val overview = calculate(
            race = race(RaceDistance.FIVE_K, 11),
            currentPlan = plan(monday),
            references = history,
            weeks = 6,
        )

        val next = overview.weeks[1]
        assertThat(next.deload).isEqualTo(CycleDeload.CONDITIONAL)
        assertThat(next.deloadConditionWeeks).isEqualTo(1)
        assertThat(next.confidence).isEqualTo(DataConfidence.LOW)
        assertThat(overview.milestones.any { it.kind == CycleMilestoneKind.DELOAD && it.conditional }).isTrue()
    }

    @Test
    fun `no history never invents a deload date`() {
        val overview = calculate(
            race = race(RaceDistance.FIVE_K, 11),
            currentPlan = plan(monday),
            weeks = 6,
        )

        assertThat(overview.weeks.map { it.deload }).containsOnly(CycleDeload.NONE)
        assertThat(overview.hasLoadHistory).isFalse()
        assertThat(overview.milestones.map { it.kind }).doesNotContain(CycleMilestoneKind.DELOAD)
    }

    @Test
    fun `taper and peak never receive a deload`() {
        val overview = calculate(
            race = race(RaceDistance.FIVE_K, 1),
            currentPlan = plan(monday),
            references = listOf(
                reference(monday.minusWeeks(3)),
                reference(monday.minusWeeks(2)),
                reference(monday.minusWeeks(1)),
            ),
            weeks = 3,
        )

        assertThat(overview.weeks.map { it.phase }).contains(MesoPhase.TAPER)
        assertThat(overview.weeks.map { it.deload }).containsOnly(CycleDeload.NONE)
    }

    @Test
    fun `race changed after the plan makes the current phase unknown`() {
        val overview = calculate(
            race = race(RaceDistance.FIVE_K, 5),
            currentPlan = plan(monday, createdOn = monday.minusDays(1)),
            raceUpdatedOn = monday,
            weeks = 6,
        )

        assertThat(overview.staleRace).isTrue()
        assertThat(overview.weeks.first().phase).isNull()
        assertThat(overview.weeks.first().volume).isEqualTo(CycleVolumeIntent.UNKNOWN)
    }

    @Test
    fun `race in the middle of the week is still a race week`() {
        val race = RacePlan("race", RaceDistance.FIVE_K, monday.plusWeeks(1).plusDays(2))
        val overview = calculate(race = race, weeks = 6)

        assertThat(overview.race?.weeksToRace).isEqualTo(1)
        assertThat(overview.weeks[1].state).isEqualTo(CycleWeekState.RACE)
        assertThat(overview.weeks[1].phase).isEqualTo(MesoPhase.TAPER)
    }

    @Test
    fun `recovery without a planned race is surfaced without inventing a cycle`() {
        val recovery = RecoveryWindow(CompletedRace("race", RaceDistance.FIVE_K, monday.minusDays(1)))
        val overview = calculate(race = null, weeks = 6, recovery = recovery)

        assertThat(overview.weeks).isEmpty()
        assertThat(overview.recoveryUntil).isEqualTo(monday.plusDays(2))
    }

    @Test
    fun `beginner gets no structured strong session outside taper`() {
        val overview = calculate(
            race = race(RaceDistance.FIVE_K, 5),
            experience = Experience.BEGINNER,
            weeks = 6,
        )

        assertThat(overview.weeks.take(4).map { it.strong }).containsOnly(CycleStrongIntent.NONE)
        assertThat(overview.weeks.drop(4).map { it.strong })
            .containsOnly(CycleStrongIntent.MICRO_DOSE)
    }

    @Test
    fun `intermediate caps strong sessions by phase`() {
        val overview = calculate(
            race = race(RaceDistance.FIVE_K, 5),
            experience = Experience.INTERMEDIATE,
            weeks = 6,
        )

        assertThat(overview.weeks[0].strong).isEqualTo(CycleStrongIntent.UP_TO_ONE)
        assertThat(overview.weeks[1].strong).isEqualTo(CycleStrongIntent.UP_TO_TWO)
        assertThat(overview.weeks[4].strong).isEqualTo(CycleStrongIntent.MICRO_DOSE)
    }

    @Test
    fun `same inputs produce the same overview`() {
        val input = input(race(RaceDistance.FIVE_K, 5), weeks = 6)
        assertThat(CycleOverviewCalculator.calculate(input))
            .isEqualTo(CycleOverviewCalculator.calculate(input))
    }

    private fun calculate(
        race: RacePlan?,
        experience: Experience = Experience.INTERMEDIATE,
        currentPlan: WeeklyPlan? = null,
        nextWeekPlan: WeeklyPlan? = null,
        references: List<VolumeReference> = emptyList(),
        recovery: RecoveryWindow? = null,
        raceUpdatedOn: LocalDate? = null,
        weeks: Int = 6,
    ): CycleOverview = CycleOverviewCalculator.calculate(
        input(
            race = race,
            experience = experience,
            currentPlan = currentPlan,
            nextWeekPlan = nextWeekPlan,
            references = references,
            recovery = recovery,
            raceUpdatedOn = raceUpdatedOn,
            weeks = weeks,
        ),
    )

    private fun input(
        race: RacePlan?,
        experience: Experience = Experience.INTERMEDIATE,
        currentPlan: WeeklyPlan? = null,
        nextWeekPlan: WeeklyPlan? = null,
        references: List<VolumeReference> = emptyList(),
        recovery: RecoveryWindow? = null,
        raceUpdatedOn: LocalDate? = null,
        weeks: Int = 6,
    ) = CycleOverviewInput(
        today = monday,
        experience = experience,
        race = race,
        raceUpdatedOn = raceUpdatedOn,
        currentPlan = currentPlan,
        nextWeekPlan = nextWeekPlan,
        references = references,
        recovery = recovery,
        maxWeeks = weeks,
    )

    private fun race(distance: RaceDistance, weeks: Long): RacePlan =
        RacePlan("race", distance, monday.plusWeeks(weeks).plusDays(6))

    private fun plan(
        weekStart: LocalDate,
        minutes: Int = 120,
        qualityDate: LocalDate? = null,
        deload: Boolean = false,
        createdOn: LocalDate = weekStart,
    ): WeeklyPlan {
        val sessions = buildList {
            add(
                PlannedSession(
                    date = weekStart,
                    type = WorkoutType.EASY,
                    title = "Rodagem",
                    durationMin = minutes,
                    rationale = "Base aeróbica.",
                    ruleIds = listOf("R-PLAN-01"),
                    evidenceIds = listOf("EV-SEILER-2010"),
                ),
            )
            qualityDate?.let { date ->
                add(
                    PlannedSession(
                        date = date,
                        type = WorkoutType.QUALITY,
                        title = "Intervalado",
                        durationMin = 30,
                        rationale = "Estímulo.",
                        ruleIds = listOf("R-PER-04"),
                        evidenceIds = listOf("EV-SEILER-2010"),
                    ),
                )
            }
        }
        return WeeklyPlan(
            weekStart = weekStart,
            sessions = sessions,
            weeklyMinutes = minutes,
            longRunMinutes = null,
            qualityCount = if (qualityDate != null) 1 else 0,
            rationale = "Plano.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = buildList {
                add("R-PER-01")
                if (deload) add("R-PLAN-04")
            },
            evidenceIds = listOf("EV-ISSURIN-2010"),
            createdOn = createdOn,
        )
    }

    private fun reference(weekStart: LocalDate) = VolumeReference(
        weekStart = weekStart,
        plannedMinutes = 120,
        executedMinutes = 120,
        strongMinutes = 120,
        strongSessions = 2,
        plannedSessions = 3,
        createdOn = weekStart,
        specialWeek = false,
    )
}
