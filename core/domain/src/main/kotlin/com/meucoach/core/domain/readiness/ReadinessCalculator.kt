package com.meucoach.core.domain.readiness

import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ReadinessLevel
import kotlin.math.roundToInt

object ReadinessCalculator {
    private const val MIN_HRV_NIGHTS = 4
    private const val SHORT_SLEEP_MINUTES = 360

    private val weights =
        mapOf(
            ReadinessComponent.SUBJECTIVE to 0.35,
            ReadinessComponent.SLEEP to 0.25,
            ReadinessComponent.HRV to 0.25,
            ReadinessComponent.RESTING_HEART_RATE to 0.15,
        )

    fun calculate(
        checkIn: SubjectiveCheckIn?,
        sleep: SleepMetrics?,
        hrv: HrvMetrics?,
        restingHeartRate: RestingHeartRateMetrics?,
        signals: ReadinessSignals = ReadinessSignals(),
    ): ReadinessResult {
        val components = linkedMapOf<ReadinessComponent, Int>()

        checkIn?.let { components[ReadinessComponent.SUBJECTIVE] = subjectiveScore(it) }
        sleep?.let { components[ReadinessComponent.SLEEP] = sleepScore(it.durationMin) }
        hrvScore(hrv)?.let { components[ReadinessComponent.HRV] = it }
        restingHeartRateScore(restingHeartRate)?.let {
            components[ReadinessComponent.RESTING_HEART_RATE] = it
        }

        val allComponents = ReadinessComponent.entries.toSet()
        if (components.isEmpty()) {
            return ReadinessResult(
                score = 50,
                level = ReadinessLevel.AMBER,
                confidence = DataConfidence.LOW,
                components = components,
                missing = allComponents,
                flags = setOf(ReadinessFlag.INSUFFICIENT_DATA),
            )
        }

        val totalWeight = components.keys.sumOf { component -> weights.getValue(component) }
        val weightedScore =
            components.entries.sumOf { (component, score) ->
                score * weights.getValue(component)
            } / totalWeight
        var score = weightedScore.roundToInt().coerceIn(0, 100)
        var level = levelFor(score)
        val flags = linkedSetOf<ReadinessFlag>()

        if (sleep != null && sleep.durationMin < SHORT_SLEEP_MINUTES) {
            flags += ReadinessFlag.SHORT_SLEEP
            if (level == ReadinessLevel.GREEN) {
                level = ReadinessLevel.AMBER
                score = score.coerceAtMost(69)
            }
        }

        if (signals.illnessOrInjury) {
            flags += ReadinessFlag.ILLNESS_OR_INJURY
            level = ReadinessLevel.RED
            score = score.coerceAtMost(29)
        }

        if (signals.restingHeartRateElevatedTwoDays) {
            flags += ReadinessFlag.RESTING_HEART_RATE_ELEVATED
            if (level == ReadinessLevel.GREEN) {
                level = ReadinessLevel.AMBER
                score = score.coerceAtMost(69)
            }
        }

        if (level != ReadinessLevel.GREEN) {
            flags += ReadinessFlag.NO_QUALITY
        }

        val confidence = confidenceFor(components)

        return ReadinessResult(
            score = score,
            level = level,
            confidence = confidence,
            components = components,
            missing = allComponents - components.keys,
            flags = flags,
        )
    }

    private fun subjectiveScore(checkIn: SubjectiveCheckIn): Int =
        ((20 - checkIn.hooperSum) / 16.0 * 100.0).roundToInt().coerceIn(0, 100)

    private fun sleepScore(durationMin: Int): Int {
        val score =
            when {
                durationMin <= 300 -> 0.0
                durationMin < SHORT_SLEEP_MINUTES -> lerp(0.0, 40.0, (durationMin - 300) / 60.0)
                durationMin < 420 -> lerp(40.0, 100.0, (durationMin - SHORT_SLEEP_MINUTES) / 60.0)
                durationMin <= 540 -> 100.0
                else -> 90.0
            }
        return score.roundToInt().coerceIn(0, 100)
    }

    private fun hrvScore(hrv: HrvMetrics?): Int? {
        if (hrv == null || hrv.validNights < MIN_HRV_NIGHTS) return null
        return (50.0 + 25.0 * hrv.zScore).roundToInt().coerceIn(0, 100)
    }

    private fun restingHeartRateScore(metrics: RestingHeartRateMetrics?): Int? {
        if (metrics == null) return null
        val delta = metrics.average7dBpm - metrics.baseline30dBpm
        return (50.0 - 10.0 * delta).roundToInt().coerceIn(0, 100)
    }

    private fun confidenceFor(components: Map<ReadinessComponent, Int>): DataConfidence {
        val objectiveCount = components.keys.count { it != ReadinessComponent.SUBJECTIVE }
        val hasSubjective = ReadinessComponent.SUBJECTIVE in components
        return when {
            objectiveCount >= 3 && hasSubjective -> DataConfidence.HIGH
            objectiveCount >= 2 || (objectiveCount >= 1 && hasSubjective) -> DataConfidence.MEDIUM
            else -> DataConfidence.LOW
        }
    }

    private fun levelFor(score: Int): ReadinessLevel = when {
        score >= 70 -> ReadinessLevel.GREEN
        score >= 40 -> ReadinessLevel.AMBER
        else -> ReadinessLevel.RED
    }

    private fun lerp(start: Double, end: Double, fraction: Double): Double =
        start + (end - start) * fraction.coerceIn(0.0, 1.0)
}
