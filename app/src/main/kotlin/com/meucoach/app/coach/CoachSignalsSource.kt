package com.meucoach.app.coach

import com.meucoach.app.application.PersistedTrainingSignals
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * Reactive read of the persisted training signals shared by F2, F4 and the
 * review (Bloco B). Guards, progress and review observe the same emission
 * instead of each one reading check-ins, metrics, activities and executions in
 * isolation. It never writes prescription, plan or profile.
 */
interface CoachSignalsSource {
    fun observeSignals(): Flow<PersistedTrainingSignals>
}

class RoomCoachSignalsSource @Inject constructor(
    private val checkIns: CheckInRepository,
    private val metrics: DailyMetricsRepository,
    private val activities: ActivityRepository,
    private val executions: SessionExecutionRepository,
) : CoachSignalsSource {
    override fun observeSignals(): Flow<PersistedTrainingSignals> = combine(
        checkIns.observeAll(),
        metrics.observeAll(),
        activities.observeActivitySummaries(),
        activities.observeLastImportedAt(),
        executions.observeAll(),
    ) { checkIns, metrics, summaries, lastImportedAt, executions ->
        PersistedTrainingSignals(
            checkIns = checkIns,
            metrics = metrics,
            activities = summaries,
            lastImportedAt = lastImportedAt,
            executions = executions,
        )
    }.distinctUntilChanged()
}
