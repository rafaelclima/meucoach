package com.meucoach.app.coach

import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.WeeklyPlan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The persisted week plus the revision identity used by the explanation cache.
 * `version`, `inputsHash` and the plan content are all part of the value, so a
 * replan accepted in the same week invalidates the previous narrative even
 * when the compatibility hash did not change.
 */
data class CoachPlanContext(val plan: WeeklyPlan, val inputsHash: String, val version: Int = 1)

interface CoachPlanSource {
    fun observeCurrent(): Flow<CoachPlanContext?>
}

class RoomCoachPlanSource @Inject constructor(
    private val plans: TrainingPlanRepository,
    private val time: TimeProvider,
) : CoachPlanSource {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCurrent(): Flow<CoachPlanContext?> = time.today
        .map { it.weekStart() }
        .distinctUntilChanged()
        .flatMapLatest { weekStart ->
            plans.observeWeekRecord(weekStart)
                .map { record ->
                    record?.let {
                        CoachPlanContext(
                            plan = it.plan,
                            inputsHash = it.inputsHash
                                ?: "${it.plan.weekStart}|${it.plan.engineVersion}|${it.plan.weeklyMinutes}",
                            version = it.version,
                        )
                    }
                }
                .distinctUntilChanged()
        }
}
