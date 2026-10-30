package com.meucoach.app.ui.today

import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DailyMetricsSource
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ReadinessLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class TodayViewModelTest {
    private val today = LocalDate.of(2026, 10, 30)

    @Test
    fun `shows a pending check-in with low confidence`() {
        val state = todayState(null, today = today)

        assertThat(state.readinessScore).isEqualTo(50)
        assertThat(state.readinessLevel).isEqualTo(ReadinessLevel.AMBER)
        assertThat(state.readinessConfidence).isEqualTo(DataConfidence.LOW)
        assertThat(state.readinessSignals.single().value).isEqualTo("Pendente")
        assertThat(state.hasCheckedIn).isFalse()
    }

    @Test
    fun `uses the check-in score and confidence`() {
        val state = todayState(checkIn(), today = today)

        assertThat(state.readinessScore).isGreaterThan(70)
        assertThat(state.readinessLevel).isEqualTo(ReadinessLevel.GREEN)
        assertThat(state.readinessConfidence).isEqualTo(DataConfidence.LOW)
        assertThat(state.readinessSignals.single().value).isEqualTo("7/20")
        assertThat(state.hasCheckedIn).isTrue()
    }

    @Test
    fun `flags illness as a warning`() {
        val state = todayState(checkIn(illnessOrInjury = true), today = today)

        assertThat(state.readinessLevel).isEqualTo(ReadinessLevel.RED)
        assertThat(state.readinessSignals.single().tone).isEqualTo(SignalTone.WARNING)
        assertThat(state.readinessSignals.single().hint).isEqualTo("sinal de alerta")
    }

    @Test
    fun `shows objective signals and raises confidence`() {
        val metrics = (0..9).map { daysAgo ->
            DailyMetrics(
                date = today.minusDays(daysAgo.toLong()),
                sleepDurationMin = if (daysAgo == 0) 450 else null,
                restingHeartRate = 58.0,
                hrvRmssdMs = 60.0 + daysAgo,
                source = DailyMetricsSource.CSV,
            )
        }

        val state = todayState(checkIn(), metrics, today)

        assertThat(state.readinessConfidence).isEqualTo(DataConfidence.HIGH)
        assertThat(state.readinessSignals.map { it.label })
            .contains("Sono", "HRV", "FC repouso")
        assertThat(state.readinessSignals.first { it.label == "Sono" }.value).isEqualTo("7h30")
    }

    @Test
    fun `ignores metrics from the future`() {
        val future = DailyMetrics(
            date = today.plusDays(1),
            sleepDurationMin = 450,
            source = DailyMetricsSource.CSV,
        )

        val state = todayState(checkIn(), listOf(future), today)

        assertThat(state.readinessSignals.map { it.label }).doesNotContain("Sono")
    }

    private fun checkIn(illnessOrInjury: Boolean = false) = DailyCheckIn(
        date = today,
        sleepQuality = 4,
        fatigue = 2,
        soreness = 1,
        stress = 2,
        illnessOrInjury = illnessOrInjury,
    )
}
