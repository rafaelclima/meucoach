package com.meucoach.core.domain.cycle

import com.meucoach.core.domain.prescription.MesoCycle
import com.meucoach.core.domain.prescription.TaperBaselineResolver
import com.meucoach.core.domain.prescription.VolumeGates
import com.meucoach.core.domain.prescription.VolumeProgression
import com.meucoach.core.domain.prescription.VolumeReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * ADR 0072 §2.8: a persisted preview or any future reference must never change
 * adherence, volume, mesocycle, deload or taper before its week starts.
 */
class FutureWeekIsolationTest {
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `future reference does not change the volume decision`() {
        val previous = monday.minusWeeks(1)
        val references = listOf(reference(previous, strongMinutes = 120))
        val base = VolumeProgression().decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 300,
            references = references,
            gates = VolumeGates(),
        )
        val withFuture = VolumeProgression().decide(
            targetWeekStart = monday,
            availabilityCeilingMinutes = 300,
            references = references + reference(monday.plusWeeks(4), strongMinutes = 999),
            gates = VolumeGates(),
        )

        assertThat(withFuture).isEqualTo(base)
    }

    @Test
    fun `future reference does not change the mesocycle or deload`() {
        val references = listOf(
            reference(monday.minusWeeks(1)),
            reference(monday.minusWeeks(2)),
        )
        val base = MesoCycle.resolve(
            targetWeekStart = monday,
            race = null,
            recoveryActive = false,
            isRaceWeek = false,
            isTaperWeek = false,
            references = references,
        )
        val withFuture = MesoCycle.resolve(
            targetWeekStart = monday,
            race = null,
            recoveryActive = false,
            isRaceWeek = false,
            isTaperWeek = false,
            references = references + reference(monday.plusWeeks(4), strongMinutes = 999),
        )

        assertThat(withFuture).isEqualTo(base)
    }

    @Test
    fun `future reference does not change the taper baseline`() {
        val firstTaper = monday.plusWeeks(2)
        val references = listOf(
            reference(monday, strongMinutes = 100),
            reference(monday.plusWeeks(1), strongMinutes = 110),
        )
        val base = TaperBaselineResolver.resolve(references, firstTaper)
        val withFuture = TaperBaselineResolver.resolve(
            references + reference(firstTaper.plusWeeks(2), strongMinutes = 999),
            firstTaper,
        )

        assertThat(withFuture).isEqualTo(base)
    }

    private fun reference(weekStart: LocalDate, strongMinutes: Int = 60) = VolumeReference(
        weekStart = weekStart,
        plannedMinutes = strongMinutes,
        executedMinutes = strongMinutes,
        strongMinutes = strongMinutes,
        strongSessions = 2,
        plannedSessions = 3,
        createdOn = weekStart,
        specialWeek = false,
    )
}
