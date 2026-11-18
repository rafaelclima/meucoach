package com.meucoach.core.domain.coach

import com.meucoach.core.domain.review.WeeklyAdherence
import com.meucoach.core.domain.review.WeeklyQuality
import com.meucoach.core.domain.review.WeeklyReadiness
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewCalculator
import com.meucoach.core.domain.review.WeeklyReviewObservation
import com.meucoach.core.domain.review.WeeklyReviewObservationKind
import com.meucoach.core.domain.review.WeeklyReviewStatus
import com.meucoach.core.domain.review.WeeklySleep
import com.meucoach.core.domain.review.WeeklyVolume
import com.meucoach.core.domain.trends.WeeklyIntensity
import com.meucoach.core.model.DataConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoachReviewPromptBuilderTest {
    private val weekStart = LocalDate.of(2026, 11, 9)

    private fun review(
        status: WeeklyReviewStatus = WeeklyReviewStatus.AVAILABLE,
        adherence: WeeklyAdherence? = WeeklyAdherence(
            planned = 4,
            done = 3,
            verified = 2,
            manual = 1,
            legacy = 0,
            skipped = 1,
        ),
        volume: WeeklyVolume? = WeeklyVolume(
            plannedMin = 120,
            executedMin = 90,
            percentOfPlan = 75,
            extraMin = 10,
        ),
        quality: WeeklyQuality? = WeeklyQuality(planned = 1, kept = 1, downgraded = 0, firstDowngradeDetail = null),
        readiness: WeeklyReadiness? = WeeklyReadiness(measuredDays = 5, averageScore = 71, redDays = 1),
        sleep: WeeklySleep? = WeeklySleep(measuredNights = 6, averageMinutes = 420, shortNights = 3),
        intensity: WeeklyIntensity? = WeeklyIntensity(weekStart, easySec = 4_680, moderateSec = 900, hardSec = 420),
        observations: List<WeeklyReviewObservation> = listOf(
            WeeklyReviewObservation(
                kind = WeeklyReviewObservationKind.SHORT_SLEEP,
                ruleId = "R-READY-04",
                evidenceIds = listOf("EV-WALSH-2021"),
                confidence = DataConfidence.MEDIUM,
            ),
        ),
    ): WeeklyReview = WeeklyReview(
        weekStart = weekStart,
        weekEnd = weekStart.plusDays(6),
        status = status,
        adherence = adherence,
        volume = volume,
        quality = quality,
        intensity = intensity,
        intensitySessions = if (intensity == null) 0 else 3,
        readiness = readiness,
        sleep = sleep,
        observations = observations,
        ruleIds = listOf(WeeklyReviewCalculator.RULE_ID, "R-READY-04"),
        evidenceIds = observations.flatMap { it.evidenceIds }.distinct(),
        confidence = DataConfidence.MEDIUM,
    )

    @Test
    fun `mapeia fatos da semana sem texto livre`() {
        val prompt = CoachReviewPromptBuilder.build(review())

        assertEquals("2026-11-09", prompt.weekStart)
        assertEquals("2026-11-15", prompt.weekEnd)
        assertEquals("AVAILABLE", prompt.status)
        assertEquals(4, prompt.adherence?.planned)
        assertEquals(3, prompt.adherence?.done)
        assertEquals(75, prompt.adherence?.percent)
        assertEquals(120, prompt.volume?.plannedMin)
        assertEquals(90, prompt.volume?.executedMin)
        assertEquals(10, prompt.volume?.extraMin)
        assertEquals(1, prompt.quality?.kept)
        assertEquals(420, prompt.sleep?.averageMinutes)
        assertEquals(78, prompt.intensity?.easyPercent)
        assertEquals(listOf("SHORT_SLEEP"), prompt.observations)
        assertEquals("MEDIUM", prompt.confidence)
        assertTrue(prompt.inputsHash.startsWith(CoachReviewPromptBuilder.SCHEMA))
        assertFalse(prompt.inputsHash.contains("Rafael"))
    }

    @Test
    fun `semana sem dados mantem nulos sem inventar`() {
        val prompt = CoachReviewPromptBuilder.build(
            review(
                status = WeeklyReviewStatus.EMPTY_NO_DATA,
                adherence = null,
                volume = null,
                quality = null,
                readiness = null,
                sleep = null,
                intensity = null,
                observations = emptyList(),
            ),
        )

        assertEquals("EMPTY_NO_DATA", prompt.status)
        assertNull(prompt.adherence)
        assertNull(prompt.volume)
        assertNull(prompt.quality)
        assertNull(prompt.sleep)
        assertNull(prompt.intensity)
        assertTrue(prompt.observations.isEmpty())
        assertTrue(CoachReviewPromptBuilder.allowedMinutes(prompt).isEmpty())
        assertTrue(CoachReviewPromptBuilder.allowedPercents(prompt).isEmpty())
    }

    @Test
    fun `permite apenas minutos e porcentagens calculados`() {
        val prompt = CoachReviewPromptBuilder.build(review())
        val minutes = CoachReviewPromptBuilder.allowedMinutes(prompt)
        val percents = CoachReviewPromptBuilder.allowedPercents(prompt)

        assertTrue(minutes.containsAll(setOf(120, 90, 10, 420, WeeklyReviewCalculator.SHORT_SLEEP_MINUTES)))
        assertFalse(minutes.contains(60))
        assertEquals(setOf(75, 78), percents)
    }

    @Test
    fun `hash muda quando um fato muda`() {
        val first = CoachReviewPromptBuilder.build(review())
        val second = CoachReviewPromptBuilder.build(
            review(
                adherence = WeeklyAdherence(
                    planned = 4,
                    done = 4,
                    verified = 3,
                    manual = 1,
                    legacy = 0,
                    skipped = 0,
                ),
            ),
        )

        assertNotNull(first.inputsHash)
        assertTrue(first.inputsHash != second.inputsHash)
    }
}
