package com.meucoach.core.domain.insights

import com.meucoach.core.model.ActivityLap
import com.meucoach.core.model.DataConfidence
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ActivityInsightTone {
    POSITIVE,
    NEUTRAL,
    WARNING,
}

/**
 * Explainable observation about a finished activity. Every insight carries the
 * rule that produced it, its evidence and a confidence level so the UI never
 * shows an unsourced claim.
 */
data class ActivityInsight(
    val ruleId: String,
    val evidenceIds: List<String>,
    val confidence: DataConfidence,
    val tone: ActivityInsightTone,
    val message: String,
) {
    init {
        require(ruleId.isNotBlank()) { "Insight rule id must not be blank" }
        require(evidenceIds.isNotEmpty()) { "Insight must cite at least one evidence" }
        require(evidenceIds.none { it.isBlank() }) { "Evidence ids must not be blank" }
        require(message.isNotBlank()) { "Insight message must not be blank" }
    }
}

object ActivityInsightRules {
    const val PACING = "R-ACT-01"
    const val HEART_RATE_DRIFT = "R-ACT-02"
}

/**
 * Deterministic post-run observations based only on recorded laps.
 *
 * Missing lap data produces no insight; the calculator never fills a gap with
 * a neutral claim.
 */
class ActivityInsightsCalculator {
    fun calculate(laps: List<ActivityLap>): List<ActivityInsight> {
        val ordered = laps.sortedBy { it.index }
        if (ordered.size < 2) return emptyList()

        val midpoint = (ordered.size + 1) / 2
        val first = ordered.take(midpoint).aggregate() ?: return emptyList()
        val second = ordered.drop(midpoint).aggregate() ?: return emptyList()

        val paceChangePercent = (second.paceSecPerKm - first.paceSecPerKm) / first.paceSecPerKm * 100.0
        val insights = mutableListOf(pacingInsight(paceChangePercent))

        val firstHeartRate = first.averageHeartRate
        val secondHeartRate = second.averageHeartRate
        if (
            firstHeartRate != null &&
            secondHeartRate != null &&
            abs(paceChangePercent) <= STABLE_PACE_PERCENT
        ) {
            val heartRateChange = (secondHeartRate - firstHeartRate) / firstHeartRate.toDouble() * 100.0
            if (heartRateChange >= DRIFT_THRESHOLD_PERCENT) {
                insights += ActivityInsight(
                    ruleId = ActivityInsightRules.HEART_RATE_DRIFT,
                    evidenceIds = listOf("EV-FRIEL-2018"),
                    confidence = DataConfidence.LOW,
                    tone = ActivityInsightTone.WARNING,
                    message = "A FC subiu ${formatPercent(heartRateChange)} com ritmo estável. " +
                        "Calor, desidratação ou fadiga podem explicar; não é diagnóstico.",
                )
            }
        }
        return insights
    }

    private fun pacingInsight(paceChangePercent: Double): ActivityInsight = when {
        paceChangePercent <= NEGATIVE_SPLIT_PERCENT -> ActivityInsight(
            ruleId = ActivityInsightRules.PACING,
            evidenceIds = PACING_EVIDENCE,
            confidence = DataConfidence.MEDIUM,
            tone = ActivityInsightTone.POSITIVE,
            message = "Você fechou mais rápido: a segunda metade foi " +
                "${formatPercent(-paceChangePercent)} mais rápida.",
        )

        paceChangePercent < FADE_THRESHOLD_PERCENT -> ActivityInsight(
            ruleId = ActivityInsightRules.PACING,
            evidenceIds = PACING_EVIDENCE,
            confidence = DataConfidence.MEDIUM,
            tone = ActivityInsightTone.POSITIVE,
            message = "Ritmo constante: a segunda metade ficou dentro de " +
                "${formatPercent(FADE_THRESHOLD_PERCENT)} da primeira.",
        )

        else -> ActivityInsight(
            ruleId = ActivityInsightRules.PACING,
            evidenceIds = PACING_EVIDENCE,
            confidence = DataConfidence.MEDIUM,
            tone = ActivityInsightTone.WARNING,
            message = "O ritmo caiu ${formatPercent(paceChangePercent)} na segunda metade. " +
                "Pode ser fadiga, calor ou largada forte.",
        )
    }

    private fun List<ActivityLap>.aggregate(): HalfAggregate? {
        var distanceM = 0.0
        var durationSec = 0
        var heartRateWeighted = 0.0
        var heartRateDuration = 0
        forEach { lap ->
            distanceM += lap.distanceM
            durationSec += lap.durationSec
            val heartRate = lap.averageHeartRate
            if (heartRate != null && lap.durationSec > 0) {
                heartRateWeighted += heartRate * lap.durationSec
                heartRateDuration += lap.durationSec
            }
        }
        if (distanceM <= 0.0 || durationSec <= 0) return null
        return HalfAggregate(
            paceSecPerKm = durationSec / (distanceM / 1_000.0),
            averageHeartRate = if (heartRateDuration > 0) {
                (heartRateWeighted / heartRateDuration).roundToInt()
            } else {
                null
            },
        )
    }

    private fun formatPercent(value: Double): String = String.format(PT_BR, "%.1f%%", value)

    private data class HalfAggregate(val paceSecPerKm: Double, val averageHeartRate: Int?)

    private companion object {
        val PT_BR = Locale.forLanguageTag("pt-BR")
        val PACING_EVIDENCE = listOf("EV-ABBISS-2008", "EV-DANIELS-2014")
        const val NEGATIVE_SPLIT_PERCENT = -2.0
        const val FADE_THRESHOLD_PERCENT = 3.0
        const val STABLE_PACE_PERCENT = 3.0
        const val DRIFT_THRESHOLD_PERCENT = 5.0
    }
}
