package com.meucoach.app.ui.trends

import com.meucoach.core.domain.review.WeeklyAdherence
import com.meucoach.core.domain.review.WeeklyReadiness
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewObservation
import com.meucoach.core.domain.review.WeeklyReviewObservationKind
import com.meucoach.core.domain.review.WeeklyReviewStatus
import com.meucoach.core.domain.review.WeeklySleep
import com.meucoach.core.domain.review.WeeklyVolume
import com.meucoach.core.domain.trends.WeeklyIntensity
import com.meucoach.core.model.DataConfidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class WeeklyReviewFormatTest {
    private val weekStart = LocalDate.of(2026, 10, 19)
    private val weekEnd = LocalDate.of(2026, 10, 25)

    @Test
    fun `formats the closed week range`() {
        val review = review()

        assertThat(WeeklyReviewFormat.dateRange(review)).isEqualTo("Semana de 19/10 a 25/10")
    }

    @Test
    fun `formats adherence as fact with evidence detail`() {
        val adherence = WeeklyAdherence(
            planned = 4,
            done = 2,
            verified = 1,
            manual = 1,
            legacy = 0,
            skipped = 2,
        )

        assertThat(WeeklyReviewFormat.adherenceHeadline(adherence)).isEqualTo("2 de 4 sessões concluídas")
        assertThat(WeeklyReviewFormat.adherencePercent(adherence)).isEqualTo("50%")
        assertThat(WeeklyReviewFormat.adherenceDetail(adherence))
            .isEqualTo("1 com evidência · 1 sem registro · 2 puladas")
    }

    @Test
    fun `formats minutes without inventing a duration`() {
        assertThat(WeeklyReviewFormat.minutes(45)).isEqualTo("45 min")
        assertThat(WeeklyReviewFormat.minutes(120)).isEqualTo("2h")
        assertThat(WeeklyReviewFormat.minutes(451)).isEqualTo("7h31")
        assertThat(WeeklyReviewFormat.volume(null)).isEqualTo("—")
    }

    @Test
    fun `formats volume and health values with missing sides as dash`() {
        val volume = WeeklyVolume(plannedMin = 150, executedMin = 100, percentOfPlan = 67)
        assertThat(WeeklyReviewFormat.volume(volume.plannedMin)).isEqualTo("2h30")
        assertThat(WeeklyReviewFormat.volume(volume.executedMin)).isEqualTo("1h40")
        assertThat(WeeklyReviewFormat.volumePercent(volume)).isEqualTo("67%")

        val executedOnly = WeeklyVolume(plannedMin = null, executedMin = 30, percentOfPlan = null)
        assertThat(WeeklyReviewFormat.volume(executedOnly.plannedMin)).isEqualTo("—")
        assertThat(WeeklyReviewFormat.volumePercent(executedOnly)).isEqualTo("—")

        val readiness = WeeklyReadiness(measuredDays = 5, averageScore = 48, redDays = 3)
        assertThat(WeeklyReviewFormat.readinessValue(readiness)).isEqualTo("48")
        assertThat(WeeklyReviewFormat.readinessHint(readiness)).isEqualTo("5 de 7 dias")

        val sleep = WeeklySleep(measuredNights = 6, averageMinutes = 451, shortNights = 3)
        assertThat(WeeklyReviewFormat.sleepValue(sleep)).isEqualTo("7h31")
        assertThat(WeeklyReviewFormat.sleepHint(sleep)).isEqualTo("6 noites · 3 curtas")
    }

    @Test
    fun `writes every observation without technical ids`() {
        val review = review()
        val texts = WeeklyReviewObservationKind.entries.map { kind ->
            WeeklyReviewFormat.observationText(review, observation(kind))
        }

        assertThat(texts).allSatisfy { text ->
            assertThat(text).isNotBlank()
            assertThat(text).doesNotContain("R-")
            assertThat(text).doesNotContain("EV-")
        }
    }

    @Test
    fun `uses the week numbers in each observation`() {
        val review = review()

        assertThat(WeeklyReviewFormat.observationText(review, observation(WeeklyReviewObservationKind.ILLNESS_SIGNAL)))
            .contains("doença ou lesão")
        assertThat(WeeklyReviewFormat.observationText(review, observation(WeeklyReviewObservationKind.SHORT_SLEEP)))
            .contains("3 noites", "6h30")
        assertThat(WeeklyReviewFormat.observationText(review, observation(WeeklyReviewObservationKind.LOW_READINESS)))
            .contains("3 dias")
        assertThat(
            WeeklyReviewFormat.observationText(review, observation(WeeklyReviewObservationKind.ADHERENCE_PARTIAL)),
        )
            .contains("2 de 4")
        assertThat(
            WeeklyReviewFormat.observationText(review, observation(WeeklyReviewObservationKind.VOLUME_BELOW_PLAN)),
        )
            .contains("33% abaixo")
        assertThat(
            WeeklyReviewFormat.observationText(
                review(volume = WeeklyVolume(plannedMin = 100, executedMin = 110, percentOfPlan = 110)),
                observation(WeeklyReviewObservationKind.VOLUME_ABOVE_PLAN),
            ),
        ).contains("10% acima")
        assertThat(
            WeeklyReviewFormat.observationText(
                review,
                observation(WeeklyReviewObservationKind.INTENSITY_ABOVE_EASY_TARGET),
            ),
        ).contains("50%", "80%")
        assertThat(WeeklyReviewFormat.observationText(review, observation(WeeklyReviewObservationKind.NO_ALERTS)))
            .contains("Nenhum alerta")
    }

    @Test
    fun `reports none done without blaming the athlete`() {
        val review = review(
            adherence = WeeklyAdherence(planned = 3, done = 0, verified = 0, manual = 0, legacy = 0, skipped = 3),
        )

        val text = WeeklyReviewFormat.observationText(
            review,
            observation(WeeklyReviewObservationKind.ADHERENCE_NONE),
        )

        assertThat(text).isEqualTo("Nenhuma das 3 sessões planejadas foi concluída.")
    }

    @Test
    fun `keeps singular grammar for a single skipped session or short night`() {
        val adherence = WeeklyAdherence(
            planned = 3,
            done = 2,
            verified = 1,
            manual = 0,
            legacy = 1,
            skipped = 1,
        )

        assertThat(WeeklyReviewFormat.adherenceDetail(adherence))
            .isEqualTo("1 com evidência · 1 não verificada · 1 pulada")
        assertThat(WeeklyReviewFormat.sleepHint(WeeklySleep(measuredNights = 6, averageMinutes = 451, shortNights = 1)))
            .isEqualTo("6 noites · 1 curta")

        val single = review(
            adherence = WeeklyAdherence(planned = 1, done = 0, verified = 0, manual = 0, legacy = 0, skipped = 1),
        )
        assertThat(
            WeeklyReviewFormat.observationText(single, observation(WeeklyReviewObservationKind.ADHERENCE_NONE)),
        ).isEqualTo("A única sessão planejada não foi concluída.")
        assertThat(
            WeeklyReviewFormat.observationText(
                review(sleep = WeeklySleep(measuredNights = 6, averageMinutes = 451, shortNights = 1)),
                observation(WeeklyReviewObservationKind.SHORT_SLEEP),
            ),
        ).contains("1 noite,", "abaixo de 6h30")
    }

    @Test
    fun `states an honest empty state`() {
        assertThat(WeeklyReviewFormat.emptyDescription())
            .isEqualTo("Quando houver plano, corridas ou check-ins, o resumo da semana aparece aqui.")
    }

    private fun review(
        adherence: WeeklyAdherence = WeeklyAdherence(
            planned = 4,
            done = 2,
            verified = 1,
            manual = 1,
            legacy = 0,
            skipped = 2,
        ),
        volume: WeeklyVolume = WeeklyVolume(plannedMin = 150, executedMin = 100, percentOfPlan = 67),
        intensity: WeeklyIntensity = WeeklyIntensity(weekStart, easySec = 3_600, moderateSec = 1_800, hardSec = 1_800),
        readiness: WeeklyReadiness = WeeklyReadiness(measuredDays = 5, averageScore = 48, redDays = 3),
        sleep: WeeklySleep = WeeklySleep(measuredNights = 6, averageMinutes = 451, shortNights = 3),
    ) = WeeklyReview(
        weekStart = weekStart,
        weekEnd = weekEnd,
        status = WeeklyReviewStatus.AVAILABLE,
        adherence = adherence,
        volume = volume,
        intensity = intensity,
        intensitySessions = 4,
        readiness = readiness,
        sleep = sleep,
        observations = emptyList(),
        ruleIds = listOf("R-REVIEW-01"),
        evidenceIds = emptyList(),
        confidence = DataConfidence.MEDIUM,
    )

    private fun observation(kind: WeeklyReviewObservationKind) = WeeklyReviewObservation(
        kind = kind,
        ruleId = "R-REVIEW-01",
        evidenceIds = emptyList(),
        confidence = DataConfidence.MEDIUM,
    )
}
