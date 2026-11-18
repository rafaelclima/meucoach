package com.meucoach.core.domain.coach

import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.WeeklyPlan
import java.time.format.DateTimeFormatter

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

    private fun PlannedSession.toSummary(): CoachSessionSummary = CoachSessionSummary(
        date = date.format(dateFormat),
        type = type.name,
        durationMin = durationMin,
        status = status.name,
    )
}
