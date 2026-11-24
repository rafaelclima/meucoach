package com.meucoach.app.ui.coach

import com.meucoach.app.coach.CoachSources
import com.meucoach.core.domain.coach.CoachOpenAnswer
import com.meucoach.core.domain.coach.CoachOpenContext
import com.meucoach.core.domain.coach.CoachProgress
import com.meucoach.core.domain.coach.CoachSessionReference
import com.meucoach.core.domain.coach.FreeInputParse
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Open running/training question (ADR 0063): the model answers the typed
 * question, but only with the facts the engine already holds. Missing facts
 * stay missing; the validator rejects any number outside the allow-lists, so
 * flexibility never becomes invention.
 */
internal suspend fun answerOpenContent(
    parsed: FreeInputParse,
    exchangeId: String,
    today: LocalDate,
    sources: CoachSources,
    send: suspend (FreeAnswerRequest) -> Unit,
) {
    val planContext = sources.plans.observeCurrent().first()
    val next = sources.next.observeNext().first()
    val progressContext = sources.progress.observeProgress().first()
    val review = sources.reviews.observeLastClosedWeek().first()
    val progress = CoachProgress.summarize(progressContext.weeks, progressContext.easyPercent28d)
    val prescription = next.prescription
    val session = next.todaySession
    val context = CoachOpenContext(
        today = today,
        plan = planContext?.plan,
        prescription = prescription,
        readiness = next.readiness,
        hasCheckIn = next.hasCheckIn,
        sleepMinutes = next.sleepMinutes,
        progress = progress.takeIf { it.availableWeeks > 0 },
        lastReview = review,
    )
    val answer = CoachOpenAnswer.build(parsed.sanitized, context)
    val forbidden = buildSet {
        planContext?.plan?.let {
            addAll(it.ruleIds)
            addAll(it.evidenceIds)
        }
        session?.let {
            addAll(it.ruleIds)
            addAll(it.evidenceIds)
        }
        prescription?.let {
            addAll(it.ruleIds)
            addAll(it.evidenceIds)
        }
    }
    val reference = if (session != null) CoachSessionReference.lines(session, prescription) else emptyList()
    send(
        FreeAnswerRequest(
            parsed = parsed,
            exchangeId = exchangeId,
            contextHash = "open|${planContext?.inputsHash ?: "none"}|$today|${parsed.sanitized.hashCode()}",
            allowedMinutes = answer.allowedMinutes,
            allowedPercents = answer.allowedPercents,
            allowedCounts = answer.allowedCounts,
            allowedDistancesKm = answer.allowedDistancesKm,
            forbiddenCodes = forbidden,
            engineContext = answer.engineContext,
            reference = reference,
        ),
    )
}
