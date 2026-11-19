package com.meucoach.core.domain.coach

import com.meucoach.core.domain.review.WeeklyAdherence
import com.meucoach.core.domain.review.WeeklyReadiness
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewObservation
import com.meucoach.core.domain.review.WeeklyReviewStatus
import com.meucoach.core.domain.review.WeeklySleep
import com.meucoach.core.domain.review.WeeklyVolume
import com.meucoach.core.model.DataConfidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class CoachProgressTest {
    @Test
    fun `soma adesao e volume com o extra fora da adesao`() {
        val summary = CoachProgress.summarize(
            listOf(
                week(
                    adherence = WeeklyAdherence(3, 3, 3, 0, 0, 0),
                    volume = WeeklyVolume(114, 129, 113, 28),
                ),
            ),
            easyPercent28d = 46,
        )

        assertThat(summary.availableWeeks).isEqualTo(1)
        assertThat(summary.plannedSessions).isEqualTo(3)
        assertThat(summary.doneSessions).isEqualTo(3)
        assertThat(summary.adherencePercent).isEqualTo(100)
        assertThat(summary.plannedMinutes).isEqualTo(114)
        assertThat(summary.executedMinutes).isEqualTo(129)
        assertThat(summary.extraMinutes).isEqualTo(28)
        assertThat(summary.volumePercent).isEqualTo(113)
        assertThat(summary.easyPercent28d).isEqualTo(46)
    }

    @Test
    fun `semana vazia nao entra nas medias`() {
        val summary = CoachProgress.summarize(
            listOf(
                week(
                    adherence = WeeklyAdherence(4, 2, 2, 0, 0, 0),
                    volume = WeeklyVolume(120, 60, 50, null),
                    readiness = WeeklyReadiness(7, 60, 0),
                    sleep = WeeklySleep(6, 330, 1),
                ),
                emptyWeek(LocalDate.of(2026, 11, 2)),
            ),
            easyPercent28d = null,
        )

        assertThat(summary.availableWeeks).isEqualTo(1)
        assertThat(summary.adherencePercent).isEqualTo(50)
        assertThat(summary.avgReadiness).isEqualTo(60)
        assertThat(summary.avgSleepMinutes).isEqualTo(330)
        assertThat(summary.shortNights).isEqualTo(1)
        assertThat(summary.easyPercent28d).isNull()
    }

    @Test
    fun `sem semana com dados tudo e nulo`() {
        val summary = CoachProgress.summarize(
            listOf(emptyWeek(LocalDate.of(2026, 11, 9))),
            easyPercent28d = null,
        )

        assertThat(summary.availableWeeks).isEqualTo(0)
        assertThat(summary.adherencePercent).isNull()
        assertThat(summary.volumePercent).isNull()
        assertThat(summary.avgReadiness).isNull()
        assertThat(summary.avgSleepMinutes).isNull()
    }

    private fun week(
        adherence: WeeklyAdherence,
        volume: WeeklyVolume,
        readiness: WeeklyReadiness? = null,
        sleep: WeeklySleep? = null,
    ) = WeeklyReview(
        weekStart = LocalDate.of(2026, 11, 9),
        weekEnd = LocalDate.of(2026, 11, 15),
        status = WeeklyReviewStatus.AVAILABLE,
        adherence = adherence,
        volume = volume,
        quality = null,
        intensity = null,
        intensitySessions = 0,
        readiness = readiness,
        sleep = sleep,
        observations = emptyList<WeeklyReviewObservation>(),
        ruleIds = emptyList(),
        evidenceIds = emptyList(),
        confidence = DataConfidence.MEDIUM,
    )

    private fun emptyWeek(weekStart: LocalDate) = WeeklyReview(
        weekStart = weekStart,
        weekEnd = weekStart.plusDays(6),
        status = WeeklyReviewStatus.EMPTY_NO_DATA,
        adherence = null,
        volume = null,
        quality = null,
        intensity = null,
        intensitySessions = 0,
        readiness = null,
        sleep = null,
        observations = emptyList<WeeklyReviewObservation>(),
        ruleIds = emptyList(),
        evidenceIds = emptyList(),
        confidence = DataConfidence.LOW,
    )
}
