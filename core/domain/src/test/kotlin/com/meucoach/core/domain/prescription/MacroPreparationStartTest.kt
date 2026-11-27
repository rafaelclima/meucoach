package com.meucoach.core.domain.prescription

import com.meucoach.core.model.RaceDistance
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

/** ADR 0072: the macro plan exposes when the specific table starts. */
class MacroPreparationStartTest {
    private val monday = LocalDate.of(2026, 11, 9)

    @Test
    fun `ten K beyond horizon prepares the specific start without phases`() {
        val race = RacePlan("race", RaceDistance.TEN_K, monday.plusWeeks(10).plusDays(6))

        val plan = MacroPlanResolver.resolve(race, monday)

        assertThat(plan.isGeneral).isTrue()
        assertThat(plan.weeks).isEmpty()
        assertThat(plan.horizonWeeks).isEqualTo(8)
        assertThat(plan.preparationStart).isEqualTo(monday.plusWeeks(3))
    }

    @Test
    fun `twelve week 5K starts the table this week`() {
        val race = RacePlan("race", RaceDistance.FIVE_K, monday.plusWeeks(11).plusDays(6))

        val plan = MacroPlanResolver.resolve(race, monday)

        assertThat(plan.isGeneral).isFalse()
        assertThat(plan.horizonWeeks).isEqualTo(12)
        assertThat(plan.preparationStart).isEqualTo(monday)
    }

    @Test
    fun `no race has no preparation start`() {
        val plan = MacroPlanResolver.resolve(race = null, today = monday)

        assertThat(plan.preparationStart).isNull()
        assertThat(plan.horizonWeeks).isNull()
    }

    @Test
    fun `labels are the canonical glossary names`() {
        assertThat(MesoPhaseLabels.of(MesoPhase.BASE)).isEqualTo("Base")
        assertThat(MesoPhaseLabels.of(MesoPhase.BUILD)).isEqualTo("Construção")
        assertThat(MesoPhaseLabels.of(MesoPhase.PEAK)).isEqualTo("Pico")
        assertThat(MesoPhaseLabels.of(MesoPhase.TAPER)).isEqualTo("Semana de prova")
        assertThat(MesoPhaseLabels.of(MesoPhase.RECOVERY)).isEqualTo("Recuperação")
    }
}
