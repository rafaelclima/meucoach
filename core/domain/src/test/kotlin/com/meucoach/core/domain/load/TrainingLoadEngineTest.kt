package com.meucoach.core.domain.load

import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test
import java.time.Instant
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
            sourceStatus = source(),
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
            sourceStatus = source(),
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
            sourceStatus = source(),
            days = 28,
        )

        assertThat(snapshot.daily.last().metric).isEqualTo(LoadMetric.RTSS)
        assertThat(snapshot.daily.last().load).isCloseTo(100.0, within(1e-6))
    }

    @Test
    fun `matches the calculator when there is no pre-window history`() {
        val snapshot = engine.calculate(
            activities = (0 until 8).map { index ->
                LoadActivity(
                    date = today.minusDays(index.toLong()),
                    durationSec = 1_800 + index * 300,
                    averagePace = Pace.fromSecondsPerKm(360 + index * 10),
                )
            },
            profile = profile(),
            today = today,
            sourceStatus = source(),
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
    fun `uses pre-window history as warm-up for CTL and ATL`() {
        val snapshot = engine.calculate(
            activities = constantLoadDays(days = 200),
            profile = profile(),
            today = today,
            sourceStatus = source(),
            days = 42,
        )

        assertThat(snapshot.daily).hasSize(42)
        assertThat(snapshot.ctlSeries).hasSize(42)
        assertThat(snapshot.atlSeries).hasSize(42)
        // 200 days of 100 points/day converge to the real value instead of 63% of it.
        assertThat(snapshot.ctl).isGreaterThan(99.0)
        assertThat(snapshot.atl).isCloseTo(100.0, within(0.1))
        assertThat(snapshot.tsb).isGreaterThan(-2.0)
    }

    @Test
    fun `does not invent past load when there is no history before the window`() {
        val snapshot = engine.calculate(
            activities = constantLoadDays(days = 42),
            profile = profile(),
            today = today,
            sourceStatus = source(),
            days = 42,
        )

        // Honest convergence from zero: no pre-window activity exists to seed the EMA.
        assertThat(snapshot.ctl).isCloseTo(63.65, within(0.1))
    }

    @Test
    fun `lets the warm-up decay when the display window has no load`() {
        val snapshot = engine.calculate(
            activities = (42..241).map { daysAgo -> constantLoadDay(daysAgo.toLong()) },
            profile = profile(),
            today = today,
            sourceStatus = source(),
            days = 42,
        )

        assertThat(snapshot.hasData).isFalse()
        // The 42-day break decays the warm-up; a zeroed window would show 0.
        assertThat(snapshot.ctl).isGreaterThan(35.0)
        assertThat(snapshot.ctl).isLessThan(37.0)
    }

    @Test
    fun `keeps warm-up activities out of coverage and lastActivityAt`() {
        val snapshot = engine.calculate(
            activities = listOf(
                LoadActivity(
                    date = today.minusDays(100),
                    durationSec = 3_600,
                    averagePace = Pace.fromSecondsPerKm(360),
                    startTime = Instant.parse("2026-07-22T09:00:00Z"),
                ),
            ),
            profile = profile(),
            today = today,
            sourceStatus = source(fresh = false),
            days = 28,
        )

        assertThat(snapshot.coverage.lastActivityAt).isNull()
        assertThat(snapshot.coverage.daysWithLoad).isZero()
        assertThat(snapshot.dataState).isEqualTo(LoadDataState.INSUFFICIENT)
    }

    @Test
    fun `returns no ACWR or monotony without load data`() {
        val snapshot = engine.calculate(
            activities = emptyList(),
            profile = profile(),
            today = today,
            sourceStatus = source(),
            days = 28,
        )

        assertThat(snapshot.hasData).isFalse()
        assertThat(snapshot.acwr).isNull()
        assertThat(snapshot.monotony).isNull()
        assertThat(snapshot.missingDays).isEqualTo(28)
        assertThat(snapshot.daily).allSatisfy { assertThat(it.load).isZero() }
        assertThat(snapshot.dataState).isEqualTo(LoadDataState.INSUFFICIENT)
        assertThat(snapshot.coverage.daysWithLoad).isZero()
        assertThat(snapshot.coverage.sourceFresh).isTrue()
    }

    @Test
    fun `exposes known load with eight days and a fresh source`() {
        val snapshot = engine.calculate(
            activities = loadDays(count = 8),
            profile = profile(),
            today = today,
            sourceStatus = source(),
            days = 28,
        )

        assertThat(snapshot.dataState).isEqualTo(LoadDataState.KNOWN)
        assertThat(snapshot.acwr).isNotNull()
        assertThat(snapshot.coverage.daysWithLoad).isEqualTo(8)
        assertThat(snapshot.coverage.daysWithoutActivity).isEqualTo(20)
        assertThat(snapshot.coverage.daysWithoutData).isZero()
        assertThat(snapshot.coverage.sourceFresh).isTrue()
    }

    @Test
    fun `hides ACWR and marks the source stale when imports are old`() {
        val snapshot = engine.calculate(
            activities = loadDays(count = 8),
            profile = profile(),
            today = today,
            sourceStatus = source(fresh = false),
            days = 28,
        )

        assertThat(snapshot.dataState).isEqualTo(LoadDataState.STALE)
        assertThat(snapshot.acwr).isNull()
        assertThat(snapshot.coverage.daysWithLoad).isEqualTo(8)
        assertThat(snapshot.coverage.daysWithoutActivity).isZero()
        assertThat(snapshot.coverage.daysWithoutData).isEqualTo(20)
        assertThat(snapshot.coverage.sourceFresh).isFalse()
    }

    @Test
    fun `keeps ACWR near one with a constant weekly load`() {
        val snapshot = engine.calculate(
            activities = listOf(1L, 4L, 8L, 11L, 15L, 18L, 22L, 25L).map { daysAgo ->
                LoadActivity(
                    date = today.minusDays(daysAgo),
                    durationSec = 3_600,
                    averagePace = Pace.fromSecondsPerKm(360),
                )
            },
            profile = profile(),
            today = today,
            sourceStatus = source(),
            days = 28,
        )

        assertThat(snapshot.dataState).isEqualTo(LoadDataState.KNOWN)
        assertThat(snapshot.acwr!!).isCloseTo(1.0, within(0.2))
        assertThat(snapshot.monotony).isNotNull
    }

    @Test
    fun `requires eight days of load before exposing ACWR`() {
        val snapshot = engine.calculate(
            activities = loadDays(count = 7),
            profile = profile(),
            today = today,
            sourceStatus = source(),
            days = 28,
        )

        assertThat(snapshot.dataState).isEqualTo(LoadDataState.INSUFFICIENT)
        assertThat(snapshot.acwr).isNull()
        assertThat(snapshot.coverage.daysWithLoad).isEqualTo(7)
        assertThat(snapshot.coverage.daysWithoutActivity).isEqualTo(21)
    }

    @Test
    fun `counts an activity without a usable metric as missing data`() {
        val snapshot = engine.calculate(
            activities = listOf(
                LoadActivity(date = today.minusDays(1), durationSec = 1_800),
            ),
            profile = profile().copy(thresholdPace = null),
            today = today,
            sourceStatus = source(),
            days = 28,
        )

        assertThat(snapshot.coverage.daysWithLoad).isZero()
        assertThat(snapshot.coverage.daysWithoutActivity).isEqualTo(27)
        assertThat(snapshot.coverage.daysWithoutData).isEqualTo(1)
        assertThat(snapshot.dataState).isEqualTo(LoadDataState.INSUFFICIENT)
    }

    @Test
    fun `reports the last activity instant in the coverage window`() {
        val lastStart = Instant.parse("2026-10-29T09:30:00Z")
        val snapshot = engine.calculate(
            activities = listOf(
                LoadActivity(
                    date = today.minusDays(1),
                    durationSec = 1_800,
                    averagePace = Pace.fromSecondsPerKm(360),
                    startTime = lastStart,
                ),
            ),
            profile = profile(),
            today = today,
            sourceStatus = source(),
            days = 28,
        )

        assertThat(snapshot.coverage.lastActivityAt).isEqualTo(lastStart)
    }

    @Test
    fun `ignores activities before the warm-up window`() {
        val snapshot = engine.calculate(
            activities = listOf(
                LoadActivity(
                    date = today.minusDays(300),
                    durationSec = 3_600,
                    averagePace = Pace.fromSecondsPerKm(360),
                ),
            ),
            profile = profile(),
            today = today,
            sourceStatus = source(),
            days = 28,
        )

        assertThat(snapshot.hasData).isFalse()
        assertThat(snapshot.ctl).isZero()
    }

    @Test
    fun `requires at least 28 days`() {
        org.assertj.core.api.Assertions.assertThatThrownBy {
            engine.calculate(emptyList(), profile(), today, source(), days = 27)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun loadDays(count: Int): List<LoadActivity> = (0 until count).map { index ->
        LoadActivity(
            date = today.minusDays(index.toLong()),
            durationSec = 1_800 + index * 120,
            averagePace = Pace.fromSecondsPerKm(360 + index * 10),
            startTime = Instant.parse("2026-10-30T09:00:00Z").minusSeconds(index * 86_400L),
        )
    }

    private fun constantLoadDays(days: Int): List<LoadActivity> =
        (0 until days).map { index -> constantLoadDay(index.toLong()) }

    private fun constantLoadDay(daysAgo: Long) = LoadActivity(
        date = today.minusDays(daysAgo),
        durationSec = 3_600,
        averagePace = Pace.fromSecondsPerKm(360),
    )

    private fun source(fresh: Boolean = true) = ActivitySourceStatus(
        lastImportedAt = if (fresh) {
            Instant.parse("2026-10-29T10:00:00Z")
        } else {
            Instant.parse("2026-09-01T10:00:00Z")
        },
        checkedAt = Instant.parse("2026-10-30T10:00:00Z"),
    )

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
