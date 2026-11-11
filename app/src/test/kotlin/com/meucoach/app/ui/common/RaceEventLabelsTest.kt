package com.meucoach.app.ui.common

import androidx.compose.ui.text.AnnotatedString
import com.meucoach.core.domain.race.RaceSplitProjection
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
    fun `date mask keeps raw digits and maps the caret around the slashes`() {
        val result = RaceDateVisualTransformation.filter(AnnotatedString("14032027"))

        assertThat(result.text.text).isEqualTo("14/03/2027")
        assertThat(result.offsetMapping.originalToTransformed(0)).isEqualTo(0)
        assertThat(result.offsetMapping.originalToTransformed(2)).isEqualTo(2)
        assertThat(result.offsetMapping.originalToTransformed(3)).isEqualTo(4)
        assertThat(result.offsetMapping.originalToTransformed(4)).isEqualTo(5)
        assertThat(result.offsetMapping.transformedToOriginal(3)).isEqualTo(2)
        assertThat(result.offsetMapping.transformedToOriginal(6)).isEqualTo(4)
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
