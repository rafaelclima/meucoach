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
        val previous = monday.minusWeeks(1)
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 210,
            references = listOf(reference(previous, strongMinutes = 120)),
            previousWeek = week(previous, completed = listOf(30, 30, 30, 30)),
        )

        assertThat(decision.baseMinutes).isEqualTo(120)
        assertThat(decision.baseWeekStart).isEqualTo(previous)
        assertThat(decision.minutes).isEqualTo(127)
        assertThat(decision.growthPercent).isEqualTo(6)
        assertThat(decision.block).isEqualTo(VolumeGrowthBlock.NONE)
        assertThat(decision.cappedByAvailability).isFalse()
    }

    @Test
    fun `maintains the base when the previous week is below eighty percent`() {
        val previous = monday.minusWeeks(1)
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 210,
            references = listOf(reference(previous, strongMinutes = 60)),
            previousWeek = week(
                previous,
                completed = listOf(30, 30),
                planned = listOf(30, 30, 30, 30),
            ),
        )

        assertThat(decision.baseMinutes).isEqualTo(60)
        assertThat(decision.minutes).isEqualTo(60)
        assertThat(decision.growthPercent).isZero()
        assertThat(decision.block).isEqualTo(VolumeGrowthBlock.PREVIOUS_WEEK_BELOW_80)
    }

    @Test
    fun `an older completed week is the base but never grows`() {
        val previous = monday.minusWeeks(1)
        val older = monday.minusWeeks(2)
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 300,
            references = listOf(
                reference(previous, strongMinutes = 60),
                reference(older, strongMinutes = 200),
            ),
            previousWeek = week(
                previous,
                completed = listOf(30, 30),
                planned = listOf(30, 30, 30, 30),
            ),
        )

        assertThat(decision.baseMinutes).isEqualTo(200)
        assertThat(decision.baseWeekStart).isEqualTo(older)
        assertThat(decision.minutes).isEqualTo(200)
        assertThat(decision.growthPercent).isZero()
        assertThat(decision.block).isEqualTo(VolumeGrowthBlock.PREVIOUS_WEEK_BELOW_80)
    }

    @Test
    fun `a special previous week does not grow the next one`() {
        val previous = monday.minusWeeks(1)
        val older = monday.minusWeeks(2)
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 300,
            references = listOf(
                reference(previous, strongMinutes = 120, specialWeek = true),
                reference(older, strongMinutes = 200),
            ),
            previousWeek = week(previous, completed = listOf(30, 30, 30, 30)),
        )

        assertThat(decision.baseMinutes).isEqualTo(200)
        assertThat(decision.minutes).isEqualTo(200)
        assertThat(decision.block).isEqualTo(VolumeGrowthBlock.PREVIOUS_WEEK_SPECIAL)
    }

    @Test
    fun `special target weeks never grow`() {
        val previous = monday.minusWeeks(1)
        val references = listOf(reference(previous, strongMinutes = 120))
        val previousWeek = week(previous, completed = listOf(30, 30, 30, 30))

        VolumeGrowthBlock.entries
            .filter {
                it in setOf(
                    VolumeGrowthBlock.DELOAD,
                    VolumeGrowthBlock.PEAK,
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
                    previousWeek = previousWeek,
                    gates = VolumeGates(targetWeekBlock = block),
                )

                assertThat(decision.minutes).isEqualTo(120)
                assertThat(decision.growthPercent).isZero()
                assertThat(decision.block).isEqualTo(block)
            }
    }

    @Test
    fun `readiness load and acwr each block the increase`() {
        val previous = monday.minusWeeks(1)
        val references = listOf(reference(previous, strongMinutes = 120))
        val previousWeek = week(previous, completed = listOf(30, 30, 30, 30))

        val notGreen = progression.decide(
            monday,
            210,
            references,
            previousWeek,
            VolumeGates(readinessGreen = false),
        )
        val loadUnknown = progression.decide(
            monday,
            210,
            references,
            previousWeek,
            VolumeGates(loadKnown = false),
        )
        val acwrOut = progression.decide(
            monday,
            210,
            references,
            previousWeek,
            VolumeGates(acwrInRange = false),
        )
        val acwrCalibrating = progression.decide(
            monday,
            210,
            references,
            previousWeek,
            VolumeGates(acwrReliable = false),
        )
        val monotonyHigh = progression.decide(
            monday,
            210,
            references,
            previousWeek,
            VolumeGates(monotonyOk = false),
        )
        val strainSpike = progression.decide(
            monday,
            210,
            references,
            previousWeek,
            VolumeGates(strainOk = false),
        )

        assertThat(notGreen.block).isEqualTo(VolumeGrowthBlock.READINESS)
        assertThat(loadUnknown.block).isEqualTo(VolumeGrowthBlock.LOAD_UNKNOWN)
        assertThat(acwrOut.block).isEqualTo(VolumeGrowthBlock.ACWR_OUT_OF_RANGE)
        assertThat(acwrCalibrating.block).isEqualTo(VolumeGrowthBlock.ACWR_UNRELIABLE)
        assertThat(monotonyHigh.block).isEqualTo(VolumeGrowthBlock.MONOTONY_HIGH)
        assertThat(strainSpike.block).isEqualTo(VolumeGrowthBlock.STRAIN_SPIKE)
        assertThat(
            listOf(notGreen, loadUnknown, acwrOut, acwrCalibrating, monotonyHigh, strainSpike)
                .map { it.minutes },
        ).containsOnly(120)
    }

    @Test
    fun `availability caps an increase and a base above the ceiling`() {
        val previous = monday.minusWeeks(1)
        val references = listOf(reference(previous, strongMinutes = 120))
        val previousWeek = week(previous, completed = listOf(30, 30, 30, 30))

        val cappedGrowth = progression.decide(monday, 100, references, previousWeek)
        val cappedBase = progression.decide(
            monday,
            100,
            references,
            previousWeek,
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
        val previous = monday.minusWeeks(1)
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 210,
            references = listOf(
                reference(
                    previous,
                    strongMinutes = 120,
                    createdOn = previous.plusDays(3),
                ),
            ),
            previousWeek = week(previous, completed = listOf(30, 30, 30, 30)),
        )

        assertThat(decision.hasHistory).isFalse()
        assertThat(decision.minutes).isEqualTo(210)
    }

    @Test
    fun `a plan created the day before the week is a volume reference`() {
        val previous = monday.minusWeeks(1)
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 210,
            references = listOf(
                reference(
                    previous,
                    strongMinutes = 120,
                    createdOn = previous.minusDays(1),
                ),
            ),
            previousWeek = week(previous, completed = listOf(30, 30, 30, 30)),
        )

        assertThat(decision.hasHistory).isTrue()
    }

    @Test
    fun `only the last four weeks are considered`() {
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 210,
            references = listOf(reference(monday.minusWeeks(5), strongMinutes = 240)),
        )

        assertThat(decision.hasHistory).isFalse()
    }

    @Test
    fun `a tie goes to the most recent week`() {
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 300,
            references = listOf(
                reference(monday.minusWeeks(1), strongMinutes = 120),
                reference(monday.minusWeeks(2), strongMinutes = 120),
            ),
        )

        assertThat(decision.baseWeekStart).isEqualTo(monday.minusWeeks(1))
    }

    @Test
    fun `a history without the immediately previous week never grows`() {
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 210,
            references = listOf(reference(monday.minusWeeks(2), strongMinutes = 120)),
        )

        assertThat(decision.minutes).isEqualTo(120)
        assertThat(decision.block).isEqualTo(VolumeGrowthBlock.NO_PREVIOUS_WEEK)
    }

    @Test
    fun `the real strong volume is the base not the planned minutes`() {
        val previous = monday.minusWeeks(1)
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 300,
            references = listOf(
                reference(
                    previous,
                    strongMinutes = 142,
                    plannedMinutes = 114,
                ),
            ),
            previousWeek = week(previous, completed = listOf(38, 38, 38)),
        )

        assertThat(decision.baseMinutes).isEqualTo(142)
        assertThat(decision.minutes).isEqualTo(151)
        assertThat(decision.growthPercent).isEqualTo(6)
        assertThat(decision.block).isEqualTo(VolumeGrowthBlock.NONE)
    }

    @Test
    fun `the real strong volume can be lower than the planned minutes`() {
        val previous = monday.minusWeeks(1)
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 300,
            references = listOf(
                reference(
                    previous,
                    strongMinutes = 90,
                    plannedMinutes = 120,
                ),
            ),
            previousWeek = week(previous, completed = listOf(30, 30, 30, 30)),
        )

        assertThat(decision.baseMinutes).isEqualTo(90)
        assertThat(decision.minutes).isEqualTo(95)
    }

    @Test
    fun `extra training counts for volume but never inflates adherence`() {
        val previous = monday.minusWeeks(1)
        // Three planned sessions done (3/3 adherence) plus a 28 min extra run.
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 300,
            references = listOf(reference(previous, strongMinutes = 142)),
            previousWeek = week(previous, completed = listOf(38, 38, 38)),
        )

        assertThat(decision.baseMinutes).isEqualTo(142)
        assertThat(decision.block).isEqualTo(VolumeGrowthBlock.NONE)
        assertThat(decision.growthPercent).isEqualTo(6)
    }

    @Test
    fun `a week without a plan is not a volume reference`() {
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 210,
            references = listOf(
                reference(
                    monday.minusWeeks(1),
                    strongMinutes = 120,
                    plannedMinutes = null,
                    createdOn = null,
                ),
            ),
        )

        assertThat(decision.hasHistory).isFalse()
        assertThat(decision.minutes).isEqualTo(210)
        assertThat(decision.block).isEqualTo(VolumeGrowthBlock.NO_HISTORY)
    }

    @Test
    fun `a week without real strong volume is not a volume reference`() {
        val decision = progression.decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 210,
            references = listOf(reference(monday.minusWeeks(1), strongMinutes = 0)),
        )

        assertThat(decision.hasHistory).isFalse()
    }

    @Test
    fun `only strong evidence counts as completed adherence`() {
        val previous = monday.minusWeeks(1)
        val plan = week(
            previous,
            completed = listOf(40),
            planned = listOf(40, 40),
        ).let { week ->
            val manual = week.sessions[1].copy(
                status = SessionStatus.DONE,
                completionEvidence = CompletionEvidence.MANUAL,
            )
            week.copy(sessions = listOf(week.sessions[0], manual))
        }

        assertThat(plan.strongCompletionRatio()).isEqualTo(0.5)
    }

    private fun reference(
        weekStart: LocalDate,
        strongMinutes: Int,
        plannedMinutes: Int? = 120,
        plannedSessions: Int = 4,
        createdOn: LocalDate? = weekStart,
        specialWeek: Boolean = false,
    ) = VolumeReference(
        weekStart = weekStart,
        plannedMinutes = plannedMinutes,
        executedMinutes = strongMinutes,
        strongMinutes = strongMinutes,
        strongSessions = if (strongMinutes > 0) 1 else 0,
        plannedSessions = plannedSessions,
        createdOn = createdOn,
        specialWeek = specialWeek,
    )

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
