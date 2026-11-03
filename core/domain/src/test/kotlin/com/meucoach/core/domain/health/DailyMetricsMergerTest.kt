package com.meucoach.core.domain.health

import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DailyMetricsSource
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.LocalDate

class DailyMetricsMergerTest {
    private val day = LocalDate.of(2026, 11, 3)

    @Test
    fun `keeps fields that only the existing source has`() {
        val merged = DailyMetricsMerger.merge(
            existing = metrics(sleep = 450, source = DailyMetricsSource.CSV),
            incoming = metrics(hrv = 60.0, source = DailyMetricsSource.HEALTH_CONNECT),
        )

        assertThat(merged.sleepDurationMin).isEqualTo(450)
        assertThat(merged.hrvRmssdMs).isEqualTo(60.0)
        assertThat(merged.source).isEqualTo(DailyMetricsSource.HEALTH_CONNECT)
    }

    @Test
    fun `health connect updates a field it shares with csv`() {
        val merged = DailyMetricsMerger.merge(
            existing = metrics(sleep = 400, source = DailyMetricsSource.CSV),
            incoming = metrics(sleep = 470, source = DailyMetricsSource.HEALTH_CONNECT),
        )

        assertThat(merged.sleepDurationMin).isEqualTo(470)
        assertThat(merged.source).isEqualTo(DailyMetricsSource.HEALTH_CONNECT)
    }

    @Test
    fun `csv fills gaps but never overwrites health connect`() {
        val merged = DailyMetricsMerger.merge(
            existing = metrics(sleep = 470, source = DailyMetricsSource.HEALTH_CONNECT),
            incoming = metrics(sleep = 400, spo2 = 98.0, source = DailyMetricsSource.CSV),
        )

        assertThat(merged.sleepDurationMin).isEqualTo(470)
        assertThat(merged.spo2Avg).isEqualTo(98.0)
        assertThat(merged.source).isEqualTo(DailyMetricsSource.HEALTH_CONNECT)
    }

    @Test
    fun `same source updates the day`() {
        val merged = DailyMetricsMerger.merge(
            existing = metrics(hrv = 60.0, source = DailyMetricsSource.CSV),
            incoming = metrics(hrv = 65.0, source = DailyMetricsSource.CSV),
        )

        assertThat(merged.hrvRmssdMs).isEqualTo(65.0)
        assertThat(merged.source).isEqualTo(DailyMetricsSource.CSV)
    }

    @Test
    fun `manual values are not overwritten by automated sources`() {
        val merged = DailyMetricsMerger.merge(
            existing = metrics(hrv = 70.0, source = DailyMetricsSource.MANUAL),
            incoming = metrics(hrv = 60.0, sleep = 450, source = DailyMetricsSource.HEALTH_CONNECT),
        )

        assertThat(merged.hrvRmssdMs).isEqualTo(70.0)
        assertThat(merged.sleepDurationMin).isEqualTo(450)
        assertThat(merged.source).isEqualTo(DailyMetricsSource.MANUAL)
    }

    @Test
    fun `manual incoming wins over stored sources`() {
        val merged = DailyMetricsMerger.merge(
            existing = metrics(hrv = 60.0, source = DailyMetricsSource.HEALTH_CONNECT),
            incoming = metrics(hrv = 70.0, source = DailyMetricsSource.MANUAL),
        )

        assertThat(merged.hrvRmssdMs).isEqualTo(70.0)
        assertThat(merged.source).isEqualTo(DailyMetricsSource.MANUAL)
    }

    @Test
    fun `keeps the existing source when the incoming day has no data`() {
        val merged = DailyMetricsMerger.merge(
            existing = metrics(sleep = 450, source = DailyMetricsSource.CSV),
            incoming = metrics(source = DailyMetricsSource.HEALTH_CONNECT),
        )

        assertThat(merged.sleepDurationMin).isEqualTo(450)
        assertThat(merged.source).isEqualTo(DailyMetricsSource.CSV)
    }

    @Test
    fun `rejects days from different dates`() {
        assertThatThrownBy {
            DailyMetricsMerger.merge(
                existing = metrics(date = day, sleep = 450, source = DailyMetricsSource.CSV),
                incoming = metrics(
                    date = day.plusDays(1),
                    hrv = 60.0,
                    source = DailyMetricsSource.HEALTH_CONNECT,
                ),
            )
        }.hasMessageContaining("different dates")
    }

    private fun metrics(
        date: LocalDate = day,
        sleep: Int? = null,
        rhr: Double? = null,
        hrv: Double? = null,
        spo2: Double? = null,
        source: DailyMetricsSource,
    ) = DailyMetrics(
        date = date,
        sleepDurationMin = sleep,
        restingHeartRate = rhr,
        hrvRmssdMs = hrv,
        spo2Avg = spo2,
        source = source,
    )
}
