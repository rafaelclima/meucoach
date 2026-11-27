package com.meucoach.app.ui.today

import android.content.Context
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.application.CompleteSession
import com.meucoach.app.application.GetTodayDecision
import com.meucoach.app.application.GetTodayPrescription
import com.meucoach.app.application.ReplaceManualCompletion
import com.meucoach.app.application.ReplanContextBuilder
import com.meucoach.app.application.TrainingSignalSource
import com.meucoach.app.application.UndoRaceAutoCompletion
import com.meucoach.app.application.UpgradeWeekOnOpening
import com.meucoach.app.health.HealthSyncCoordinator
import com.meucoach.app.health.HealthSyncGate
import com.meucoach.app.importer.RaceAutoCompletionStore
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.app.time.TimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.health.HealthConnectReadResult
import com.meucoach.core.data.health.HealthConnectStatus
import com.meucoach.core.data.health.HealthDailySource
import com.meucoach.core.data.health.HealthSyncService
import com.meucoach.core.data.health.HealthSyncStore
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.NewRaceEvent
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.Sex
import com.meucoach.core.model.Sport
import com.meucoach.core.model.WeeklyAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TodayViewModelPersistTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: MeuCoachDatabase
    private lateinit var checkIns: CheckInRepository
    private lateinit var metrics: DailyMetricsRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var activities: ActivityRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var prescriptions: DailyPrescriptionRepository
    private lateinit var raceEvents: RaceEventRepository
    private var nextActivityId = 0
    private val viewModels = mutableListOf<TodayViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        shadowOf(Looper.getMainLooper()).idle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        val clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC)
        checkIns = CheckInRepository(database, clock)
        metrics = DailyMetricsRepository(database, clock)
        profiles = ProfileRepository(database, clock)
        activities =
            ActivityRepository(
                database = database,
                clock = clock,
                zoneId = ZoneId.of("America/Sao_Paulo"),
                idGenerator = { "activity-${++nextActivityId}" },
            )
        plans = TrainingPlanRepository(database, clock)
        prescriptions = DailyPrescriptionRepository(database, clock)
        raceEvents = RaceEventRepository(database, clock)
    }

    @After
    fun tearDown() {
        // Cancel the ViewModel scopes before closing the in-memory database: a
        // collection still subscribed to the plan can leave a Room transaction
        // in flight and leak "no current transaction" into the next test.
        viewModels.forEach { it.viewModelScope.cancel() }
        viewModels.clear()
        shadowOf(Looper.getMainLooper()).idle()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `does not generate a plan while the sync gate is open`(): Unit = runBlocking {
        val coordinator = syncCoordinator()
        val timeProvider = FakeTimeProvider(
            Instant.parse("2026-11-02T12:00:00Z"),
            ZoneId.of("America/Sao_Paulo"),
        )
        val today = timeProvider.currentDate()
        profiles.saveProfile(profile(today))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )

        val viewModel = createViewModel(timeProvider, coordinator, settleGate = false)
        val state = withTimeout(10_000) { viewModel.uiState.first { it.isSyncingData } }

        try {
            assertThat(state.plan).isNull()
            assertThat(database.trainingPlanDao().getAllPlans()).isEmpty()
        } finally {
            coordinator.syncOnResume()
            withTimeout(10_000) {
                coordinator.gate.first { it is HealthSyncGate.Ready || it is HealthSyncGate.Failed }
            }
        }
    }

    @Test
    fun `persists a Sunday preview and reuses it on Monday morning`(): Unit = runBlocking {
        val sundayProvider = FakeTimeProvider(
            Instant.parse("2026-11-01T12:00:00Z"),
            ZoneId.of("America/Sao_Paulo"),
        )
        val sunday = sundayProvider.currentDate()
        val nextMonday = LocalDate.of(2026, 11, 2)
        profiles.saveProfile(profile(sunday))
        checkIns.save(
            DailyCheckIn(sunday, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        val coordinator = syncCoordinator()
        coordinator.syncOnResume()
        withTimeout(10_000) { coordinator.gate.first { it is HealthSyncGate.Ready } }

        val sundayViewModel = createViewModel(sundayProvider, coordinator, settleGate = false)
        val sundayState = withTimeout(10_000) {
            sundayViewModel.uiState.first { it.nextWeekPlan != null }
        }

        assertThat(sundayState.nextWeekPlan?.weekStart).isEqualTo(nextMonday)
        assertThat(sundayState.nextWeekPlan?.createdOn).isEqualTo(sunday)
        assertThat(plans.getWeek(nextMonday)).isNotNull

        val mondayProvider = FakeTimeProvider(
            Instant.parse("2026-11-02T07:00:00Z"),
            ZoneId.of("America/Sao_Paulo"),
        )
        val mondayViewModel = createViewModel(mondayProvider, coordinator, settleGate = false)
        val mondayState = withTimeout(10_000) {
            mondayViewModel.uiState.first { it.plan?.weekStart == nextMonday }
        }

        assertThat(mondayState.plan?.createdOn).isEqualTo(sunday)
        assertThat(mondayState.nextWeekPlan).isNull()
    }

    @Test
    fun `generates the plan once the sync gate settles ready`(): Unit = runBlocking {
        val coordinator = syncCoordinator()
        val timeProvider = FakeTimeProvider(
            Instant.parse("2026-11-02T12:00:00Z"),
            ZoneId.of("America/Sao_Paulo"),
        )
        val today = timeProvider.currentDate()
        profiles.saveProfile(profile(today))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        coordinator.syncOnResume()
        withTimeout(10_000) { coordinator.gate.first { it is HealthSyncGate.Ready } }

        val viewModel = createViewModel(timeProvider, coordinator)
        val state = withTimeout(10_000) {
            viewModel.uiState.first { it.plan != null && it.plan.sessions.isNotEmpty() }
        }

        assertThat(state.isSyncingData).isFalse()
        assertThat(plans.getWeek(state.plan!!.weekStart)).isNotNull
        assertThat(state.plan!!.rationale).doesNotContain("sincronização")
    }

    @Test
    fun `failed gate generates with an explicit incomplete note`(): Unit = runBlocking {
        val coordinator = syncCoordinator(status = HealthConnectStatus.UNAVAILABLE)
        val timeProvider = FakeTimeProvider(
            Instant.parse("2026-11-02T12:00:00Z"),
            ZoneId.of("America/Sao_Paulo"),
        )
        val today = timeProvider.currentDate()
        profiles.saveProfile(profile(today))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        coordinator.syncOnResume()
        withTimeout(10_000) { coordinator.gate.first { it is HealthSyncGate.Failed } }

        val viewModel = createViewModel(timeProvider, coordinator)
        val state = withTimeout(10_000) {
            viewModel.uiState.first { it.plan != null && it.plan.sessions.isNotEmpty() }
        }

        assertThat(state.isSyncingData).isFalse()
        assertThat(state.plan!!.rationale).contains("sincronização")
        assertThat(state.plan!!.ruleIds).contains("R-DATA-01")
        assertThat(state.plan!!.evidenceIds).contains("EV-IMPELLIZZERI-2020")
    }

    @Test
    fun `upgrades a stale stored week on opening`(): Unit = runBlocking {
        val coordinator = syncCoordinator()
        val timeProvider = FakeTimeProvider(
            Instant.parse("2026-11-04T12:00:00Z"),
            ZoneId.of("America/Sao_Paulo"),
        )
        val today = timeProvider.currentDate()
        val weekStart = LocalDate.of(2026, 11, 2)
        val friday = weekStart.plusDays(4)
        val sunday = weekStart.plusDays(6)
        profiles.saveProfile(profile(today))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        plans.saveWeek(
            com.meucoach.core.domain.prescription.WeeklyPlan(
                weekStart = weekStart,
                sessions = listOf(
                    com.meucoach.core.domain.prescription.PlannedSession(
                        date = today,
                        type = com.meucoach.core.domain.prescription.WorkoutType.EASY,
                        title = "Fácil e conversável",
                        durationMin = 40,
                        rationale = "Base.",
                        ruleIds = listOf("R-PLAN-03"),
                        evidenceIds = listOf("EV-SEILER-2010"),
                    ),
                    com.meucoach.core.domain.prescription.PlannedSession(
                        date = friday,
                        type = com.meucoach.core.domain.prescription.WorkoutType.EASY,
                        title = "Fácil de sexta",
                        durationMin = 60,
                        rationale = "Base.",
                        ruleIds = listOf("R-PLAN-03"),
                        evidenceIds = listOf("EV-SEILER-2010"),
                    ),
                    com.meucoach.core.domain.prescription.PlannedSession(
                        date = sunday,
                        type = com.meucoach.core.domain.prescription.WorkoutType.LONG,
                        title = "Longão",
                        durationMin = 90,
                        rationale = "Base.",
                        ruleIds = listOf("R-PLAN-03"),
                        evidenceIds = listOf("EV-PFITZINGER-2008"),
                    ),
                ),
                weeklyMinutes = 190,
                longRunMinutes = 90,
                qualityCount = 0,
                rationale = "Semana antiga.",
                confidence = com.meucoach.core.model.DataConfidence.MEDIUM,
                ruleIds = listOf("R-PLAN-03"),
                evidenceIds = listOf("EV-PFITZINGER-2008"),
                createdOn = weekStart,
                engineVersion = "weekly-plan-v6",
            ),
        )
        coordinator.syncOnResume()
        withTimeout(10_000) { coordinator.gate.first { it is HealthSyncGate.Ready } }

        val viewModel = createViewModel(timeProvider, coordinator)
        val state = withTimeout(30_000) {
            viewModel.uiState.first {
                it.plan != null && it.plan.sessions.isNotEmpty() && it.completionMessage != null
            }
        }

        assertThat(state.plan!!.sessions.first { it.date == friday }.durationMin).isEqualTo(40)
        val record = plans.getWeekRecord(weekStart)!!
        assertThat(record.version).isEqualTo(2)
        assertThat(record.generatorVersion).isEqualTo(
            com.meucoach.core.domain.prescription.WeeklyPlanGenerator.ENGINE_VERSION,
        )
        assertThat(plans.getLatestRevision(weekStart)!!.trigger)
            .isEqualTo(com.meucoach.core.domain.replan.PlanUpgrade.ENGINE_UPGRADE_TRIGGER)
        assertThat(state.completionMessage).contains("nova versão do coach")
    }

    @Test
    fun `restores the planned quality after the check-in arrives`(): Unit = runBlocking {
        val coordinator = syncCoordinator()
        val timeProvider = FakeTimeProvider(
            Instant.parse("2026-11-03T12:00:00Z"),
            ZoneId.of("America/Sao_Paulo"),
        )
        val today = timeProvider.currentDate()
        val weekStart = LocalDate.of(2026, 11, 2)
        profiles.saveProfile(profile(today, com.meucoach.core.model.Experience.INTERMEDIATE))
        seedReliableLoad(today)
        plans.saveWeek(qualityWeek(today, weekStart))
        coordinator.syncOnResume()
        withTimeout(10_000) { coordinator.gate.first { it is HealthSyncGate.Ready } }

        val viewModel = createViewModel(timeProvider, coordinator)
        // C4-31 (ADR 0044 §2.5): without the check-in the day downgrades the
        // planned quality and shows no "Atualizado" note.
        val downgraded = withTimeout(10_000) {
            viewModel.uiState.first {
                it.prescription?.adjustment ==
                    com.meucoach.core.domain.prescription.PrescriptionAdjustment.DOWNGRADE
            }
        }
        assertThat(downgraded.prescription?.prescribedType)
            .isEqualTo(com.meucoach.core.domain.prescription.WorkoutType.EASY)
        assertThat(downgraded.qualityRestored).isFalse()

        // The check-in arrives before the session is done: the day restores the
        // planned quality in a new version and signals it to the UI.
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        val restored = withTimeout(10_000) {
            viewModel.uiState.first {
                it.prescription?.adjustment ==
                    com.meucoach.core.domain.prescription.PrescriptionAdjustment.KEEP &&
                    it.prescription?.prescribedType ==
                    com.meucoach.core.domain.prescription.WorkoutType.QUALITY &&
                    it.qualityRestored
            }
        }

        assertThat(restored.qualityRestored).isTrue()
        val versions = prescriptions.getVersions(today)
        assertThat(versions).hasSize(2)
        assertThat(versions.last().adjustment)
            .isEqualTo(com.meucoach.core.domain.prescription.PrescriptionAdjustment.KEEP)
    }

    @Test
    fun `keeps the restored flag off while the quality stays downgraded`(): Unit = runBlocking {
        val coordinator = syncCoordinator()
        val timeProvider = FakeTimeProvider(
            Instant.parse("2026-11-03T12:00:00Z"),
            ZoneId.of("America/Sao_Paulo"),
        )
        val today = timeProvider.currentDate()
        val weekStart = LocalDate.of(2026, 11, 2)
        profiles.saveProfile(profile(today, com.meucoach.core.model.Experience.INTERMEDIATE))
        plans.saveWeek(qualityWeek(today, weekStart))
        coordinator.syncOnResume()
        withTimeout(10_000) { coordinator.gate.first { it is HealthSyncGate.Ready } }

        val viewModel = createViewModel(timeProvider, coordinator)
        val state = withTimeout(10_000) {
            viewModel.uiState.first { it.prescription != null }
        }

        assertThat(state.prescription?.adjustment)
            .isEqualTo(com.meucoach.core.domain.prescription.PrescriptionAdjustment.DOWNGRADE)
        assertThat(state.qualityRestored).isFalse()
    }

    @Test
    fun `leaves a current week untouched on opening`(): Unit = runBlocking {
        val coordinator = syncCoordinator()
        val timeProvider = FakeTimeProvider(
            Instant.parse("2026-11-04T12:00:00Z"),
            ZoneId.of("America/Sao_Paulo"),
        )
        val today = timeProvider.currentDate()
        val weekStart = LocalDate.of(2026, 11, 2)
        val friday = weekStart.plusDays(4)
        profiles.saveProfile(profile(today))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        plans.saveWeek(weekWith(today, weekStart))
        coordinator.syncOnResume()
        withTimeout(10_000) { coordinator.gate.first { it is HealthSyncGate.Ready } }

        val viewModel = createViewModel(timeProvider, coordinator)
        val state = withTimeout(10_000) {
            viewModel.uiState.first { it.plan != null && it.plan.sessions.isNotEmpty() }
        }

        assertThat(state.plan!!.sessions.first { it.date == today }.durationMin).isEqualTo(40)
        assertThat(plans.getWeekRecord(weekStart)!!.version).isEqualTo(1)
        assertThat(database.weeklyPlanRevisionDao().getAll()).isEmpty()
        assertThat(state.completionMessage).isNull()
    }

    @Test
    fun `open gate degrades to a noted plan after the timeout`(): Unit = runBlocking {
        val coordinator = syncCoordinator()
        val timeProvider = FakeTimeProvider(
            Instant.parse("2026-11-02T12:00:00Z"),
            ZoneId.of("America/Sao_Paulo"),
        )
        val today = timeProvider.currentDate()
        profiles.saveProfile(profile(today))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )

        val viewModel = createViewModel(timeProvider, coordinator, settleGate = false)
        withTimeout(10_000) { viewModel.uiState.first { it.isSyncingData } }
        dispatcher.scheduler.advanceTimeBy(TodayViewModel.DATA_GATE_TIMEOUT_MS + 1)

        val state = withTimeout(10_000) {
            viewModel.uiState.first { it.plan != null && it.plan.sessions.isNotEmpty() }
        }

        assertThat(state.isSyncingData).isFalse()
        assertThat(state.plan!!.rationale).contains("sincronização")
        assertThat(state.plan!!.ruleIds).contains("R-DATA-01")
    }

    @Test
    fun `generates and persists the current week once`(): Unit = runBlocking {
        val timeProvider = FakeTimeProvider(
            instant = Instant.parse("2026-11-02T12:00:00Z"),
            zone = ZoneId.of("America/Sao_Paulo"),
        )
        val today = timeProvider.currentDate()
        profiles.saveProfile(profile(today))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )

        val viewModel = createViewModel(timeProvider)
        val state = withTimeout(10_000) {
            viewModel.uiState.first { it.plan != null && it.plan.sessions.isNotEmpty() }
        }

        val stored = plans.getWeek(state.plan!!.weekStart)
        assertThat(stored).isNotNull
        assertThat(stored?.sessions).hasSameSizeAs(state.plan.sessions)
        val prescription = prescriptions.getLatest(today)
        assertThat(prescription).isNotNull
        assertThat(prescription?.date).isEqualTo(today)
        assertThat(prescription?.plannedType).isNotNull
        assertThat(state.prescription?.id).isEqualTo(prescription?.id)
    }

    @Test
    fun `uses completed previous week to add a second quality`(): Unit = runBlocking {
        val timeProvider = FakeTimeProvider(
            instant = Instant.parse("2026-10-26T12:00:00Z"),
            zone = ZoneId.of("America/Sao_Paulo"),
        )
        val today = timeProvider.currentDate()
        val weekStart = today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
        profiles.saveProfile(profile(today, experience = com.meucoach.core.model.Experience.INTERMEDIATE))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        // ADR 0047: the first half of BASE has no quality. Four demonstrated
        // load weeks put the plan in BUILD, where progression can add the
        // second quality.
        (1L..4L).forEach { weeksAgo ->
            plans.saveWeek(previousWeek(weekStart.minusWeeks(weeksAgo)))
        }
        seedLoad(today)

        val viewModel = createViewModel(timeProvider)
        val state = withTimeout(10_000) {
            viewModel.uiState.first { it.plan?.qualityCount == 2 }
        }

        assertThat(state.plan?.qualityCount).isEqualTo(2)
        assertThat(state.plan?.rationale).contains("concluiu pelo menos 80%")
    }

    @Test
    fun `uses the completed volume as the week base`(): Unit = runBlocking {
        val timeProvider = FakeTimeProvider(
            instant = Instant.parse("2026-10-26T12:00:00Z"),
            zone = ZoneId.of("America/Sao_Paulo"),
        )
        val today = timeProvider.currentDate()
        val weekStart = today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
        profiles.saveProfile(profile(today, experience = com.meucoach.core.model.Experience.INTERMEDIATE))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        plans.saveWeek(previousWeek(weekStart.minusWeeks(1)))
        val zone = ZoneId.of("America/Sao_Paulo")
        val previous = weekStart.minusWeeks(1)
        (0L..3L).forEach { offset ->
            seedRun(previous.plusDays(offset), minutes = 30, zone = zone)
        }
        listOf(8L, 10L, 12L, 14L, 16L, 18L, 20L, 22L, 24L, 27L).forEach { daysAgo ->
            seedRun(today.minusDays(daysAgo), minutes = 30, zone = zone)
        }

        val viewModel = createViewModel(timeProvider)
        val state = withTimeout(10_000) {
            viewModel.uiState.first { it.plan?.rationale?.contains("Volume: base de 120 min") == true }
        }

        // O piso de 45 do longão ancora o corte do alvo de 127; com o strides de
        // segunda (C4-18), o restante cabe em 82 min (20 + 31 + 31),
        // totalizando 127.
        assertThat(state.plan?.weeklyMinutes).isEqualTo(127)
        assertThat(state.plan?.ruleIds).contains("R-PLAN-01")
    }

    @Test
    fun `deloads after three consecutive load weeks from history`(): Unit = runBlocking {
        val timeProvider = FakeTimeProvider(
            instant = Instant.parse("2026-11-02T12:00:00Z"),
            zone = ZoneId.of("America/Sao_Paulo"),
        )
        val today = timeProvider.currentDate()
        val weekStart = today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
        profiles.saveProfile(profile(today))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        (1L..3L).forEach { weeksAgo ->
            plans.saveWeek(previousWeek(weekStart.minusWeeks(weeksAgo)))
            seedRun(
                weekStart.minusWeeks(weeksAgo).plusDays(1),
                minutes = 30,
                zone = ZoneId.of("America/Sao_Paulo"),
            )
        }

        val viewModel = createViewModel(timeProvider)
        val state = withTimeout(10_000) {
            viewModel.uiState.first { it.plan?.rationale?.contains("Semana de descarga") == true }
        }

        assertThat(state.plan?.ruleIds).contains("R-PLAN-04")
        assertThat(state.plan?.evidenceIds).contains("EV-ISSURIN-2010")
        assertThat(state.plan?.rationale).contains("após 3 semanas", "Fase BASE")
    }

    @Test
    fun `persists a session swap`(): Unit = runBlocking {
        val today = LocalDate.now()
        val weekStart = today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
        profiles.saveProfile(profile(today))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        plans.saveWeek(
            previousWeek(weekStart).copy(
                sessions = listOf(
                    com.meucoach.core.domain.prescription.PlannedSession(
                        date = weekStart,
                        type = com.meucoach.core.domain.prescription.WorkoutType.EASY,
                        title = "Fácil",
                        durationMin = 30,
                        rationale = "Base.",
                        ruleIds = listOf("R-ZONE-01"),
                        evidenceIds = listOf("EV-SEILER-2010"),
                    ),
                    com.meucoach.core.domain.prescription.PlannedSession(
                        date = weekStart.plusDays(1),
                        type = com.meucoach.core.domain.prescription.WorkoutType.QUALITY,
                        title = "Tempo",
                        durationMin = 30,
                        rationale = "Qualidade.",
                        ruleIds = listOf("R-PLAN-02"),
                        evidenceIds = listOf("EV-SEILER-2010"),
                    ),
                ),
                weeklyMinutes = 60,
                qualityCount = 1,
            ),
        )

        val viewModel = createViewModel()
        viewModel.swapSession(weekStart, weekStart.plusDays(1), today = weekStart)
        withTimeout(10_000) {
            while (
                plans.getWeek(weekStart)?.sessions?.first { it.date == weekStart }?.type !=
                com.meucoach.core.domain.prescription.WorkoutType.QUALITY
            ) {
                delay(20)
            }
        }

        val stored = plans.getWeek(weekStart)!!
        assertThat(stored.sessions.first { it.date == weekStart }.type)
            .isEqualTo(com.meucoach.core.domain.prescription.WorkoutType.QUALITY)
        assertThat(stored.sessions.first { it.date == weekStart.plusDays(1) }.type)
            .isEqualTo(com.meucoach.core.domain.prescription.WorkoutType.EASY)
    }

    @Test
    fun `summarizes a completed session with its linked run`(): Unit = runBlocking {
        val today = LocalDate.now()
        val weekStart = today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
        profiles.saveProfile(profile(today))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        plans.saveWeek(weekWith(today, weekStart))
        val zone = ZoneId.of("America/Sao_Paulo")
        val saved = activities.saveImportedActivity(
            ImportedActivity(
                source = ActivitySource.FIT,
                sport = Sport.RUNNING,
                startTime = today.atTime(7, 0).atZone(zone).toInstant(),
                durationSec = 2_400,
                distanceM = 6_000.0,
                averagePace = Pace.fromSecondsPerKm(400),
            ),
        ) as com.meucoach.core.data.repository.SaveActivityResult.Saved
        val executions = SessionExecutionRepository(database)
        executions.recordActivityExecution(
            plannedSessionId = com.meucoach.core.data.db.entity.plannedSessionId(weekStart, today),
            activityId = saved.id,
            startedAt = today.atTime(7, 0).atZone(zone).toInstant(),
            durationSec = 2_400,
            distanceM = 6_000.0,
        )

        val viewModel = createViewModel()
        val state = withTimeout(10_000) {
            viewModel.uiState.first { it.todayExecution != null }
        }

        val execution = state.todayExecution!!
        assertThat(execution.duration).isEqualTo("40:00")
        assertThat(execution.distance).isEqualTo("6,00 km")
        assertThat(execution.pace).isEqualTo("6:40 /km")
        assertThat(execution.activityId).isEqualTo(saved.id)
        assertThat(execution.withoutRunData).isFalse()
    }

    @Test
    fun `summarizes a manual completion without inventing run data`(): Unit = runBlocking {
        val today = LocalDate.now()
        val weekStart = today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
        profiles.saveProfile(profile(today))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        plans.saveWeek(weekWith(today, weekStart))
        SessionExecutionRepository(database).recordManualExecution(
            plannedSessionId = com.meucoach.core.data.db.entity.plannedSessionId(weekStart, today),
            durationMin = 40,
            rpe = 6,
        )

        val viewModel = createViewModel()
        val state = withTimeout(10_000) {
            viewModel.uiState.first { it.todayExecution != null }
        }

        val execution = state.todayExecution!!
        assertThat(execution.duration).isEqualTo("40:00")
        assertThat(execution.distance).isNull()
        assertThat(execution.pace).isNull()
        assertThat(execution.rpe).isEqualTo(6)
        assertThat(execution.activityId).isNull()
        assertThat(execution.withoutRunData).isTrue()
    }

    @Test
    fun `replaces a manual completion with today's imported run`(): Unit = runBlocking {
        val today = LocalDate.now()
        val weekStart = today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
        profiles.saveProfile(profile(today))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        plans.saveWeek(weekWith(today, weekStart))
        val executionRepository = SessionExecutionRepository(database)
        executionRepository.recordManualExecution(
            plannedSessionId = com.meucoach.core.data.db.entity.plannedSessionId(weekStart, today),
            durationMin = 40,
            rpe = 6,
        )
        val zone = ZoneId.of("America/Sao_Paulo")
        val saved = activities.saveImportedActivity(
            ImportedActivity(
                source = ActivitySource.FIT,
                sport = Sport.RUNNING,
                startTime = today.atTime(7, 0).atZone(zone).toInstant(),
                durationSec = 2_400,
                distanceM = 6_000.0,
                averagePace = Pace.fromSecondsPerKm(400),
            ),
        ) as com.meucoach.core.data.repository.SaveActivityResult.Saved

        val viewModel = createViewModel()
        val state = withTimeout(10_000) {
            viewModel.uiState.first { it.todayExecution?.withoutRunData == true }
        }
        assertThat(state.activitySuggestions.map { it.id }).contains(saved.id)

        viewModel.replaceManualWithActivity(today, saved.id)

        val updated = withTimeout(10_000) {
            viewModel.uiState.first {
                it.plan?.sessions?.single()?.completionEvidence ==
                    com.meucoach.core.model.CompletionEvidence.ACTIVITY
            }
        }
        assertThat(updated.todayExecution?.activityId).isEqualTo(saved.id)
        assertThat(updated.completionMessage).contains("substituído")
    }

    @Test
    fun `race day suppresses the training prescription and briefing`(): Unit = runBlocking {
        val today = LocalDate.now()
        val weekStart = today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
        profiles.saveProfile(profile(today))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        plans.saveWeek(weekWith(today, weekStart))
        raceEvents.create(NewRaceEvent(RaceDistance.TEN_K, today))

        val viewModel = createViewModel()
        val state = withTimeout(10_000) {
            viewModel.uiState.first { it.raceEvent != null }
        }

        assertThat(state.decision)
            .isInstanceOf(com.meucoach.core.domain.today.TodayDecision.Competition::class.java)
        assertThat(state.prescription).isNull()
        assertThat(state.briefing).isNull()
        assertThat(state.explanation).isNull()
        assertThat(state.todayExecution).isNull()
        assertThat(state.activitySuggestions).isEmpty()
        assertThat(prescriptions.getLatest(today)).isNull()
    }

    @Test
    fun `does not generate an ordinary session on a future race day`(): Unit = runBlocking {
        val timeProvider = FakeTimeProvider(
            instant = Instant.parse("2026-11-02T12:00:00Z"),
            zone = ZoneId.of("America/Sao_Paulo"),
        )
        val today = timeProvider.currentDate()
        val weekStart = today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
        val raceDate = weekStart.plusDays(4)
        profiles.saveProfile(profile(today))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        raceEvents.create(NewRaceEvent(RaceDistance.TEN_K, raceDate))

        val viewModel = createViewModel(timeProvider)
        val state = withTimeout(10_000) {
            viewModel.uiState.first { it.plan?.sessions?.isNotEmpty() == true }
        }

        assertThat(state.plan!!.sessions.map { it.date }).doesNotContain(raceDate)
        assertThat(state.eventDates).contains(raceDate)
    }

    @Test
    fun `blocks swapping into a race date across a week boundary`(): Unit = runBlocking {
        // Regression: on Sunday `tomorrow` lands in the next week, outside the
        // firstDate week range the race lookup used to cover. Fixed dates keep
        // this failing on any weekday while the range stays single-week.
        val today = LocalDate.parse("2026-11-15") // a Sunday
        val weekStart = today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
        profiles.saveProfile(profile(today))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        val tomorrow = today.plusDays(1)
        val week = weekWith(today, weekStart).copy(
            sessions = listOf(
                com.meucoach.core.domain.prescription.PlannedSession(
                    date = today,
                    type = com.meucoach.core.domain.prescription.WorkoutType.EASY,
                    title = "Fácil",
                    durationMin = 30,
                    rationale = "Base.",
                    ruleIds = listOf("R-ZONE-01"),
                    evidenceIds = listOf("EV-SEILER-2010"),
                ),
                com.meucoach.core.domain.prescription.PlannedSession(
                    date = tomorrow,
                    type = com.meucoach.core.domain.prescription.WorkoutType.QUALITY,
                    title = "Tempo",
                    durationMin = 30,
                    rationale = "Qualidade.",
                    ruleIds = listOf("R-PLAN-02"),
                    evidenceIds = listOf("EV-SEILER-2010"),
                ),
            ),
        )
        plans.saveWeek(week)
        raceEvents.create(NewRaceEvent(RaceDistance.TEN_K, tomorrow))

        val viewModel = createViewModel()
        withTimeout(10_000) { viewModel.uiState.first { it.plan != null } }
        viewModel.swapSession(today, tomorrow, today)
        delay(200)

        val stored = plans.getWeek(weekStart)!!
        assertThat(stored.sessions.first { it.date == today }.type)
            .isEqualTo(com.meucoach.core.domain.prescription.WorkoutType.EASY)
        assertThat(stored.sessions.first { it.date == tomorrow }.type)
            .isEqualTo(com.meucoach.core.domain.prescription.WorkoutType.QUALITY)
    }

    @Test
    fun `blocks swapping a session into a race date`(): Unit = runBlocking {
        val today = LocalDate.now()
        val weekStart = today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
        profiles.saveProfile(profile(today))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        val tomorrow = today.plusDays(1)
        val week = weekWith(today, weekStart).copy(
            sessions = listOf(
                com.meucoach.core.domain.prescription.PlannedSession(
                    date = today,
                    type = com.meucoach.core.domain.prescription.WorkoutType.EASY,
                    title = "Fácil",
                    durationMin = 30,
                    rationale = "Base.",
                    ruleIds = listOf("R-ZONE-01"),
                    evidenceIds = listOf("EV-SEILER-2010"),
                ),
                com.meucoach.core.domain.prescription.PlannedSession(
                    date = tomorrow,
                    type = com.meucoach.core.domain.prescription.WorkoutType.QUALITY,
                    title = "Tempo",
                    durationMin = 30,
                    rationale = "Qualidade.",
                    ruleIds = listOf("R-PLAN-02"),
                    evidenceIds = listOf("EV-SEILER-2010"),
                ),
            ),
        )
        plans.saveWeek(week)
        raceEvents.create(NewRaceEvent(RaceDistance.TEN_K, tomorrow))

        val viewModel = createViewModel()
        withTimeout(10_000) { viewModel.uiState.first { it.plan != null } }
        viewModel.swapSession(today, tomorrow, today)
        delay(200)

        val stored = plans.getWeek(weekStart)!!
        assertThat(stored.sessions.first { it.date == today }.type)
            .isEqualTo(com.meucoach.core.domain.prescription.WorkoutType.EASY)
        assertThat(stored.sessions.first { it.date == tomorrow }.type)
            .isEqualTo(com.meucoach.core.domain.prescription.WorkoutType.QUALITY)
    }

    /**
     * C4-31: a stored week with a planned quality today, so the day gate (not
     * the generation) decides between DOWNGRADE and KEEP.
     */
    private fun qualityWeek(today: LocalDate, weekStart: LocalDate) = com.meucoach.core.domain.prescription.WeeklyPlan(
        weekStart = weekStart,
        sessions = listOf(
            com.meucoach.core.domain.prescription.PlannedSession(
                date = today,
                type = com.meucoach.core.domain.prescription.WorkoutType.QUALITY,
                title = "Fartlek leve",
                durationMin = 40,
                rationale = "Oportunidade estrutural; o dia confirma.",
                ruleIds = listOf("R-PLAN-02", "R-PER-03"),
                evidenceIds = listOf("EV-SEILER-2010", "EV-DANIELS-2014"),
            ),
            com.meucoach.core.domain.prescription.PlannedSession(
                date = today.plusDays(2),
                type = com.meucoach.core.domain.prescription.WorkoutType.EASY,
                title = "Fácil e conversável",
                durationMin = 40,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
            ),
        ),
        weeklyMinutes = 80,
        longRunMinutes = null,
        qualityCount = 1,
        rationale = "Qualidade planejada; o dia confirma.",
        confidence = com.meucoach.core.model.DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-02", "R-PER-03"),
        evidenceIds = listOf("EV-SEILER-2010", "EV-DANIELS-2014"),
    )

    /**
     * C4-31: nine objective runs spread over the 28-day window (plus one
     * older run so the whole window counts as known history). Fresh source,
     * enough objective days, no subjective-only day: the ACWR is reliable and
     * near 1.0, so the day gate can keep a planned quality.
     */
    private suspend fun seedReliableLoad(today: LocalDate) {
        val zone = ZoneId.of("America/Sao_Paulo")
        val offsets = listOf(30L, 24L, 21L, 18L, 15L, 12L, 9L, 6L, 3L, 1L)
        offsets.forEachIndexed { index, daysAgo ->
            activities.saveImportedActivity(
                ImportedActivity(
                    source = ActivitySource.FIT,
                    sport = Sport.RUNNING,
                    startTime = today.minusDays(daysAgo).atTime(7, 0).atZone(zone).toInstant(),
                    durationSec = 1800,
                    distanceM = 5000.0,
                    sourceId = "seed-$index",
                    averageHeartRate = 150,
                    averagePace = Pace.fromSecondsPerKm(360),
                ),
            )
        }
    }

    private fun weekWith(today: LocalDate, weekStart: LocalDate) = com.meucoach.core.domain.prescription.WeeklyPlan(
        weekStart = weekStart,
        sessions = listOf(
            com.meucoach.core.domain.prescription.PlannedSession(
                date = today,
                type = com.meucoach.core.domain.prescription.WorkoutType.EASY,
                title = "Fácil e conversável",
                durationMin = 40,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
            ),
        ),
        weeklyMinutes = 40,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Sem progressão.",
        confidence = com.meucoach.core.model.DataConfidence.MEDIUM,
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
    )

    private suspend fun createViewModel(
        timeProvider: TimeProvider = FakeTimeProvider(),
        coordinator: HealthSyncCoordinator = syncCoordinator(),
        settleGate: Boolean = true,
    ): TodayViewModel {
        if (settleGate) {
            coordinator.syncOnResume()
            withTimeout(10_000) {
                coordinator.gate.first { it is HealthSyncGate.Ready || it is HealthSyncGate.Failed }
            }
        }
        val executions = SessionExecutionRepository(database)
        val contexts = ReplanContextBuilder(
            plans = plans,
            profiles = profiles,
            checkIns = checkIns,
            signals = TrainingSignalSource(checkIns, metrics, activities, executions),
            raceEvents = raceEvents,
            timeProvider = timeProvider,
        )
        return TodayViewModel(
            checkIns,
            metrics,
            profiles,
            activities,
            plans,
            executions,
            raceEvents,
            CompleteSession(plans, activities, executions, raceEvents),
            ReplaceManualCompletion(plans, activities, executions),
            UndoRaceAutoCompletion(
                raceEvents,
                RaceAutoCompletionStore(
                    ApplicationProvider.getApplicationContext(),
                    "persist-race-${System.nanoTime()}",
                ),
            ),
            timeProvider,
            GetTodayDecision(raceEvents, GetTodayPrescription(prescriptions)),
            coordinator,
            UpgradeWeekOnOpening(contexts, plans),
            prescriptions,
        ).also { viewModels += it }
    }

    private fun syncCoordinator(status: HealthConnectStatus = HealthConnectStatus.AVAILABLE): HealthSyncCoordinator {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = FakeSource(status = status)
        val store = HealthSyncStore(context, prefsName = "today-gate-${System.nanoTime()}")
        val service = HealthSyncService(
            source = source,
            metricsRepository = metrics,
            profileRepository = profiles,
            syncStore = store,
            clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC),
        )
        return HealthSyncCoordinator(source, service, store, CoroutineScope(SupervisorJob() + dispatcher))
    }

    private class FakeSource(var status: HealthConnectStatus = HealthConnectStatus.AVAILABLE) : HealthDailySource {
        override val requiredPermissions: Set<String> = setOf("sleep", "hr")

        override suspend fun status(): HealthConnectStatus = status

        override suspend fun grantedPermissions(): Set<String> = requiredPermissions

        override suspend fun read(from: LocalDate, to: LocalDate, zoneId: java.time.ZoneId): HealthConnectReadResult =
            HealthConnectReadResult(
                days = emptyList(),
                missingPermissions = emptySet(),
            )
    }

    private fun previousWeek(weekStart: LocalDate) = com.meucoach.core.domain.prescription.WeeklyPlan(
        weekStart = weekStart,
        sessions = listOf(
            com.meucoach.core.domain.prescription.PlannedSession(
                date = weekStart,
                type = com.meucoach.core.domain.prescription.WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = com.meucoach.core.domain.prescription.SessionStatus.DONE,
                completionEvidence = com.meucoach.core.model.CompletionEvidence.ACTIVITY,
                activityId = "previous-$weekStart-0",
            ),
            com.meucoach.core.domain.prescription.PlannedSession(
                date = weekStart.plusDays(1),
                type = com.meucoach.core.domain.prescription.WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = com.meucoach.core.domain.prescription.SessionStatus.DONE,
                completionEvidence = com.meucoach.core.model.CompletionEvidence.ACTIVITY,
                activityId = "previous-$weekStart-1",
            ),
            com.meucoach.core.domain.prescription.PlannedSession(
                date = weekStart.plusDays(2),
                type = com.meucoach.core.domain.prescription.WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = com.meucoach.core.domain.prescription.SessionStatus.DONE,
                completionEvidence = com.meucoach.core.model.CompletionEvidence.ACTIVITY,
                activityId = "previous-$weekStart-2",
            ),
            com.meucoach.core.domain.prescription.PlannedSession(
                date = weekStart.plusDays(3),
                type = com.meucoach.core.domain.prescription.WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = com.meucoach.core.domain.prescription.SessionStatus.DONE,
                completionEvidence = com.meucoach.core.model.CompletionEvidence.ACTIVITY,
                activityId = "previous-$weekStart-3",
            ),
        ),
        weeklyMinutes = 120,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Semana anterior.",
        confidence = com.meucoach.core.model.DataConfidence.MEDIUM,
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
    )

    private suspend fun seedRun(date: LocalDate, minutes: Int, zone: ZoneId) {
        activities.saveImportedActivity(
            ImportedActivity(
                source = ActivitySource.FIT,
                sport = Sport.RUNNING,
                startTime = date.atTime(12, 0).atZone(zone).toInstant(),
                durationSec = minutes * 60,
                distanceM = minutes * 150.0,
                averageHeartRate = 150,
                averagePace = Pace.fromSecondsPerKm(400),
            ),
        )
    }

    private suspend fun seedLoad(today: LocalDate) {
        val zone = ZoneId.of("America/Sao_Paulo")
        listOf(1L, 4L, 6L, 8L, 11L, 15L, 18L, 22L, 25L, 27L).forEach { daysAgo ->
            activities.saveImportedActivity(
                ImportedActivity(
                    source = ActivitySource.FIT,
                    sport = Sport.RUNNING,
                    startTime = today
                        .minusDays(daysAgo)
                        .atStartOfDay(zone)
                        .toInstant()
                        .plusSeconds(12 * 3_600),
                    durationSec = 1_800 + daysAgo.toInt() * 30,
                    distanceM = 5_000.0,
                    averageHeartRate = 150,
                    averagePace = Pace.fromSecondsPerKm(360 + daysAgo.toInt() * 10),
                ),
            )
        }
    }

    private fun profile(
        today: LocalDate,
        experience: com.meucoach.core.model.Experience = com.meucoach.core.model.Experience.BEGINNER,
    ) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        experience = experience,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(
            mapOf(
                java.time.DayOfWeek.MONDAY to 40,
                java.time.DayOfWeek.WEDNESDAY to 40,
                java.time.DayOfWeek.FRIDAY to 40,
                java.time.DayOfWeek.SUNDAY to 90,
            ),
        ),
    )
}
