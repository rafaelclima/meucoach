package com.meucoach.app.ui.activitydetail

import com.meucoach.core.model.ActivitySample
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class ActivityChartDataTest {
    @Test
    fun `derives pace from speed and keeps heart rate`() {
        val data = chartData(
            listOf(
                sample(offset = 0, speed = 2.5, heartRate = 140),
                sample(offset = 10, speed = 3.0, heartRate = 150),
            ),
        )

        assertThat(data).isNotNull
        assertThat(data!!.points).hasSize(2)
        assertThat(data.points[0].paceSecPerKm).isEqualTo(400)
        assertThat(data.points[1].paceSecPerKm).isEqualTo(333)
        assertThat(data.points.map { it.heartRate }).containsExactly(140, 150)
        assertThat(data.paceRange).isEqualTo(333..400)
        assertThat(data.heartRateRange).isEqualTo(140..150)
        assertThat(data.durationSec).isEqualTo(10)
    }

    @Test
    fun `ignores samples without a usable speed`() {
        val data = chartData(
            listOf(
                sample(offset = 0, speed = 2.5),
                sample(offset = 10, speed = null),
                sample(offset = 20, speed = 0.0),
                sample(offset = 30, speed = 3.0),
            ),
        )

        assertThat(data!!.points.map { it.offsetSec }).containsExactly(0, 30)
    }

    @Test
    fun `returns null when fewer than two usable samples remain`() {
        assertThat(chartData(emptyList())).isNull()
        assertThat(chartData(listOf(sample(offset = 0, speed = 2.5)))).isNull()
        assertThat(chartData(listOf(sample(offset = 0, speed = 0.0), sample(offset = 1, speed = 0.0)))).isNull()
    }

    @Test
    fun `hides the heart rate range when fewer than two values exist`() {
        val data = chartData(
            listOf(
                sample(offset = 0, speed = 2.5, heartRate = 150),
                sample(offset = 10, speed = 3.0),
            ),
        )

        assertThat(data!!.heartRateRange).isNull()
    }

    @Test
    fun `downsamples long series but keeps the last sample`() {
        val samples = List(1_000) { index ->
            sample(offset = index, speed = 2.5 + index % 5 * 0.1)
        }

        val data = chartData(samples, maxPoints = 100)!!

        assertThat(data.points.size).isLessThanOrEqualTo(101)
        assertThat(data.points.first().offsetSec).isZero()
        assertThat(data.points.last().offsetSec).isEqualTo(999)
    }

    @Test
    fun `requires a valid point limit`() {
        assertThatThrownBy {
            chartData(listOf(sample(offset = 0, speed = 2.5), sample(offset = 1, speed = 2.5)), maxPoints = 1)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun sample(offset: Int, speed: Double?, heartRate: Int? = null) = ActivitySample(
        offsetSec = offset,
        speedMps = speed,
        heartRate = heartRate,
    )
}
