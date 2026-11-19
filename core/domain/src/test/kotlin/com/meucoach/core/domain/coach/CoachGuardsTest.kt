package com.meucoach.core.domain.coach

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.RaceStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CoachGuardsTest {
    private val today = LocalDate.of(2026, 11, 16)

    @Test
    fun `semana verde nao mostra guarda`() {
        val guards = CoachGuards.resolve(
            CoachGuardInputs(
                load = snapshot(daysWithLoad = 10, acwr = 1.0, monotony = 1.2),
                races = emptyList(),
                hasAvailability = true,
                today = today,
            ),
        )

        assertThat(guards).isEmpty()
    }

    @Test
    fun `calibracao mostra o progresso da base objetiva`() {
        val guards = CoachGuards.resolve(
            CoachGuardInputs(
                load = snapshot(daysWithLoad = 5),
                races = emptyList(),
                hasAvailability = true,
                today = today,
            ),
        )

        assertThat(guards).hasSize(1)
        assertThat(guards[0].kind).isEqualTo(CoachGuardKind.CALIBRATION)
        assertThat(guards[0].detail).contains("5 de 28 dias")
    }

    @Test
    fun `base parcial com acwr instavel segue em calibracao`() {
        val guards = CoachGuards.resolve(
            CoachGuardInputs(
                load = snapshot(daysWithLoad = 8, acwr = 1.6, beforeHistory = 20),
                races = emptyList(),
                hasAvailability = true,
                today = today,
            ),
        )

        assertThat(guards.map { it.kind }).containsExactly(CoachGuardKind.CALIBRATION)
        assertThat(guards[0].detail).contains("8 de 28 dias")
    }

    @Test
    fun `fonte velha pede atualizacao`() {
        val guards = CoachGuards.resolve(
            CoachGuardInputs(
                load = snapshot(daysWithLoad = 10, acwr = 1.0, sourceFresh = false),
                races = emptyList(),
                hasAvailability = true,
                today = today,
            ),
        )

        assertThat(guards.map { it.kind }).containsExactly(CoachGuardKind.STALE_SOURCE)
    }

    @Test
    fun `janela pos-prova mostra teto e fim`() {
        val guards = CoachGuards.resolve(
            CoachGuardInputs(
                load = snapshot(daysWithLoad = 10, acwr = 1.0),
                races = listOf(completed(RaceDistance.FIVE_K, today.minusDays(1))),
                hasAvailability = true,
                today = today,
            ),
        )

        assertThat(guards.map { it.kind }).containsExactly(CoachGuardKind.RECOVERY)
        assertThat(guards[0].title).contains("18/11")
        assertThat(guards[0].detail).contains("30 min")
    }

    @Test
    fun `prova proxima vira semana de prova e distante vira taper`() {
        val raceWeek = CoachGuards.resolve(
            CoachGuardInputs(
                load = snapshot(daysWithLoad = 10, acwr = 1.0),
                races = listOf(planned(RaceDistance.FIVE_K, today.plusDays(5))),
                hasAvailability = true,
                today = today,
            ),
        )
        assertThat(raceWeek.map { it.kind }).containsExactly(CoachGuardKind.RACE_WEEK)

        val taper = CoachGuards.resolve(
            CoachGuardInputs(
                load = snapshot(daysWithLoad = 10, acwr = 1.0),
                races = listOf(planned(RaceDistance.MARATHON, today.plusDays(20))),
                hasAvailability = true,
                today = today,
            ),
        )
        assertThat(taper.map { it.kind }).containsExactly(CoachGuardKind.TAPER)
    }

    @Test
    fun `monotonia e pico aparecem juntos`() {
        val guards = CoachGuards.resolve(
            CoachGuardInputs(
                load = snapshot(daysWithLoad = 10, acwr = 1.0, monotony = 2.5, strainSpike = true),
                races = emptyList(),
                hasAvailability = true,
                today = today,
            ),
        )

        assertThat(guards.map { it.kind }).containsExactly(
            CoachGuardKind.MONOTONY,
            CoachGuardKind.STRAIN,
        )
    }

    @Test
    fun `sem disponibilidade avisa a base`() {
        val guards = CoachGuards.resolve(
            CoachGuardInputs(
                load = snapshot(daysWithLoad = 10, acwr = 1.0),
                races = emptyList(),
                hasAvailability = false,
                today = today,
            ),
        )

        assertThat(guards.map { it.kind }).containsExactly(CoachGuardKind.NO_AVAILABILITY)
    }

    private fun snapshot(
        daysWithLoad: Int,
        acwr: Double? = null,
        monotony: Double? = 1.0,
        strainSpike: Boolean = false,
        sourceFresh: Boolean = true,
        beforeHistory: Int = 0,
    ): LoadSnapshot {
        val withoutData = if (sourceFresh) 0 else 28 - daysWithLoad
        return LoadSnapshot(
            daily = emptyList(),
            ctlSeries = emptyList(),
            atlSeries = emptyList(),
            ctl = 0.0,
            atl = 0.0,
            tsb = 0.0,
            acuteAverage7d = 0.0,
            chronicAverage28d = 10.0,
            acwr = acwr,
            monotony = monotony,
            missingDays = 0,
            coverage = LoadCoverage(
                daysInWindow = 28,
                daysWithLoad = daysWithLoad,
                daysWithoutActivity = 28 - daysWithLoad - withoutData,
                daysWithoutData = withoutData,
                lastActivityAt = Instant.parse("2026-11-15T12:00:00Z"),
                sourceFresh = sourceFresh,
                daysBeforeHistory = beforeHistory,
            ),
            strain = if (strainSpike) 200.0 else null,
        )
    }

    private fun completed(distance: RaceDistance, date: LocalDate) = RaceEvent(
        id = "race-1",
        distance = distance,
        targetDate = date,
        status = RaceStatus.COMPLETED,
        createdAt = Instant.parse("2026-11-16T12:00:00Z"),
        updatedAt = Instant.parse("2026-11-16T12:00:00Z"),
        resolvedAt = Instant.parse("2026-11-16T12:00:00Z"),
    )

    private fun planned(distance: RaceDistance, date: LocalDate) = RaceEvent(
        id = "race-1",
        distance = distance,
        targetDate = date,
        status = RaceStatus.PLANNED,
        createdAt = Instant.parse("2026-11-16T12:00:00Z"),
        updatedAt = Instant.parse("2026-11-16T12:00:00Z"),
    )
}
