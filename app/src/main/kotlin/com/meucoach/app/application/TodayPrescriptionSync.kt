package com.meucoach.app.application

import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.RaceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps today's prescription fresh while the app process is alive.
 *
 * Writes are append-only and deduped by `(date, engineVersion, inputsHash)`.
 * A check-in, an import, a load sync or a completion changes the observed
 * signals, the resolver appends a new version once, and every surface that
 * observes the day (Hoje, Coach, the active notification) sees the same
 * decision without waiting for a specific screen to open.
 */
@Singleton
class TodayPrescriptionSync @Inject constructor(
    private val signals: TrainingSignalSource,
    private val profiles: ProfileRepository,
    private val plans: TrainingPlanRepository,
    private val races: RaceEventRepository,
    private val refresh: RefreshTodayPrescription,
    private val time: TimeProvider,
) {
    private data class Inputs(
        val today: LocalDate,
        val persisted: PersistedTrainingSignals,
        val profile: AthleteProfile?,
        val plan: WeeklyPlan?,
        val races: List<RaceEvent>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            time.today
                .flatMapLatest { today ->
                    combine(
                        signals.observeSignals(),
                        profiles.observeProfile(),
                        plans.observeWeek(today.weekStart()),
                        races.observeAll(),
                    ) { persisted, profile, plan, raceEvents ->
                        Inputs(today, persisted, profile, plan, raceEvents)
                    }
                        .distinctUntilChanged()
                        .mapLatest { inputs ->
                            val profile = inputs.profile ?: return@mapLatest
                            val plan = inputs.plan ?: return@mapLatest
                            refresh.resolve(
                                today = inputs.today,
                                profile = profile,
                                plan = plan,
                                races = inputs.races,
                                persisted = inputs.persisted,
                                checkedAt = time.now(),
                            )
                        }
                }
                .collect { }
        }
    }
}
