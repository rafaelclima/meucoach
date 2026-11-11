package com.meucoach.app.ui.common

import com.meucoach.core.domain.race.RaceSplitProjection
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class RaceEventLabelsTest {
    @Test
    fun `formats every supported distance and status`() {
        assertThat(RaceDistance.entries.map(::raceDistanceLabel))
            .containsExactly("5 km", "10 km", "Meia maratona", "Maratona")
        assertThat(RaceStatus.entries.map(::raceStatusLabel))
            .containsExactly("Planejada", "Concluída", "Cancelada", "Não compareceu")
    }

    @Test
    fun `shows the target time only when one was declared`() {
        assertThat(raceTargetTimeLabel(null)).isNull()
        assertThat(raceTargetTimeLabel(1_500)).isEqualTo("25:00")
        assertThat(raceTargetTimeLabel(3_723)).isEqualTo("1:02:03")
    }

    @Test
    fun `formats the reference pace and band with units`() {
        assertThat(racePaceLabel(300)).isEqualTo("5:00/km")
        assertThat(racePaceRangeLabel(291..309)).isEqualTo("4:51–5:09/km")
    }

    @Test
    fun `formats full kilometer and fractional finish splits`() {
        assertThat(raceProjectionSplitLabel(RaceSplitProjection(1_000, 300, isFinish = false)))
            .isEqualTo("1 km · 5:00")
        assertThat(raceProjectionSplitLabel(RaceSplitProjection(5_000, 1_500, isFinish = true)))
            .isEqualTo("5 km · 25:00")
        assertThat(raceProjectionSplitLabel(RaceSplitProjection(21_098, 6_300, isFinish = true)))
            .isEqualTo("Chegada · 1:45:00")
    }
}
