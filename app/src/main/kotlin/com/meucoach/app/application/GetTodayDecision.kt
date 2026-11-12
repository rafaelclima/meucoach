package com.meucoach.app.application

import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.RecoveryWindow
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.domain.today.TodayDecision
import com.meucoach.core.domain.today.TodayDecisionResolver
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.HeatLevel
import java.time.LocalDate
import javax.inject.Inject

/**
 * Request for the single decision of the day (ADR 0030/0023).
 *
 * [profile] is null before onboarding; without an event there is no decision.
 */
data class TodayDecisionRequest(
    val date: LocalDate,
    val profile: AthleteProfile?,
    val readiness: ReadinessResult,
    val load: LoadSnapshot? = null,
    val heatLevel: HeatLevel = HeatLevel.NONE,
    val plan: WeeklyPlan? = null,
    /** Post-race recovery window (ADR 0037). */
    val recovery: RecoveryWindow? = null,
)

/**
 * Resolves the day before any surface renders it.
 *
 * A race event on [TodayDecisionRequest.date] wins and suppresses the training
 * prescription: no prescription is calculated or persisted for a race day. A
 * legacy prescription stored for the same date is not deleted, only hidden.
 */
class GetTodayDecision @Inject constructor(
    private val raceEvents: RaceEventRepository,
    private val getTodayPrescription: GetTodayPrescription,
) {
    suspend operator fun invoke(request: TodayDecisionRequest): TodayDecision? {
        val event = raceEvents.getForDate(request.date)
        if (event != null) {
            return TodayDecisionResolver.resolve(date = request.date, event = event, prescription = null)
        }
        val profile = request.profile ?: return null
        val prescription = getTodayPrescription(
            TodayPrescriptionRequest(
                date = request.date,
                profile = profile,
                readiness = request.readiness,
                load = request.load,
                heatLevel = request.heatLevel,
                plan = request.plan,
                recovery = request.recovery,
            ),
        )
        return TodayDecisionResolver.resolve(date = request.date, event = null, prescription = prescription)
    }
}
