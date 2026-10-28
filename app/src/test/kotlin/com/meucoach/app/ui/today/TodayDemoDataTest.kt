package com.meucoach.app.ui.today

import com.meucoach.core.model.ReadinessLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TodayDemoDataTest {
    @Test
    fun `demo state has a complete week and a single today marker`() {
        val state = demoTodayUiState(LocalDate.of(2026, 10, 27), LocalTime.of(9, 0))

        assertThat(state.days).hasSize(7)
        assertThat(state.days.count { it.isToday }).isEqualTo(1)
        assertThat(state.days[1].isToday).isTrue()
        assertThat(state.weeklyLoadKm).isEqualTo(17.9)
        assertThat(state.readinessLevel).isEqualTo(ReadinessLevel.GREEN)
        assertThat(state.workout.zone).isEqualTo("Z2")
    }

    @Test
    fun `date line is localized for brazilian portuguese`() {
        val state = demoTodayUiState(LocalDate.of(2026, 10, 27), LocalTime.of(9, 0))

        assertThat(state.dateLine).isEqualTo("Ter, 27 de outubro")
    }

    @Test
    fun `greeting follows local time`() {
        val date = LocalDate.of(2026, 10, 27)

        assertThat(demoTodayUiState(date, LocalTime.of(8, 0)).greeting).isEqualTo("Bom dia")
        assertThat(demoTodayUiState(date, LocalTime.of(14, 0)).greeting).isEqualTo("Boa tarde")
        assertThat(demoTodayUiState(date, LocalTime.of(20, 0)).greeting).isEqualTo("Boa noite")
    }
}
