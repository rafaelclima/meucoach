package com.meucoach.core.domain.coach

import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewCalculator

/**
 * Minimal, deterministic payload of the closed-week review (PRD IA Coach 3.2).
 *
 * Only facts the engine already consolidated travel: no name, no GPS, no exact
 * timestamps and no free text from the athlete. `inputsHash` is the cache key of
 * the rewritten narrative and changes whenever a reviewed fact changes.
 */
data class CoachReviewAdherence(
    val planned: Int,
    val done: Int,
    val verified: Int,
    val manual: Int,
    val legacy: Int,
    val skipped: Int,
    val percent: Int?,
)

data class CoachReviewVolume(val plannedMin: Int?, val executedMin: Int?, val extraMin: Int?, val percentOfPlan: Int?)

data class CoachReviewQuality(val planned: Int, val kept: Int, val downgraded: Int)

data class CoachReviewReadiness(val measuredDays: Int, val averageScore: Int?, val redDays: Int)

data class CoachReviewSleep(val measuredNights: Int, val averageMinutes: Int?, val shortNights: Int)

data class CoachReviewIntensity(val easyPercent: Int, val sessions: Int)

data class CoachReviewPrompt(
    val inputsHash: String,
    val locale: String,
    val weekStart: String,
    val weekEnd: String,
    val status: String,
    val adherence: CoachReviewAdherence?,
    val volume: CoachReviewVolume?,
    val quality: CoachReviewQuality?,
    val readiness: CoachReviewReadiness?,
    val sleep: CoachReviewSleep?,
    val intensity: CoachReviewIntensity?,
    val observations: List<String>,
    val ruleIds: List<String>,
    val confidence: String,
)

object CoachReviewPromptBuilder {
    /** Versioned prefix so a newer prompt schema never reuses an old cache key. */
    const val SCHEMA = "coach-review-v1"

    fun build(review: WeeklyReview, locale: String = "pt-BR"): CoachReviewPrompt {
        val adherence = review.adherence?.let {
            CoachReviewAdherence(
                planned = it.planned,
                done = it.done,
                verified = it.verified,
                manual = it.manual,
                legacy = it.legacy,
                skipped = it.skipped,
                percent = it.completionRatio?.let { ratio -> Math.round(ratio * 100).toInt() },
            )
        }
        val volume = review.volume?.let {
            CoachReviewVolume(
                plannedMin = it.plannedMin,
                executedMin = it.executedMin,
                extraMin = it.extraMin,
                percentOfPlan = it.percentOfPlan,
            )
        }
        val quality = review.quality?.takeIf { it.hasDecision }?.let {
            CoachReviewQuality(planned = it.planned, kept = it.kept, downgraded = it.downgraded)
        }
        val readiness = review.readiness?.let {
            CoachReviewReadiness(
                measuredDays = it.measuredDays,
                averageScore = it.averageScore,
                redDays = it.redDays,
            )
        }
        val sleep = review.sleep?.let {
            CoachReviewSleep(
                measuredNights = it.measuredNights,
                averageMinutes = it.averageMinutes,
                shortNights = it.shortNights,
            )
        }
        val intensity = review.intensity?.let {
            CoachReviewIntensity(
                easyPercent = Math.round(it.easyPercent()).toInt(),
                sessions = review.intensitySessions,
            )
        }
        val observations = review.observations.map { it.kind.name }
        val prompt = CoachReviewPrompt(
            inputsHash = "",
            locale = locale,
            weekStart = review.weekStart.toString(),
            weekEnd = review.weekEnd.toString(),
            status = review.status.name,
            adherence = adherence,
            volume = volume,
            quality = quality,
            readiness = readiness,
            sleep = sleep,
            intensity = intensity,
            observations = observations,
            ruleIds = review.ruleIds,
            confidence = review.confidence.name,
        )
        return prompt.copy(inputsHash = fingerprint(prompt))
    }

    /** Minutes the narrative may quote: planned, executed, extra, average sleep and the short-sleep threshold. */
    fun allowedMinutes(prompt: CoachReviewPrompt): Set<Int> = buildSet {
        prompt.volume?.let { volume ->
            addAll(listOfNotNull(volume.plannedMin, volume.executedMin, volume.extraMin))
        }
        prompt.sleep?.let { sleep ->
            sleep.averageMinutes?.let(::add)
            if (sleep.shortNights > 0) add(WeeklyReviewCalculator.SHORT_SLEEP_MINUTES)
        }
    }

    /** Share values the narrative may quote; any other percentage is an invention. */
    fun allowedPercents(prompt: CoachReviewPrompt): Set<Int> = buildSet {
        prompt.adherence?.percent?.let(::add)
        prompt.volume?.percentOfPlan?.let(::add)
        prompt.intensity?.easyPercent?.let(::add)
    }

    /**
     * Counts the review narrative may quote. Percentages stay in
     * [allowedPercents]; they are never accepted as bare counts (U-1/U-2).
     */
    fun allowedCounts(prompt: CoachReviewPrompt): Set<Int> = buildSet {
        add(DAYS_IN_WEEK)
        prompt.adherence?.let {
            add(it.planned)
            add(it.done)
            add(it.verified)
            add(it.manual)
            add(it.legacy)
            add(it.skipped)
        }
        prompt.quality?.let {
            add(it.planned)
            add(it.kept)
            add(it.downgraded)
        }
        prompt.readiness?.let {
            add(it.measuredDays)
            add(it.redDays)
        }
        prompt.sleep?.let {
            add(it.measuredNights)
            add(it.shortNights)
        }
        prompt.intensity?.let { add(it.sessions) }
    }

    private const val DAYS_IN_WEEK = 7

    private fun fingerprint(prompt: CoachReviewPrompt): String = listOf(
        SCHEMA,
        prompt.locale,
        prompt.weekStart,
        prompt.weekEnd,
        prompt.status,
        prompt.adherence.toString(),
        prompt.volume.toString(),
        prompt.quality.toString(),
        prompt.readiness.toString(),
        prompt.sleep.toString(),
        prompt.intensity.toString(),
        prompt.observations.joinToString(","),
        prompt.ruleIds.joinToString(","),
        prompt.confidence,
    ).joinToString("|")
}
