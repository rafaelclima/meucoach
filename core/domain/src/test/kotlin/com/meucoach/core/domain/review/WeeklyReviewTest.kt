package com.meucoach.core.domain.review

import com.meucoach.core.domain.trends.WeeklyIntensity
import com.meucoach.core.model.DataConfidence
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.LocalDate

class WeeklyReviewTest {
    private val weekStart = LocalDate.of(2026, 11, 2)

    @Test
    fun `adherence must fit the planned total`() {
        assertThatThrownBy {
            WeeklyAdherence(planned = 2, done = 3, verified = 0, manual = 0, legacy = 0, skipped = 0)
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            WeeklyAdherence(planned = 2, done = 1, verified = 2, manual = 0, legacy = 0, skipped = 0)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `volume percentage requires both sides`() {
        assertThatThrownBy {
            WeeklyVolume(plannedMin = 100, executedMin = null, percentOfPlan = 100)
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            WeeklyVolume(plannedMin = 0, executedMin = 100, percentOfPlan = null)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `readiness red days must fit the measured days`() {
        assertThatThrownBy {
            WeeklyReadiness(measuredDays = 2, averageScore = 50, redDays = 3)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `sleep must fit one week and a real day`() {
        assertThatThrownBy {
            WeeklySleep(measuredNights = 8, averageMinutes = 400, shortNights = 0)
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            WeeklySleep(measuredNights = 3, averageMinutes = 1_500, shortNights = 0)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `review must cover exactly one Monday to Sunday week`() {
        assertThatThrownBy {
            review(weekEnd = weekStart.plusDays(5))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Monday")
    }

    @Test
    fun `review intensity must belong to the reviewed week`() {
        assertThatThrownBy {
            review(intensity = WeeklyIntensity(weekStart.minusWeeks(1), 600, 600, 600))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("week")
    }

    @Test
    fun `review caps the observations at three`() {
        val observation = WeeklyReviewObservation(
            kind = WeeklyReviewObservationKind.NO_ALERTS,
            ruleId = WeeklyReviewCalculator.RULE_ID,
            evidenceIds = emptyList(),
            confidence = DataConfidence.HIGH,
        )

        assertThatThrownBy {
            review(observations = List(4) { observation })
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("3")
    }

    @Test
    fun `an empty review cannot carry adherence or observations`() {
        val observation = WeeklyReviewObservation(
            kind = WeeklyReviewObservationKind.NO_ALERTS,
            ruleId = WeeklyReviewCalculator.RULE_ID,
            evidenceIds = emptyList(),
            confidence = DataConfidence.HIGH,
        )

        assertThatThrownBy {
            review(status = WeeklyReviewStatus.EMPTY_NO_DATA, adherence = adherence())
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            review(status = WeeklyReviewStatus.EMPTY_NO_DATA, observations = listOf(observation))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun review(
        weekEnd: LocalDate = weekStart.plusDays(6),
        status: WeeklyReviewStatus = WeeklyReviewStatus.AVAILABLE,
        adherence: WeeklyAdherence? = null,
        intensity: WeeklyIntensity? = null,
        observations: List<WeeklyReviewObservation> = emptyList(),
    ) = WeeklyReview(
        weekStart = weekStart,
        weekEnd = weekEnd,
        status = status,
        adherence = adherence,
        volume = null,
        intensity = intensity,
        intensitySessions = if (intensity == null) 0 else 1,
        readiness = null,
        sleep = null,
        observations = observations,
        ruleIds = emptyList(),
        evidenceIds = emptyList(),
        confidence = DataConfidence.LOW,
    )

    private fun adherence() = WeeklyAdherence(
        planned = 1,
        done = 1,
        verified = 1,
        manual = 0,
        legacy = 0,
        skipped = 0,
    )
}
