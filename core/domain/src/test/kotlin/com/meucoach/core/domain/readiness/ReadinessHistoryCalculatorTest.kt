package com.meucoach.core.domain.readiness

import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DailyMetricsSource
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ReadinessLevel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.LocalDate

class ReadinessHistoryCalculatorTest {
    private val calculator = ReadinessHistoryCalculator()
    private val today = LocalDate.of(2026, 10, 30)

    @Test
    fun `replays check-ins day by day`() {
        val history = calculator.calculate(
            checkIns = listOf(
                checkIn(today.minusDays(2), fatigue = 1),
                checkIn(today.minusDays(1), fatigue = 4),
                checkIn(today, fatigue = 2),
            ),
            metrics = emptyList(),
            today = today,
            days = 7,
        )

        assertThat(history).hasSize(3)
        assertThat(history.map { it.date })
            .containsExactly(today.minusDays(2), today.minusDays(1), today)
        assertThat(history.last().level).isEqualTo(ReadinessLevel.GREEN)
        assertThat(history[1].score).isLessThan(history.last().score)
        assertThat(history).allSatisfy { assertThat(it.hasCheckIn).isTrue() }
    }

    @Test
    fun `illness forces a red day`() {
        val history = calculator.calculate(
            checkIns = listOf(checkIn(today, illness = true)),
            metrics = emptyList(),
            today = today,
        )

        assertThat(history.single().level).isEqualTo(ReadinessLevel.RED)
        assertThat(history.single().score).isLessThanOrEqualTo(29)
    }

    @Test
    fun `objective metrics raise confidence`() {
        val metrics = (0..9).map { daysAgo ->
            DailyMetrics(
                date = today.minusDays(daysAgo.toLong()),
                sleepDurationMin = if (daysAgo == 0) 450 else null,
                restingHeartRate = 58.0,
                hrvRmssdMs = 60.0 + daysAgo,
                source = DailyMetricsSource.CSV,
            )
        }

        val history = calculator.calculate(
            checkIns = listOf(checkIn(today)),
            metrics = metrics,
            today = today,
            days = 30,
        )

        assertThat(history.last().confidence).isEqualTo(DataConfidence.HIGH)
        assertThat(history.size).isGreaterThanOrEqualTo(10)
    }

    @Test
    fun `skips days without any data`() {
        val history = calculator.calculate(
            checkIns = listOf(checkIn(today)),
            metrics = emptyList(),
            today = today,
            days = 14,
        )

        assertThat(history).hasSize(1)
    }

    @Test
    fun `rejects an invalid window`() {
        assertThatThrownBy { calculator.calculate(emptyList(), emptyList(), today, days = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { calculator.calculate(emptyList(), emptyList(), today, days = 61) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun checkIn(date: LocalDate, fatigue: Int = 2, illness: Boolean = false) = DailyCheckIn(
        date = date,
        sleepQuality = 4,
        fatigue = fatigue,
        soreness = 1,
        stress = 2,
        illnessOrInjury = illness,
    )
}
