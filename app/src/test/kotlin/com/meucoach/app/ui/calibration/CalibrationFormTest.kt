package com.meucoach.app.ui.calibration

import com.meucoach.app.ui.common.TargetTimeParts
import com.meucoach.app.ui.common.TargetTimeUnit
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.Distance
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CalibrationFormTest {
    private val today = LocalDate.of(2026, 10, 30)

    @Test
    fun `accepts a valid race`() {
        val form = CalibrationForm(
            distanceKm = "5",
            timeParts = TargetTimeParts(minutes = "30", seconds = "59"),
            date = "24102026",
            averageHeartRate = "181",
        )

        assertThat(CalibrationFormValidator.validate(form, today).isValid).isTrue()
        val performance = CalibrationFormValidator.toPerformance(form, today)
        assertThat(performance.distanceM).isEqualTo(5_000.0)
        assertThat(performance.durationSec).isEqualTo(1_859)
        assertThat(performance.averageHeartRate).isEqualTo(181)
    }

    @Test
    fun `accepts comma decimals and long efforts`() {
        val form = CalibrationForm(
            distanceKm = "21,1",
            timeParts = TargetTimeParts(hours = "1", minutes = "30", seconds = "00"),
            timeUnit = TargetTimeUnit.HOURS,
            date = "24102026",
        )

        val performance = CalibrationFormValidator.toPerformance(form, today)

        assertThat(performance.distanceM).isEqualTo(21_100.0)
        assertThat(performance.durationSec).isEqualTo(5_400)
        assertThat(performance.averageHeartRate).isNull()
    }

    @Test
    fun `rejects invalid distance, time, date and heart rate`() {
        val base = CalibrationForm(
            distanceKm = "5",
            timeParts = TargetTimeParts(minutes = "30", seconds = "59"),
            date = "24102026",
        )

        assertThat(CalibrationFormValidator.validate(base.copy(distanceKm = "0"), today).distance).isNotNull()
        assertThat(CalibrationFormValidator.validate(base.copy(distanceKm = "101"), today).distance).isNotNull()
        assertThat(CalibrationFormValidator.validate(base.copy(distanceKm = "abc"), today).distance).isNotNull()
        assertThat(
            CalibrationFormValidator.validate(
                base.copy(timeParts = TargetTimeParts(minutes = "10", seconds = "99")),
                today,
            ).time,
        ).isNotNull()
        assertThat(
            CalibrationFormValidator.validate(
                base.copy(timeParts = TargetTimeParts(minutes = "5", seconds = "00")),
                today,
            ).time,
        ).isNotNull()
        assertThat(
            CalibrationFormValidator.validate(
                base.copy(
                    timeParts = TargetTimeParts(hours = "4", minutes = "00", seconds = "00"),
                    timeUnit = TargetTimeUnit.HOURS,
                ),
                today,
            ).time,
        ).isNotNull()
        assertThat(
            CalibrationFormValidator.validate(
                base.copy(timeParts = TargetTimeParts(minutes = "0", seconds = "00")),
                today,
            ).time,
        ).isNotNull()
        assertThat(
            CalibrationFormValidator.validate(base.copy(date = "31/02/2026"), today).date,
        ).isNotNull()
        assertThat(
            CalibrationFormValidator.validate(base.copy(date = "31/10/2026"), today).date,
        ).isNotNull()
        assertThat(
            CalibrationFormValidator.validate(base.copy(averageHeartRate = "20"), today).heartRate,
        ).isNotNull()
        assertThat(
            CalibrationFormValidator.validate(base.copy(averageHeartRate = "300"), today).heartRate,
        ).isNotNull()
    }

    @Test
    fun `requires all mandatory fields`() {
        val errors = CalibrationFormValidator.validate(CalibrationForm(), today)

        assertThat(errors.isValid).isFalse()
        assertThat(errors.distance).isNull() // 5 km is the default
        assertThat(errors.time).isNotNull()
        assertThat(errors.date).isNotNull()
    }

    @Test
    fun `refuses to build a performance from an invalid form`() {
        assertThatThrownBy {
            CalibrationFormValidator.toPerformance(CalibrationForm(), today)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `formats effort times`() {
        assertThat(CalibrationFormValidator.formatTime(1_859)).isEqualTo("30:59")
        assertThat(CalibrationFormValidator.formatTime(5_400)).isEqualTo("1:30:00")
    }

    @Test
    fun `counts recent efforts with heart rate and pace`() {
        val activities = listOf(
            activity(daysAgo = 10, heartRate = 150, pace = 360),
            activity(daysAgo = 20, heartRate = 155, pace = 350),
            activity(daysAgo = 30, heartRate = null, pace = 350),
            activity(daysAgo = 40, heartRate = 150, pace = null),
            activity(daysAgo = 80, heartRate = 150, pace = 360),
            activity(daysAgo = -1, heartRate = 150, pace = 360),
        )

        assertThat(recentEffortCount(activities, ZoneId.of("America/Sao_Paulo"), today)).isEqualTo(2)
    }

    private fun activity(daysAgo: Int, heartRate: Int?, pace: Int?) = ActivitySummary(
        id = "activity-$daysAgo-$heartRate-$pace",
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = today.minusDays(daysAgo.toLong()).atTime(8, 0).atZone(ZoneId.of("America/Sao_Paulo")).toInstant(),
        durationSec = 1_800,
        distance = Distance(5_000.0),
        averageHeartRate = heartRate,
        averagePace = pace?.let { Pace.fromSecondsPerKm(it) },
    )
}
