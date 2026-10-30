package com.meucoach.core.domain.importer

import com.meucoach.core.model.DailyMetricsSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.LocalDate

class DailyMetricsCsvParserTest {
    private val parser = DailyMetricsCsvParser()

    @Test
    fun `parses the full template`() {
        val result = parser.parse(
            """
            date,sleep_minutes,resting_heart_rate,hrv_rmssd,spo2
            2026-10-28,450,58,60,97
            2026-10-29,420,,55.5,
            """.trimIndent(),
        )

        assertThat(result.metrics).hasSize(2)
        assertThat(result.errors).isEmpty()
        val first = result.metrics.first()
        assertThat(first.date).isEqualTo(LocalDate.of(2026, 10, 28))
        assertThat(first.sleepDurationMin).isEqualTo(450)
        assertThat(first.restingHeartRate).isEqualTo(58.0)
        assertThat(first.hrvRmssdMs).isEqualTo(60.0)
        assertThat(first.spo2Avg).isEqualTo(97.0)
        assertThat(first.source).isEqualTo(DailyMetricsSource.CSV)
        assertThat(result.metrics.last().restingHeartRate).isNull()
        assertThat(result.metrics.last().spo2Avg).isNull()
    }

    @Test
    fun `accepts Brazilian dates, aliases and a BOM`() {
        val result = parser.parse(
            "\uFEFFData,Sono,FC_Repouso,HRV,SpO2\n24/10/2026,420,57,58,96\n",
        )

        assertThat(result.metrics.single().date).isEqualTo(LocalDate.of(2026, 10, 24))
        assertThat(result.metrics.single().sleepDurationMin).isEqualTo(420)
    }

    @Test
    fun `keeps valid rows and reports invalid ones`() {
        val result = parser.parse(
            """
            date,sleep_minutes,resting_heart_rate
            2026-10-28,450,58
            not-a-date,400,60
            2026-10-29,abc,60
            2026-10-30,480,999
            """.trimIndent(),
        )

        assertThat(result.metrics).hasSize(1)
        assertThat(result.errors).hasSize(3)
        assertThat(result.errors.map { it.line }).containsExactly(3, 4, 5)
    }

    @Test
    fun `keeps the last row for a duplicated date`() {
        val result = parser.parse(
            """
            date,sleep_minutes
            2026-10-28,450
            2026-10-28,480
            """.trimIndent(),
        )

        assertThat(result.metrics).hasSize(1)
        assertThat(result.metrics.single().sleepDurationMin).isEqualTo(480)
        assertThat(result.duplicateDates).isEqualTo(1)
    }

    @Test
    fun `requires a date column`() {
        assertThatThrownBy { parser.parse("sleep_minutes,rhr\n450,58") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("data")
    }

    @Test
    fun `rejects an empty file`() {
        assertThatThrownBy { parser.parse(" \n") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
