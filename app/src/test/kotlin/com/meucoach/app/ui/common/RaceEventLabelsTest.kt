package com.meucoach.app.ui.common

import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class RaceEventLabelsTest {
    @Test
    fun `formats every supported distance and status`() {
        assertThat(RaceDistance.entries.map(::raceDistanceLabel))
            .containsExactly("5 km", "10 km", "Meia maratona", "Maratona")
        assertThat(RaceStatus.entries.map(::raceStatusLabel))
            .containsExactly("Planejada", "Concluída", "Cancelada", "Não compareceu")
    }

    @Test
    fun `parses the date mask without shifting the day`() {
        assertThat(parseRaceDate("14032027")).isEqualTo(LocalDate.of(2027, 3, 14))
        assertThat(parseRaceDate("14/03/2027")).isEqualTo(LocalDate.of(2027, 3, 14))
        assertThat(parseRaceDate("31022027")).isNull()
        assertThat(parseRaceDate("1403")).isNull()
    }

    @Test
    fun `parses an optional declared time and rejects pace-like input`() {
        assertThat(parseRaceTargetTime("25:00")).isEqualTo(1_500)
        assertThat(parseRaceTargetTime("1:02:03")).isEqualTo(3_723)
        assertThat(parseRaceTargetTime("")).isNull()
        assertThat(parseRaceTargetTime("6:04/km")).isNull()
        assertThat(parseRaceTargetTime("0:00")).isNull()
    }

    @Test
    fun `shows the target time only when one was declared`() {
        assertThat(raceTargetTimeLabel(null)).isNull()
        assertThat(raceTargetTimeLabel(1_500)).isEqualTo("25:00")
        assertThat(raceTargetTimeLabel(3_723)).isEqualTo("1:02:03")
    }
}
