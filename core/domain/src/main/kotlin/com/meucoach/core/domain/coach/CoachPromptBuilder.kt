package com.meucoach.core.domain.coach

import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.WeeklyPlan
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class CoachSessionSummary(val date: String, val type: String, val durationMin: Int?, val status: String)

data class CoachPrompt(
    val engineVersion: String,
    val inputsHash: String,
    val locale: String,
    val weeklyMinutes: Int,
    val executedMinutes: Int?,
    val adherenceRatio: Double?,
    val sessions: List<CoachSessionSummary>,
    val rationale: String,
    val ruleIds: List<String>,
    val confidence: String,
)

object CoachPromptBuilder {
    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun build(
        plan: WeeklyPlan,
        inputsHash: String,
        executedMinutes: Int? = null,
        adherenceRatio: Double? = null,
        locale: String = "pt-BR",
    ): CoachPrompt {
        require(inputsHash.isNotBlank()) { "inputsHash must not be blank" }
        return CoachPrompt(
            engineVersion = plan.engineVersion,
            inputsHash = inputsHash,
            locale = locale,
            weeklyMinutes = plan.weeklyMinutes,
            executedMinutes = executedMinutes,
            adherenceRatio = adherenceRatio,
            sessions = plan.sessions.map { it.toSummary() },
            rationale = plan.rationale,
            ruleIds = plan.ruleIds,
            confidence = plan.confidence.name,
        )
    }

    /**
     * Counts the explain narrative may quote: total sessions and the
     * per-type/per-status counts that exist in the weekly prompt. Nothing else
     * is a computed count (COACH-10/U-1).
     */
    fun allowedCounts(prompt: CoachPrompt): Set<Int> = buildSet {
        add(prompt.sessions.size)
        prompt.sessions.groupingBy { it.type }.eachCount().values.forEach(::add)
        prompt.sessions.groupingBy { it.status }.eachCount().values.forEach(::add)
    }

    /** Share the explain narrative may quote, only when the prompt carries it. */
    fun allowedPercents(prompt: CoachPrompt): Set<Int> = setOfNotNull(
        prompt.adherenceRatio?.let { ratio -> (ratio * 100).roundToInt() },
    )

    private fun PlannedSession.toSummary(): CoachSessionSummary = CoachSessionSummary(
        date = date.format(dateFormat),
        type = type.name,
        durationMin = durationMin,
        status = status.name,
    )
}
