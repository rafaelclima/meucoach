package com.meucoach.core.domain.prescription

import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class VolumeProgressionTest {
    private val progression = VolumeProgression()
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `without history the availability ceiling is the target`() {
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 210,
            references = emptyList(),
        )

        assertThat(decision.minutes).isEqualTo(210)
        assertThat(decision.hasHistory).isFalse()
        assertThat(decision.block).isEqualTo(VolumeGrowthBlock.NO_HISTORY)
    }

    @Test
    fun `grows six percent over a fully completed previous week`() {
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 210,
            references = listOf(week(monday.minusWeeks(1), completed = listOf(30, 30, 30, 30))),
        )

        assertThat(decision.baseMinutes).isEqualTo(120)
        assertThat(decision.baseWeekStart).isEqualTo(monday.minusWeeks(1))
        assertThat(decision.minutes).isEqualTo(127)
        assertThat(decision.growthPercent).isEqualTo(6)
        assertThat(decision.block).isEqualTo(VolumeGrowthBlock.NONE)
        assertThat(decision.cappedByAvailability).isFalse()
    }

    @Test
    fun `maintains the base when the previous week is below eighty percent`() {
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 210,
            references = listOf(
                week(monday.minusWeeks(1), completed = listOf(30, 30), planned = listOf(30, 30, 30, 30)),
            ),
        )

        assertThat(decision.baseMinutes).isEqualTo(60)
        assertThat(decision.minutes).isEqualTo(60)
        assertThat(decision.growthPercent).isZero()
        assertThat(decision.block).isEqualTo(VolumeGrowthBlock.PREVIOUS_WEEK_BELOW_80)
    }

    @Test
    fun `an older completed week is the base but never grows`() {
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 300,
            references = listOf(
                week(monday.minusWeeks(1), completed = listOf(30, 30), planned = listOf(30, 30, 30, 30)),
                week(monday.minusWeeks(2), completed = listOf(40, 40, 40, 40, 40)),
            ),
        )

        assertThat(decision.baseMinutes).isEqualTo(200)
        assertThat(decision.baseWeekStart).isEqualTo(monday.minusWeeks(2))
        assertThat(decision.minutes).isEqualTo(200)
        assertThat(decision.growthPercent).isZero()
        assertThat(decision.block).isEqualTo(VolumeGrowthBlock.PREVIOUS_WEEK_BELOW_80)
    }

    @Test
    fun `a special previous week does not grow the next one`() {
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 300,
            references = listOf(
                week(
                    monday.minusWeeks(1),
                    completed = listOf(30, 30, 30, 30),
                    ruleIds = listOf("R-PLAN-04"),
                ),
                week(monday.minusWeeks(2), completed = listOf(50, 50, 50, 50)),
            ),
        )

        assertThat(decision.baseMinutes).isEqualTo(200)
        assertThat(decision.minutes).isEqualTo(200)
        assertThat(decision.block).isEqualTo(VolumeGrowthBlock.PREVIOUS_WEEK_SPECIAL)
    }

    @Test
    fun `special target weeks never grow`() {
        val references = listOf(week(monday.minusWeeks(1), completed = listOf(30, 30, 30, 30)))

        VolumeGrowthBlock.entries
            .filter {
                it in setOf(
                    VolumeGrowthBlock.DELOAD,
                    VolumeGrowthBlock.TAPER,
                    VolumeGrowthBlock.RACE_WEEK,
                    VolumeGrowthBlock.RECOVERY,
                )
            }
            .forEach { block ->
                val decision = progression.decide(
                    targetWeekStart = monday,
                    availabilityCeilingMinutes = 210,
                    references = references,
                    gates = VolumeGates(targetWeekBlock = block),
                )

                assertThat(decision.minutes).isEqualTo(120)
                assertThat(decision.growthPercent).isZero()
                assertThat(decision.block).isEqualTo(block)
            }
    }

    @Test
    fun `readiness load and acwr each block the increase`() {
        val references = listOf(week(monday.minusWeeks(1), completed = listOf(30, 30, 30, 30)))

        val notGreen = progression.decide(
            monday,
            210,
            references,
            VolumeGates(readinessGreen = false),
        )
        val loadUnknown = progression.decide(
            monday,
            210,
            references,
            VolumeGates(loadKnown = false),
        )
        val acwrOut = progression.decide(
            monday,
            210,
            references,
            VolumeGates(acwrInRange = false),
        )

        assertThat(notGreen.block).isEqualTo(VolumeGrowthBlock.READINESS)
        assertThat(loadUnknown.block).isEqualTo(VolumeGrowthBlock.LOAD_UNKNOWN)
        assertThat(acwrOut.block).isEqualTo(VolumeGrowthBlock.ACWR_OUT_OF_RANGE)
        assertThat(listOf(notGreen, loadUnknown, acwrOut).map { it.minutes }).containsOnly(120)
    }

    @Test
    fun `availability caps an increase and a base above the ceiling`() {
        val references = listOf(week(monday.minusWeeks(1), completed = listOf(30, 30, 30, 30)))

        val cappedGrowth = progression.decide(monday, 100, references)
        val cappedBase = progression.decide(
            monday,
            100,
            references,
            VolumeGates(readinessGreen = false),
        )

        assertThat(cappedGrowth.minutes).isEqualTo(100)
        assertThat(cappedGrowth.cappedByAvailability).isTrue()
        assertThat(cappedGrowth.growthPercent).isZero()
        assertThat(cappedBase.minutes).isEqualTo(100)
        assertThat(cappedBase.cappedByAvailability).isTrue()
    }

    @Test
    fun `a plan created midweek is not a volume reference`() {
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 210,
            references = listOf(
                week(
                    monday.minusWeeks(1),
                    completed = listOf(30, 30, 30, 30),
                    createdOn = monday.minusWeeks(1).plusDays(3),
                ),
            ),
        )

        assertThat(decision.hasHistory).isFalse()
        assertThat(decision.minutes).isEqualTo(210)
    }

    @Test
    fun `only the last four weeks are considered`() {
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 210,
            references = listOf(week(monday.minusWeeks(5), completed = listOf(60, 60, 60, 60))),
        )

        assertThat(decision.hasHistory).isFalse()
    }

    @Test
    fun `a tie goes to the most recent week`() {
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 300,
            references = listOf(
                week(monday.minusWeeks(1), completed = listOf(30, 30, 30, 30)),
                week(monday.minusWeeks(2), completed = listOf(30, 30, 30, 30)),
            ),
        )

        assertThat(decision.baseWeekStart).isEqualTo(monday.minusWeeks(1))
    }

    @Test
    fun `a history without the immediately previous week never grows`() {
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 210,
            references = listOf(week(monday.minusWeeks(2), completed = listOf(30, 30, 30, 30))),
        )

        assertThat(decision.minutes).isEqualTo(120)
        assertThat(decision.block).isEqualTo(VolumeGrowthBlock.NO_PREVIOUS_WEEK)
    }

    @Test
    fun `only strong evidence counts as completed volume`() {
        val reference = week(
            monday.minusWeeks(1),
            completed = listOf(40),
            planned = listOf(40, 40),
        ).let { plan ->
            val manual = plan.sessions[1].copy(
                status = SessionStatus.DONE,
                completionEvidence = CompletionEvidence.MANUAL,
            )
            plan.copy(sessions = listOf(plan.sessions[0], manual))
        }

        assertThat(reference.completedVolumeMinutes()).isEqualTo(40)
        assertThat(reference.strongCompletionRatio()).isEqualTo(0.5)
    }

    private fun week(
        weekStart: LocalDate,
        completed: List<Int>,
        planned: List<Int> = completed,
        createdOn: LocalDate = weekStart,
        ruleIds: List<String> = listOf("R-ZONE-01"),
    ): WeeklyPlan {
        val sessions = planned.mapIndexed { index, minutes ->
            val done = index < completed.size
            PlannedSession(
                date = weekStart.plusDays(index.toLong()),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = minutes,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = if (done) SessionStatus.DONE else SessionStatus.PLANNED,
                completionEvidence = if (done) CompletionEvidence.ACTIVITY else null,
                activityId = if (done) "activity-$index" else null,
            )
        }
        return WeeklyPlan(
            weekStart = weekStart,
            sessions = sessions,
            weeklyMinutes = planned.sum(),
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Semana.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = ruleIds,
            evidenceIds = listOf("EV-SEILER-2010"),
            createdOn = createdOn,
        )
    }
}
