package com.meucoach.app.ui.trends

import com.meucoach.core.domain.review.WeeklyAdherence
import com.meucoach.core.domain.review.WeeklyReadiness
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewObservation
import com.meucoach.core.domain.review.WeeklyReviewObservationKind
import com.meucoach.core.domain.review.WeeklySleep
import com.meucoach.core.domain.review.WeeklyVolume
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Copy of the weekly review. The domain returns structure, numbers and IDs; this
 * formatter turns them into short, descriptive pt-BR text without judgements,
 * streaks or technical IDs on the common surface (FR-011 / ADR 0034).
 */
internal object WeeklyReviewFormat {
    private val PT_BR = Locale.forLanguageTag("pt-BR")
    private val DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM")

    fun dateRange(review: WeeklyReview): String =
        "Semana de ${review.weekStart.format(DAY_FORMAT)} a ${review.weekEnd.format(DAY_FORMAT)}"

    fun adherenceHeadline(adherence: WeeklyAdherence): String =
        "${adherence.done} de ${adherence.planned} sessões concluídas"

    fun adherencePercent(adherence: WeeklyAdherence): String? =
        adherence.completionRatio?.let { String.format(PT_BR, "%.0f%%", it * 100) }

    fun adherenceDetail(adherence: WeeklyAdherence): String {
        val parts = buildList {
            add("${adherence.verified} com evidência")
            if (adherence.manual > 0) add("${adherence.manual} sem registro")
            if (adherence.legacy > 0) {
                add("${adherence.legacy} ${plural(adherence.legacy, "não verificada", "não verificadas")}")
            }
            if (adherence.skipped > 0) add("${adherence.skipped} ${plural(adherence.skipped, "pulada", "puladas")}")
        }
        return parts.joinToString(" · ")
    }

    fun volume(value: Int?): String = value?.let(::minutes) ?: "—"

    fun volumePercent(volume: WeeklyVolume): String = volume.percentOfPlan?.let { "$it%" } ?: "—"

    fun readinessValue(readiness: WeeklyReadiness): String = readiness.averageScore?.toString() ?: "—"

    fun readinessHint(readiness: WeeklyReadiness): String = "${readiness.measuredDays} de 7 dias"

    fun sleepValue(sleep: WeeklySleep): String = sleep.averageMinutes?.let(::minutes) ?: "—"

    fun sleepHint(sleep: WeeklySleep): String = if (sleep.shortNights == 0) {
        "${sleep.measuredNights} noites"
    } else {
        "${sleep.measuredNights} noites · ${sleep.shortNights} " +
            plural(sleep.shortNights, "curta", "curtas")
    }

    /** Minutes as `45 min`, `2h` or `7h31`, without inventing a duration. */
    fun minutes(value: Int): String {
        val hours = value / 60
        val rest = value % 60
        return when {
            hours == 0 -> "$rest min"
            rest == 0 -> "${hours}h"
            else -> "${hours}h${rest.toString().padStart(2, '0')}"
        }
    }

    fun observationText(review: WeeklyReview, observation: WeeklyReviewObservation): String = when (observation.kind) {
        WeeklyReviewObservationKind.ILLNESS_SIGNAL ->
            "Um check-in registrou doença ou lesão. O app não inicia recuperação automática; " +
                "procure avaliação se o sinal persistir."

        WeeklyReviewObservationKind.SHORT_SLEEP ->
            "Sono curto em ${review.sleep?.shortNights ?: 0} " +
                plural(review.sleep?.shortNights ?: 0, "noite", "noites") +
                ", abaixo de 6h30. " +
                "A recomendação é não aumentar o volume até o sono normalizar."

        WeeklyReviewObservationKind.LOW_READINESS ->
            "Prontidão baixa em ${review.readiness?.redDays ?: 0} " +
                plural(review.readiness?.redDays ?: 0, "dia", "dias") +
                ". A semana pediu mais recuperação."

        WeeklyReviewObservationKind.ADHERENCE_PARTIAL ->
            "${review.adherence?.done ?: 0} de ${review.adherence?.planned ?: 0} sessões " +
                "planejadas foram concluídas."

        WeeklyReviewObservationKind.ADHERENCE_NONE ->
            if ((review.adherence?.planned ?: 0) == 1) {
                "A única sessão planejada não foi concluída."
            } else {
                "Nenhuma das ${review.adherence?.planned ?: 0} sessões planejadas foi concluída."
            }

        WeeklyReviewObservationKind.VOLUME_BELOW_PLAN ->
            "O volume ficou ${100 - (review.volume?.percentOfPlan ?: 100)}% abaixo do planejado."

        WeeklyReviewObservationKind.VOLUME_ABOVE_PLAN ->
            "O volume ficou ${(review.volume?.percentOfPlan ?: 100) - 100}% acima do planejado."

        WeeklyReviewObservationKind.INTENSITY_ABOVE_EASY_TARGET ->
            "O tempo fácil ficou em ${easyPercent(review)}% do total, abaixo dos 80%."

        WeeklyReviewObservationKind.NO_ALERTS ->
            "Nenhum alerta de recuperação, adesão ou intensidade nesta semana."
    }

    fun emptyDescription(): String = "Quando houver plano, corridas ou check-ins, o resumo da semana aparece aqui."

    private fun easyPercent(review: WeeklyReview): String =
        review.intensity?.let { String.format(PT_BR, "%.0f", it.easyPercent()) } ?: "—"

    private fun plural(count: Int, singular: String, plural: String): String = if (count == 1) singular else plural
}
