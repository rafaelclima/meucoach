package com.meucoach.app.ui.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class TargetTimeInputTest {
    @Test
    fun `splits persisted seconds into the selected mask`() {
        val minutes = targetTimeParts(1_500, TargetTimeUnit.MINUTES)
        assertThat(minutes).isEqualTo(TargetTimeParts(minutes = "25", seconds = "00"))
        assertThat(minutes.totalSeconds(TargetTimeUnit.MINUTES)).isEqualTo(1_500)

        val hours = targetTimeParts(3_723, TargetTimeUnit.HOURS)
        assertThat(hours).isEqualTo(TargetTimeParts(hours = "1", minutes = "02", seconds = "03"))
        assertThat(hours.totalSeconds(TargetTimeUnit.HOURS)).isEqualTo(3_723)
    }

    @Test
    fun `accepts more than 59 minutes when the user chooses minutes`() {
        val parts = TargetTimeParts(minutes = "60", seconds = "00")

        assertThat(parts.totalSeconds(TargetTimeUnit.MINUTES)).isEqualTo(3_600)
        assertThat(parts.totalSeconds(TargetTimeUnit.HOURS)).isNull()
    }

    @Test
    fun `rejects impossible hour or minute segments`() {
        assertThat(TargetTimeParts(minutes = "1", seconds = "60").totalSeconds(TargetTimeUnit.MINUTES)).isNull()
        assertThat(
            TargetTimeParts(hours = "1", minutes = "60", seconds = "00")
                .totalSeconds(TargetTimeUnit.HOURS),
        ).isNull()
        assertThat(TargetTimeParts(minutes = "abc", seconds = "00").totalSeconds(TargetTimeUnit.MINUTES)).isNull()
    }

    @Test
    fun `blank is optional and zero is not a valid target`() {
        assertThat(TargetTimeParts().totalSeconds(TargetTimeUnit.MINUTES)).isNull()
        assertThat(TargetTimeParts(minutes = "0", seconds = "00").totalSeconds(TargetTimeUnit.MINUTES))
            .isEqualTo(0)
    }

    @Test
    fun `converts the current value when the unit changes`() {
        val minutes = TargetTimeParts(minutes = "90", seconds = "30")

        val hours = minutes.convert(TargetTimeUnit.MINUTES, TargetTimeUnit.HOURS)
        assertThat(hours).isEqualTo(TargetTimeParts(hours = "1", minutes = "30", seconds = "30"))
        assertThat(hours.totalSeconds(TargetTimeUnit.HOURS)).isEqualTo(5_430)

        val back = hours.convert(TargetTimeUnit.HOURS, TargetTimeUnit.MINUTES)
        assertThat(back).isEqualTo(TargetTimeParts(minutes = "90", seconds = "30"))
    }

    @Test
    fun `mask labels follow the selected unit`() {
        assertThat(TargetTimeUnit.MINUTES.maskLabel()).isEqualTo("mm:ss")
        assertThat(TargetTimeUnit.HOURS.maskLabel()).isEqualTo("h:mm:ss")
        assertThat(TargetTimeUnit.MINUTES.unitLabel()).isEqualTo("Minutos")
        assertThat(TargetTimeUnit.HOURS.unitLabel()).isEqualTo("Horas")
    }
}
