package com.meucoach.app.ui.today

import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ReadinessLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class TodayViewModelTest {
    @Test
    fun `shows a pending check-in with low confidence`() {
        val state = todayState(null)

        assertThat(state.readinessScore).isEqualTo(50)
        assertThat(state.readinessLevel).isEqualTo(ReadinessLevel.AMBER)
        assertThat(state.readinessConfidence).isEqualTo(DataConfidence.LOW)
        assertThat(state.readinessSignals.single().value).isEqualTo("Pendente")
    }

    @Test
    fun `uses the check-in score and confidence`() {
        val state = todayState(checkIn())

        assertThat(state.readinessScore).isGreaterThan(70)
        assertThat(state.readinessLevel).isEqualTo(ReadinessLevel.GREEN)
        assertThat(state.readinessConfidence).isEqualTo(DataConfidence.LOW)
        assertThat(state.readinessSignals.single().value).isEqualTo("7/20")
    }

    @Test
    fun `flags illness as a warning`() {
        val state = todayState(checkIn(illnessOrInjury = true))

        assertThat(state.readinessLevel).isEqualTo(ReadinessLevel.RED)
        assertThat(state.readinessSignals.single().tone).isEqualTo(SignalTone.WARNING)
        assertThat(state.readinessSignals.single().hint).isEqualTo("sinal de alerta")
    }

    private fun checkIn(illnessOrInjury: Boolean = false) = DailyCheckIn(
        date = LocalDate.of(2026, 10, 30),
        sleepQuality = 4,
        fatigue = 2,
        soreness = 1,
        stress = 2,
        illnessOrInjury = illnessOrInjury,
    )
}
