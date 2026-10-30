package com.meucoach.core.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.LocalDate

class DailyMetricsTest {
    @Test
    fun `accepts a complete metrics row`() {
        val metrics = DailyMetrics(
            date = LocalDate.of(2026, 10, 30),
            sleepDurationMin = 450,
            restingHeartRate = 58.0,
            hrvRmssdMs = 60.0,
            spo2Avg = 97.0,
        )

        assertThat(metrics.hasAnyMetric).isTrue()
    }

    @Test
    fun `allows missing values`() {
        val metrics = DailyMetrics(date = LocalDate.of(2026, 10, 30))

        assertThat(metrics.hasAnyMetric).isFalse()
        assertThat(metrics.sleepDurationMin).isNull()
    }

    @Test
    fun `rejects values outside physical ranges`() {
        val date = LocalDate.of(2026, 10, 30)
        assertThatThrownBy { DailyMetrics(date, sleepDurationMin = 1_441) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { DailyMetrics(date, restingHeartRate = 20.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { DailyMetrics(date, hrvRmssdMs = 0.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { DailyMetrics(date, spo2Avg = 101.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
