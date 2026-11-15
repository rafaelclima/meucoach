package com.meucoach.core.domain.prescription

import com.meucoach.core.model.RaceDistance
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * Fatia 2 — taper com micro-dose + recovery por severidade (TDD RED).
 *
 * Contrato (SPORTS_SCIENCE v1.1):
 * - Recovery 5k 3d / 10k 5d / meia 10d / maratona 16d, com fases
 *   30/45 (meia dias 6–10) e 30/60 (maratona dias 8–16).
 * - Taper dentro do corte/semana da prova: 1 micro-dose ≤25 min, nunca zero.
 */
class TaperRecoveryV2Test {
    private val raceDay = LocalDate.of(2026, 11, 8)

    private fun window(distance: RaceDistance) = RecoveryWindow(CompletedRace("race-1", distance, raceDay))

    @Test
    fun `uses severity windows 3 5 10 16`() {
        assertThat(window(RaceDistance.FIVE_K).totalDays).isEqualTo(3)
        assertThat(window(RaceDistance.TEN_K).totalDays).isEqualTo(5)
        assertThat(window(RaceDistance.HALF_MARATHON).totalDays).isEqualTo(10)
        assertThat(window(RaceDistance.MARATHON).totalDays).isEqualTo(16)
    }

    @Test
    fun `caps sessions by severity phase`() {
        val half = window(RaceDistance.HALF_MARATHON)
        assertThat(half.maxSessionMinutes(raceDay.plusDays(5))).isEqualTo(30)
        assertThat(half.maxSessionMinutes(raceDay.plusDays(6))).isEqualTo(45)
        assertThat(half.maxSessionMinutes(raceDay.plusDays(10))).isEqualTo(45)
        assertThat(half.maxSessionMinutes(raceDay.plusDays(11))).isNull()

        val marathon = window(RaceDistance.MARATHON)
        assertThat(marathon.maxSessionMinutes(raceDay.plusDays(7))).isEqualTo(30)
        assertThat(marathon.maxSessionMinutes(raceDay.plusDays(8))).isEqualTo(60)
        assertThat(marathon.maxSessionMinutes(raceDay.plusDays(16))).isEqualTo(60)
        assertThat(marathon.maxSessionMinutes(raceDay.plusDays(17))).isNull()
    }
}
