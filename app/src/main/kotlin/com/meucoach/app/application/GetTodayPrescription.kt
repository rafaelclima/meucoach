package com.meucoach.app.application

import com.meucoach.core.data.db.entity.plannedSessionId
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.DailyPrescriptionEngine
import com.meucoach.core.domain.prescription.DailyPrescriptionInput
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.HeatLevel
import java.time.LocalDate
import javax.inject.Inject

data class TodayPrescriptionRequest(
    val date: LocalDate,
    val profile: AthleteProfile,
    val readiness: ReadinessResult,
    val load: LoadSnapshot? = null,
    val heatLevel: HeatLevel = HeatLevel.NONE,
    val plan: WeeklyPlan? = null,
)

/**
 * Reads or appends today's single prescription (ADR 0023).
 *
 * The decision is persisted once per (date, engineVersion, inputsHash). A
 * changed check-in/readiness produces a new version that supersedes the last
 * one; nothing is overwritten.
 */
class GetTodayPrescription @Inject constructor(private val repository: DailyPrescriptionRepository) {
    private val engine = DailyPrescriptionEngine()

    suspend operator fun invoke(request: TodayPrescriptionRequest): DailyPrescription {
        val plan = request.plan
        val planned = plan
            ?.sessions
            ?.firstOrNull { it.date == request.date && it.status != SessionStatus.SKIPPED }
        val input = DailyPrescriptionInput(
            date = request.date,
            profile = request.profile,
            readiness = request.readiness,
            load = request.load,
            heatLevel = request.heatLevel,
            plannedSession = planned,
            plannedSessionId = if (plan != null && planned != null) {
                plannedSessionId(plan.weekStart, planned.date)
            } else {
                null
            },
            planConfidence = plan?.confidence,
        )
        val fresh = engine.prescribe(input)
        val latest = repository.getLatest(request.date)
        if (latest != null &&
            latest.engineVersion == fresh.engineVersion &&
            latest.inputsHash == fresh.inputsHash
        ) {
            return latest
        }
        return repository.append(fresh)
    }
}
