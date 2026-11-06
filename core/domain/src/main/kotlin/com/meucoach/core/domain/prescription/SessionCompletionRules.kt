package com.meucoach.core.domain.prescription

import java.time.LocalDate

/**
 * Why a planned session was closed without being done (ADR 0024).
 *
 * Closure is not failure: rest prescribed, illness and a replaced plan are
 * neutral. Only [USER_SKIPPED] and [MISSED] are absence of the planned work.
 */
enum class SessionClosureReason {
    REST_PRESCRIBED,
    ILLNESS_OR_INJURY,
    USER_SKIPPED,
    MISSED,
    PLAN_REPLACED,
    ;

    /** True when the day was not trainable for a reason outside the user's control. */
    val isNonTrainable: Boolean
        get() = this == REST_PRESCRIBED || this == ILLNESS_OR_INJURY || this == PLAN_REPLACED
}

/** Result of a pure completion check. */
sealed interface SessionCompletionDecision {
    /** [warning] is shown to the user but never blocks the save. */
    data class Allowed(val warning: String? = null) : SessionCompletionDecision

    /** [closureReason] is present when the blocked day must be closed instead of left pending. */
    data class Blocked(val message: String, val closureReason: SessionClosureReason? = null) :
        SessionCompletionDecision
}

/**
 * Pure rules for concluding a planned session.
 *
 * The plan is an intention; completion requires evidence (activity, guided
 * execution or explicit manual entry) and can only happen for today or an
 * earlier day of the same week. Future and rest-blocked days are never done.
 */
object SessionCompletionRules {
    fun checkDate(sessionDate: LocalDate, weekStart: LocalDate, today: LocalDate): SessionCompletionDecision {
        if (sessionDate.isAfter(today)) {
            return SessionCompletionDecision.Blocked("Não é possível concluir um treino que ainda não aconteceu.")
        }
        if (sessionDate.isBefore(weekStart)) {
            return SessionCompletionDecision.Blocked("Esta sessão pertence a uma semana anterior.")
        }
        val warning = if (sessionDate.isBefore(today)) {
            "Concluindo um treino de ${sessionDate.dayOfMonth}/${sessionDate.monthValue} — " +
                "o registro vale para aquele dia."
        } else {
            null
        }
        return SessionCompletionDecision.Allowed(warning)
    }

    fun checkSessionState(
        status: SessionStatus,
        prescriptionIsRest: Boolean,
        illnessOrInjury: Boolean,
    ): SessionCompletionDecision = when {
        status == SessionStatus.DONE ->
            SessionCompletionDecision.Blocked("Este treino já foi concluído.")

        status == SessionStatus.CLOSED ->
            SessionCompletionDecision.Blocked("Esta sessão já está encerrada.")

        illnessOrInjury -> SessionCompletionDecision.Blocked(
            message = "Você sinalizou doença ou lesão. O dia fica como descanso; " +
                "uma corrida real continua registrada como treino livre.",
            closureReason = SessionClosureReason.ILLNESS_OR_INJURY,
        )

        prescriptionIsRest -> SessionCompletionDecision.Blocked(
            message = "Hoje é descanso prescrito. Concluir o treino não é possível neste dia.",
            closureReason = SessionClosureReason.REST_PRESCRIBED,
        )

        else -> SessionCompletionDecision.Allowed()
    }

    /** A manual declaration needs a positive duration and an explicit effort. */
    fun checkManual(durationMin: Int?, rpe: Int?): SessionCompletionDecision {
        if (durationMin == null || durationMin <= 0) {
            return SessionCompletionDecision.Blocked("Informe a duração em minutos (maior que zero).")
        }
        if (rpe == null || rpe !in 1..10) {
            return SessionCompletionDecision.Blocked("Informe o esforço percebido de 1 a 10.")
        }
        return SessionCompletionDecision.Allowed()
    }

    fun closureForRest(illnessOrInjury: Boolean): SessionClosureReason = if (illnessOrInjury) {
        SessionClosureReason.ILLNESS_OR_INJURY
    } else {
        SessionClosureReason.REST_PRESCRIBED
    }
}
