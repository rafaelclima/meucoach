package com.meucoach.core.domain.trends

import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.DataConfidence
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.LocalDate

class IntensityDistributionCalculatorTest {
    private val calculator = IntensityDistributionCalculator()
    private val today = LocalDate.of(2026, 10, 30)
    private val lthr = 160

    @Test
    fun `splits time into easy moderate and hard buckets`() {
        val session = IntensitySession(
            date = today.minusDays(1),
            samples = samples(
                hr(0, 120) to 60,
                hr(60, 140) to 60,
                hr(120, 148) to 60,
                hr(180, 155) to 60,
            ),
        )

        val distribution = requireNotNull(calculator.calculate(listOf(session), today, lthr))

        assertThat(distribution.easySec).isEqualTo(120)
        assertThat(distribution.moderateSec).isEqualTo(60)
        assertThat(distribution.hardSec).isEqualTo(60)
        assertThat(distribution.totalSec).isEqualTo(240)
        assertThat(distribution.easyPercent()).isEqualTo(50.0)
        assertThat(distribution.moderatePercent()).isEqualTo(25.0)
        assertThat(distribution.hardPercent()).isEqualTo(25.0)
        assertThat(distribution.qualityPercent()).isEqualTo(50.0)
        assertThat(distribution.sessions).isEqualTo(1)
        assertThat(distribution.windowDays).isEqualTo(28)
    }

    @Test
    fun `ignores intervals without heart rate and sessions outside the window`() {
        val inside = IntensitySession(
            date = today.minusDays(1),
            samples = listOf(
                hr(0, 140),
                ActivitySample(offsetSec = 60),
                hr(120, 140),
            ),
        )
        val outside = IntensitySession(
            date = today.minusDays(40),
            samples = samples(hr(0, 140) to 600),
        )

        val distribution = requireNotNull(calculator.calculate(listOf(inside, outside), today, lthr))

        assertThat(distribution.easySec).isEqualTo(60)
        assertThat(distribution.moderateSec).isZero()
        assertThat(distribution.hardSec).isZero()
        assertThat(distribution.sessions).isEqualTo(1)
    }

    @Test
    fun `returns null without samples or measurable heart rate`() {
        val single = IntensitySession(today.minusDays(1), listOf(hr(0, 140)))
        val withoutHeartRate = IntensitySession(
            today.minusDays(2),
            listOf(hr(0, null), hr(60, null)),
        )

        assertThat(calculator.calculate(emptyList(), today, lthr)).isNull()
        assertThat(calculator.calculate(listOf(single), today, lthr)).isNull()
        assertThat(calculator.calculate(listOf(withoutHeartRate), today, lthr)).isNull()
    }

    @Test
    fun `groups measured time by week and skips empty weeks`() {
        val early = IntensitySession(today.minusDays(24), samples(hr(0, 140) to 600))
        val middle = IntensitySession(today.minusDays(9), samples(hr(0, 140) to 900))
        val latest = IntensitySession(today.minusDays(1), samples(hr(0, 140) to 300))

        val distribution = requireNotNull(calculator.calculate(listOf(latest, early, middle), today, lthr))

        assertThat(distribution.weeks).hasSize(3)
        assertThat(distribution.weeks.map { it.weekStart }).isSorted()
        assertThat(distribution.weeks.map { it.weekStart }).containsExactly(
            LocalDate.of(2026, 10, 5),
            LocalDate.of(2026, 10, 19),
            LocalDate.of(2026, 10, 26),
        )
        assertThat(distribution.weeks.map { it.easySec }).containsExactly(600, 900, 300)
        assertThat(distribution.weeks.map { it.easyPercent() }).containsOnly(100.0)
        assertThat(distribution.weeks.map { it.qualityPercent() }).containsOnly(0.0)
    }

    @Test
    fun `starts confidence low and raises it with coverage`() {
        fun session(daysAgo: Long) = IntensitySession(
            today.minusDays(daysAgo),
            samples(hr(0, 140) to 600),
        )

        val one = requireNotNull(calculator.calculate(listOf(session(1)), today, lthr))
        val three = requireNotNull(
            calculator.calculate(listOf(session(1), session(3), session(5)), today, lthr),
        )
        val six = requireNotNull(
            calculator.calculate((1L..6L).map(::session), today, lthr),
        )

        assertThat(one.confidence).isEqualTo(DataConfidence.LOW)
        assertThat(three.confidence).isEqualTo(DataConfidence.MEDIUM)
        assertThat(six.confidence).isEqualTo(DataConfidence.HIGH)
        assertThat(six.sessions).isEqualTo(6)
        assertThat(six.easySec).isEqualTo(3_600)
        assertThat(six.weeks.sumOf { it.easySec }).isEqualTo(3_600)
    }

    @Test
    fun `rejects a window outside the supported range`() {
        assertThatThrownBy { calculator.calculate(emptyList(), today, lthr, windowDays = 6) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { calculator.calculate(emptyList(), today, lthr, windowDays = 91) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `marks the easy target only at eighty percent or more`() {
        val atTarget = distribution(easySec = 800, moderateSec = 150, hardSec = 50)
        val belowTarget = distribution(easySec = 790, moderateSec = 160, hardSec = 50)

        assertThat(atTarget.easyPercent()).isEqualTo(80.0)
        assertThat(atTarget.meetsEasyTarget).isTrue()
        assertThat(belowTarget.meetsEasyTarget).isFalse()
    }

    @Test
    fun `rejects invalid aggregate values`() {
        assertThatThrownBy { WeeklyIntensity(today, -1, 0, 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { WeeklyIntensity(today, 0, 0, 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        val valid = distribution(easySec = 600, moderateSec = 300, hardSec = 100)
        assertThatThrownBy { valid.copy(windowDays = 6) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { valid.copy(sessions = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { valid.copy(weeks = emptyList()) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { distribution(easySec = 0, moderateSec = 0, hardSec = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun distribution(easySec: Int, moderateSec: Int, hardSec: Int): IntensityDistribution {
        val weekly = WeeklyIntensity(
            weekStart = today.minusDays(4),
            easySec = easySec,
            moderateSec = moderateSec,
            hardSec = hardSec,
        )
        return IntensityDistribution(
            windowDays = 28,
            easySec = easySec,
            moderateSec = moderateSec,
            hardSec = hardSec,
            sessions = 1,
            weeks = listOf(weekly),
            confidence = DataConfidence.LOW,
        )
    }

    /**
     * Builds consecutive samples where each earlier sample keeps its heart rate
     * for the given number of seconds.
     */
    private fun samples(vararg pairs: Pair<ActivitySample, Int>): List<ActivitySample> {
        var offset = 0
        val result = mutableListOf<ActivitySample>()
        pairs.forEach { (sample, seconds) ->
            result += sample.copy(offsetSec = offset)
            offset += seconds
        }
        result += ActivitySample(offsetSec = offset)
        return result
    }

    private fun hr(offsetSec: Int, heartRate: Int?): ActivitySample =
        ActivitySample(offsetSec = offsetSec, heartRate = heartRate)
}
