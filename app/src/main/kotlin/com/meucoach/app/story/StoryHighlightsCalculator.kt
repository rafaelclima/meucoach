package com.meucoach.app.story

import com.meucoach.core.model.Pace
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Self-contained highlights for the public story.
 *
 * The video is watched by people who never saw the prescribed session, so it
 * must not talk about "longão", "no alvo" or "forte demais": those only make
 * sense with the coach context. Everything here is derived from the run itself.
 */
data class StoryHighlights(
    val headline: String,
    val proof: String?,
    val fastestKm: Int?,
    val fastestPace: Pace?,
    val closingLine: String,
)

object StoryHighlightsCalculator {
    private val PT_BR = Locale.forLanguageTag("pt-BR")
    private const val NEGATIVE_SPLIT_GAIN = 0.03
    private const val CONSISTENT_SPREAD = 0.05

    fun calculate(plan: StoryPlan): StoryHighlights {
        val splits = plan.splits.filter { it.pace.secondsPerKm > 0 }
        if (splits.size < 2) {
            return StoryHighlights(
                headline = "CORRIDA CONCLUÍDA",
                proof = plan.durationTotalSec.takeIf { it > 0 }?.let { "em ${formatDuration(it)}" },
                fastestKm = splits.firstOrNull()?.km,
                fastestPace = splits.firstOrNull()?.pace,
                closingLine = "Cada corrida conta.",
            )
        }
        val paces = splits.map { it.pace.secondsPerKm }
        val fastest = splits.minBy { it.pace.secondsPerKm }
        val average = paces.average()
        val spread = (paces.max() - paces.min()) / average
        val half = splits.size / 2
        val firstHalf = splits.take(half).map { it.pace.secondsPerKm }.average()
        val secondHalf = splits.drop(half).map { it.pace.secondsPerKm }.average()
        val negativeSplit = secondHalf < firstHalf * (1 - NEGATIVE_SPLIT_GAIN)
        val gain = if (firstHalf > 0) ((firstHalf - secondHalf) / firstHalf * 100).roundToInt() else 0

        return when {
            negativeSplit -> StoryHighlights(
                headline = "2ª METADE MAIS RÁPIDA",
                proof = "$gain% mais rápida que a primeira",
                fastestKm = fastest.km,
                fastestPace = fastest.pace,
                closingLine = "Cada corrida conta.",
            )

            spread <= CONSISTENT_SPREAD -> StoryHighlights(
                headline = "RITMO CONSTANTE",
                proof = "variação de ${(spread * 100).roundToInt()}% entre os kms",
                fastestKm = fastest.km,
                fastestPace = fastest.pace,
                closingLine = "Cada corrida conta.",
            )

            else -> StoryHighlights(
                headline = "KM MAIS RÁPIDO",
                proof = "km ${fastest.km} em ${fastest.pace.format()}",
                fastestKm = fastest.km,
                fastestPace = fastest.pace,
                closingLine = "Cada corrida conta.",
            )
        }
    }

    private fun Pace.format(): String = String.format(PT_BR, "%d:%02d/km", secondsPerKm / 60, secondsPerKm % 60)

    private fun formatDuration(totalSec: Int): String {
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return String.format(PT_BR, "%d:%02d", minutes, seconds)
    }
}
