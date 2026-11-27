package com.meucoach.app.ui.today

import com.meucoach.core.domain.cycle.CycleDeload
import com.meucoach.core.domain.cycle.CycleMilestone
import com.meucoach.core.domain.cycle.CycleMilestoneKind
import com.meucoach.core.domain.cycle.CycleOverview
import com.meucoach.core.domain.cycle.CycleRace
import com.meucoach.core.domain.cycle.CycleStrongIntent
import com.meucoach.core.domain.cycle.CycleVolumeIntent
import com.meucoach.core.domain.cycle.CycleWeek
import com.meucoach.core.domain.cycle.CycleWeekState
import com.meucoach.core.domain.prescription.MesoPhase
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.RaceDistance
import java.time.LocalDate

internal object CycleOverviewFixtures {
    val monday: LocalDate = LocalDate.of(2026, 11, 30)

    fun overview(distance: RaceDistance = RaceDistance.FIVE_K): CycleOverview {
        val phases = listOf(
            MesoPhase.BASE,
            MesoPhase.BUILD,
            MesoPhase.BUILD,
            MesoPhase.PEAK,
            MesoPhase.TAPER,
            MesoPhase.TAPER,
        )
        val weeks = phases.mapIndexed { index, phase ->
            val start = monday.plusWeeks(index.toLong())
            val isCurrent = index == 0
            val isNext = index == 1
            val isRace = index == 5
            CycleWeek(
                weekStart = start,
                phase = phase,
                state = when {
                    isRace -> CycleWeekState.RACE
                    isCurrent -> CycleWeekState.CURRENT
                    isNext -> CycleWeekState.NEXT
                    else -> CycleWeekState.FUTURE
                },
                isCurrent = isCurrent,
                isNext = isNext,
                volume = when {
                    isRace -> CycleVolumeIntent.RACE
                    phase == MesoPhase.TAPER -> CycleVolumeIntent.REDUCE
                    phase == MesoPhase.PEAK -> CycleVolumeIntent.MAINTAIN
                    else -> CycleVolumeIntent.BUILD
                },
                strong = when {
                    isRace || phase == MesoPhase.TAPER -> CycleStrongIntent.MICRO_DOSE
                    phase == MesoPhase.BASE -> CycleStrongIntent.UP_TO_ONE
                    else -> CycleStrongIntent.UP_TO_TWO
                },
                plannedMinutes = when (index) {
                    0 -> 120
                    1 -> 130
                    else -> null
                },
                strongDays = when (index) {
                    0 -> listOf(start.plusDays(2))
                    1 -> listOf(start.plusDays(1), start.plusDays(4))
                    else -> emptyList()
                },
                isPreview = isNext,
                deload = if (index == 2) CycleDeload.CONDITIONAL else CycleDeload.NONE,
                deloadConditionWeeks = if (index == 2) 2 else null,
                confidence = if (index == 2) DataConfidence.LOW else DataConfidence.MEDIUM,
            )
        }
        return CycleOverview(
            today = monday,
            race = CycleRace(
                eventId = "race",
                distance = distance,
                targetDate = monday.plusWeeks(5).plusDays(6),
                weeksToRace = 5,
            ),
            weeks = weeks,
            milestones = listOf(
                CycleMilestone(CycleMilestoneKind.DELOAD, monday.plusWeeks(2), conditional = true),
                CycleMilestone(CycleMilestoneKind.TAPER_START, monday.plusWeeks(4)),
                CycleMilestone(CycleMilestoneKind.RACE, monday.plusWeeks(5)),
            ),
            preparationStart = null,
            staleRace = false,
            hasLoadHistory = true,
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-PER-01", "R-PLAN-04", "R-PLAN-05"),
            evidenceIds = listOf("EV-ISSURIN-2010"),
        )
    }
}
