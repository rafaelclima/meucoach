package com.meucoach.core.domain.prescription

import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.RaceDistance
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * Fatia 1 — ADR 0043: macro-planejador puro (TDD RED).
 *
 * Contrato: [MacroPlanResolver.resolve] distribui blocos até a prova pela
 * tabela aprovada (5k/10k 8 sem, meia 12, maratona 16), com taper reutilizado
 * de [RacePlan.taperWindowDays] e `PEAK` estável; sem prova (ou prova além do
 * horizonte/passada), devolve o caminho geral sem `PEAK`/taper. Determinístico,
 * com `inputsHash` estável e `R-PER-01` emitido.
 */
class MacroPlanTest {
    private val monday = LocalDate.of(2026, 11, 9)

    private fun race(distance: RaceDistance, raceWeekStart: LocalDate): RacePlan =
        RacePlan(eventId = "race-1", distance = distance, targetDate = raceWeekStart.plusDays(6))

    @Test
    fun `G-PER-02 eight synthetic weeks to 5k declare blocks and taper`() {
        val plan = MacroPlanResolver.resolve(race(RaceDistance.FIVE_K, monday.plusWeeks(7)), monday)

        assertThat(plan.isGeneral).isFalse()
        assertThat(plan.weeks.map { it.phase }).containsExactly(
            MesoPhase.BASE,
            MesoPhase.BASE,
            MesoPhase.BASE,
            MesoPhase.BUILD,
            MesoPhase.BUILD,
            MesoPhase.PEAK,
            MesoPhase.TAPER,
            MesoPhase.TAPER,
        )
        assertThat(plan.weeks.map { it.blockIndex }).containsExactly(1, 1, 1, 2, 2, 3, 4, 4)
        assertThat(plan.ruleIds).containsExactly("R-PER-01")
        assertThat(plan.evidenceIds).contains(
            "EV-ISSURIN-2010",
            "EV-BOSQUET-2007",
            "EV-MUJIKA-2003",
        )
        assertThat(plan.confidence).isEqualTo(DataConfidence.MEDIUM)
        assertThat(plan.inputsHash).isNotBlank()
    }

    @Test
    fun `marathon resolves a sixteen week prep with a three week taper`() {
        val plan = MacroPlanResolver.resolve(race(RaceDistance.MARATHON, monday.plusWeeks(15)), monday)

        assertThat(plan.isGeneral).isFalse()
        assertThat(plan.weeks).hasSize(16)
        val phases = plan.weeks.map { it.phase }
        assertThat(phases.takeLast(3)).containsOnly(MesoPhase.TAPER)
        assertThat(phases.dropLast(3).takeLast(2)).containsOnly(MesoPhase.PEAK)
        assertThat(phases.take(6)).containsOnly(MesoPhase.BASE)
        assertThat(phases.drop(6).take(5)).containsOnly(MesoPhase.BUILD)
    }

    @Test
    fun `half marathon resolves a twelve week prep`() {
        val plan =
            MacroPlanResolver.resolve(race(RaceDistance.HALF_MARATHON, monday.plusWeeks(11)), monday)

        assertThat(plan.isGeneral).isFalse()
        assertThat(plan.weeks.map { it.phase }).containsExactly(
            MesoPhase.BASE,
            MesoPhase.BASE,
            MesoPhase.BASE,
            MesoPhase.BASE,
            MesoPhase.BUILD,
            MesoPhase.BUILD,
            MesoPhase.BUILD,
            MesoPhase.BUILD,
            MesoPhase.PEAK,
            MesoPhase.PEAK,
            MesoPhase.TAPER,
            MesoPhase.TAPER,
        )
    }

    @Test
    fun `late prep start continues the table instead of restarting at base`() {
        val plan = MacroPlanResolver.resolve(race(RaceDistance.FIVE_K, monday.plusWeeks(3)), monday)

        assertThat(plan.isGeneral).isFalse()
        assertThat(plan.weeks.map { it.phase }).containsExactly(
            MesoPhase.BUILD,
            MesoPhase.PEAK,
            MesoPhase.TAPER,
            MesoPhase.TAPER,
        )
    }

    @Test
    fun `mid prep current week continues the table`() {
        val plan = MacroPlanResolver.resolve(race(RaceDistance.FIVE_K, monday.plusWeeks(4)), monday)

        assertThat(plan.isGeneral).isFalse()
        assertThat(plan.weeks.map { it.phase }).containsExactly(
            MesoPhase.BUILD,
            MesoPhase.BUILD,
            MesoPhase.PEAK,
            MesoPhase.TAPER,
            MesoPhase.TAPER,
        )
    }

    @Test
    fun `no race resolves the general path without peak or taper`() {
        val plan = MacroPlanResolver.resolve(race = null, today = monday)

        assertThat(plan.isGeneral).isTrue()
        assertThat(plan.weeks).isEmpty()
        assertThat(plan.ruleIds).containsExactly("R-PER-01")
        assertThat(plan.confidence).isEqualTo(DataConfidence.MEDIUM)
    }

    @Test
    fun `race beyond the horizon stays general`() {
        val plan = MacroPlanResolver.resolve(race(RaceDistance.FIVE_K, monday.plusWeeks(10)), monday)

        assertThat(plan.isGeneral).isTrue()
        assertThat(plan.weeks).isEmpty()
    }

    @Test
    fun `past race resolves general`() {
        val plan =
            MacroPlanResolver.resolve(
                race = RacePlan("race-1", RaceDistance.TEN_K, monday.minusDays(1)),
                today = monday,
            )

        assertThat(plan.isGeneral).isTrue()
        assertThat(plan.weeks).isEmpty()
    }

    @Test
    fun `resolution is deterministic with a stable inputs hash`() {
        val race = race(RaceDistance.TEN_K, monday.plusWeeks(7))
        val first = MacroPlanResolver.resolve(race, monday)
        val second = MacroPlanResolver.resolve(race, monday)

        assertThat(second).isEqualTo(first)
        assertThat(second.inputsHash).isEqualTo(first.inputsHash)
        val other =
            MacroPlanResolver.resolve(race.copy(targetDate = race.targetDate.plusWeeks(1)), monday)
        assertThat(other.inputsHash).isNotEqualTo(first.inputsHash)
    }
}
