package com.meucoach.app.coach

import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.WeeklyPlan
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

data class CoachPlanContext(
    val plan: WeeklyPlan,
    val inputsHash: String,
)

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
            flow {
                val plan = plans.getWeek(weekStart)
                if (plan == null) {
                    emit(null)
                } else {
                    val hash = plans.getWeekRecord(weekStart)?.inputsHash
                        ?: "${plan.weekStart}|${plan.engineVersion}|${plan.weeklyMinutes}"
                    emit(CoachPlanContext(plan, hash))
                }
            }
        }
}
