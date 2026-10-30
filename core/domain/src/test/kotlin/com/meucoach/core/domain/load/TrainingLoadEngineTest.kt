package com.meucoach.core.domain.load

import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test
import java.time.LocalDate

class TrainingLoadEngineTest {
    private val today = LocalDate.of(2026, 10, 30)
    private val engine = TrainingLoadEngine()

    @Test
    fun `builds a daily series with rTSS and TRIMP fallback`() {
        val snapshot = engine.calculate(
            activities = listOf(
                LoadActivity(
                    date = today.minusDays(1),
                    durationSec = 3_600,
                    averagePace = Pace.fromSecondsPerKm(360),
                ),
                LoadActivity(
                    date = today.minusDays(2),
                    durationSec = 1_800,
                    averageHeartRate = 150,
                ),
                LoadActivity(date = today.minusDays(3), durationSec = 1_800),
            ),
            profile = profile(),
            today = today,
            days = 42,
        )

        assertThat(snapshot.daily).hasSize(42)
        val first = snapshot.daily.first { it.date == today.minusDays(1) }
        val second = snapshot.daily.first { it.date == today.minusDays(2) }
        val third = snapshot.daily.first { it.date == today.minusDays(3) }
        assertThat(first.metric).isEqualTo(LoadMetric.RTSS)
        assertThat(first.load).isCloseTo(100.0, within(1e-6))
        assertThat(second.metric).isEqualTo(LoadMetric.TRIMP)
        assertThat(second.load).isGreaterThan(0.0)
        assertThat(third.metric).isNull()
        assertThat(snapshot.missingDays).isEqualTo(40)
        assertThat(snapshot.hasData).isTrue()
    }

    @Test
    fun `prefers TRIMP when heart rate is reliable`() {
        val snapshot = engine.calculate(
            activities = listOf(
                LoadActivity(
                    date = today,
                    durationSec = 3_600,
                    averageHeartRate = 150,
                    averagePace = Pace.fromSecondsPerKm(360),
                ),
            ),
            profile = profile(),
            today = today,
            days = 28,
        )

        val todayLoad = snapshot.daily.last()
        assertThat(todayLoad.metric).isEqualTo(LoadMetric.TRIMP)
        assertThat(todayLoad.load).isGreaterThan(100.0)
    }

    @Test
    fun `uses rTSS when heart rate is missing`() {
        val snapshot = engine.calculate(
            activities = listOf(
                LoadActivity(
                    date = today,
                    durationSec = 3_600,
                    averagePace = Pace.fromSecondsPerKm(360),
                ),
            ),
            profile = profile(),
            today = today,
            days = 28,
        )

        assertThat(snapshot.daily.last().metric).isEqualTo(LoadMetric.RTSS)
        assertThat(snapshot.daily.last().load).isCloseTo(100.0, within(1e-6))
    }

    @Test
    fun `matches the calculator for CTL ATL and TSB`() {
        val snapshot = engine.calculate(
            activities = listOf(
                LoadActivity(date = today.minusDays(1), durationSec = 3_600, averagePace = Pace.fromSecondsPerKm(360)),
            ),
            profile = profile(),
            today = today,
            days = 42,
        )

        val loads = snapshot.daily.map { it.load }
        assertThat(snapshot.ctl).isCloseTo(TrainingLoadCalculator.ctl(loads).last(), within(1e-9))
        assertThat(snapshot.atl).isCloseTo(TrainingLoadCalculator.atl(loads).last(), within(1e-9))
        assertThat(snapshot.tsb).isCloseTo(snapshot.ctl - snapshot.atl, within(1e-9))
        assertThat(snapshot.acwr).isNotNull
        assertThat(snapshot.monotony).isNotNull
    }

    @Test
    fun `returns no ACWR or monotony without load data`() {
        val snapshot = engine.calculate(
            activities = emptyList(),
            profile = profile(),
            today = today,
            days = 28,
        )

        assertThat(snapshot.hasData).isFalse()
        assertThat(snapshot.acwr).isNull()
        assertThat(snapshot.monotony).isNull()
        assertThat(snapshot.missingDays).isEqualTo(28)
        assertThat(snapshot.daily).allSatisfy { assertThat(it.load).isZero() }
    }

    @Test
    fun `ignores activities outside the requested window`() {
        val snapshot = engine.calculate(
            activities = listOf(
                LoadActivity(date = today.minusDays(40), durationSec = 3_600, averagePace = Pace.fromSecondsPerKm(360)),
            ),
            profile = profile(),
            today = today,
            days = 28,
        )

        assertThat(snapshot.hasData).isFalse()
    }

    @Test
    fun `requires at least 28 days`() {
        org.assertj.core.api.Assertions.assertThatThrownBy {
            engine.calculate(emptyList(), profile(), today, days = 27)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        thresholdPace = Pace.fromSecondsPerKm(360),
        timezone = "America/Sao_Paulo",
    )
}
