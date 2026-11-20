package com.meucoach.app.ui.coach

import android.content.Context
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.application.ApplyReplan
import com.meucoach.app.application.EvaluateReplan
import com.meucoach.app.application.REPLAN_TODAY
import com.meucoach.app.application.REPLAN_WEEK_START
import com.meucoach.app.application.ReplanContextBuilder
import com.meucoach.app.application.TrainingSignalSource
import com.meucoach.app.application.UndoReplan
import com.meucoach.app.application.easySession
import com.meucoach.app.application.qualitySession
import com.meucoach.app.application.replanCheckIn
import com.meucoach.app.application.replanPlan
import com.meucoach.app.application.replanProfile
import com.meucoach.app.coach.CoachConsentStore
import com.meucoach.app.coach.CoachGuardsContext
import com.meucoach.app.coach.CoachNextContext
import com.meucoach.app.coach.CoachPlanContext
import com.meucoach.app.coach.CoachProgressContext
import com.meucoach.app.coach.CoachSources
import com.meucoach.app.replan.ProfilePlanEvaluationStore
import com.meucoach.app.replan.ReplanControllerFactory
import com.meucoach.app.replan.ReplanOperations
import com.meucoach.app.replan.ReplanSuggestionStore
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.app.ui.replan.LessTimeChange
import com.meucoach.core.data.coach.CoachMemoryRepository
import com.meucoach.core.data.coach.FakeCoachApi
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.coach.CoachMessageKind
import com.meucoach.core.domain.coach.CoachNextSubject
import com.meucoach.core.domain.coach.CoachRawResponse
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.replan.PlanReplanner
import com.meucoach.core.domain.review.WeeklyAdherence
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewObservation
import com.meucoach.core.domain.review.WeeklyReviewObservationKind
import com.meucoach.core.domain.review.WeeklyReviewStatus
import com.meucoach.core.domain.review.WeeklyVolume
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.WeeklyAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.io.IOException
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoachViewModelTest {
    private lateinit var consent: CoachConsentStore
    private lateinit var plans: MutableStateFlow<CoachPlanContext?>
    private lateinit var reviews: MutableStateFlow<WeeklyReview?>
    private lateinit var next: MutableStateFlow<CoachNextContext>
    private lateinit var guards: MutableStateFlow<CoachGuardsContext>
    private lateinit var progress: MutableStateFlow<CoachProgressContext>
    private lateinit var database: MeuCoachDatabase
    private lateinit var memory: CoachMemoryRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var checkIns: CheckInRepository
    private lateinit var planRepository: TrainingPlanRepository
    private lateinit var replanFactory: ReplanControllerFactory
    private val dispatcher = UnconfinedTestDispatcher()
    private val viewModels = mutableListOf<CoachViewModel>()
    private val appScopes = mutableListOf<CoroutineScope>()
    private val clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC)
    private val timeProvider = FakeTimeProvider(clock.instant(), ZoneOffset.UTC)

    private fun plan(): WeeklyPlan = WeeklyPlan(
        weekStart = LocalDate.of(2026, 11, 16),
        sessions = listOf(
            PlannedSession(
                date = LocalDate.of(2026, 11, 17),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.PLANNED,
            ),
        ),
        weeklyMinutes = 30,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Semana base",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        createdOn = LocalDate.of(2026, 11, 16),
        engineVersion = WeeklyPlanGenerator.ENGINE_VERSION,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        consent = CoachConsentStore(context, "coach-vm-${System.nanoTime()}")
        plans = MutableStateFlow(null)
        reviews = MutableStateFlow(null)
        next = MutableStateFlow(emptyNextContext())
        guards = MutableStateFlow(CoachGuardsContext(emptyList()))
        progress = MutableStateFlow(CoachProgressContext(emptyList(), null, 0, null, LocalDate.of(2026, 11, 16)))
        database = Room
            .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        memory = CoachMemoryRepository(database, clock)
        profiles = ProfileRepository(database, clock)
        checkIns = CheckInRepository(database, clock)
        planRepository = TrainingPlanRepository(database, clock)
        val contexts = ReplanContextBuilder(
            plans = planRepository,
            profiles = profiles,
            checkIns = checkIns,
            signals = TrainingSignalSource(
                checkIns = checkIns,
                metrics = DailyMetricsRepository(database, clock),
                activities = ActivityRepository(database = database, clock = clock, zoneId = ZoneOffset.UTC),
                executions = SessionExecutionRepository(database, clock),
            ),
            raceEvents = RaceEventRepository(database, clock),
            timeProvider = timeProvider,
        )
        val evaluate = EvaluateReplan(contexts, planRepository)
        replanFactory = ReplanControllerFactory(
            operations = ReplanOperations(
                evaluate = evaluate,
                apply = ApplyReplan(planRepository, contexts, evaluate),
                undo = UndoReplan(planRepository),
            ),
            plans = planRepository,
            profiles = profiles,
            store = ReplanSuggestionStore(context, "coach-replan-${System.nanoTime()}"),
            profileResults = ProfilePlanEvaluationStore(),
            timeProvider = timeProvider,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        viewModels.clear()
        // Room writes launched in appScope can still touch the database; cancel
        // them before closing it so `Dispatchers.resetMain` never races a write.
        appScopes.forEach { it.cancel() }
        appScopes.clear()
        dispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()
        database.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(api: FakeCoachApi): CoachViewModel {
        val appScope = CoroutineScope(dispatcher)
        appScopes += appScope
        val vm = CoachViewModel(
            consent = consent,
            sources = CoachSources(
                plans = object : com.meucoach.app.coach.CoachPlanSource {
                    override fun observeCurrent() = plans
                },
                reviews = object : com.meucoach.app.coach.CoachReviewSource {
                    override fun observeLastClosedWeek() = reviews
                },
                next = object : com.meucoach.app.coach.CoachNextSource {
                    override fun observeNext() = next
                },
                guards = object : com.meucoach.app.coach.CoachGuardsSource {
                    override fun observeGuards() = guards
                },
                progress = object : com.meucoach.app.coach.CoachProgressSource {
                    override fun observeProgress() = progress
                },
            ),
            api = api,
            memory = memory,
            appScope = appScope,
            replanFactory = replanFactory,
        )
        viewModels += vm
        return vm
    }

    private fun weeklyReview() = WeeklyReview(
        weekStart = LocalDate.of(2026, 11, 9),
        weekEnd = LocalDate.of(2026, 11, 15),
        status = WeeklyReviewStatus.AVAILABLE,
        adherence = WeeklyAdherence(
            planned = 4,
            done = 3,
            verified = 2,
            manual = 1,
            legacy = 0,
            skipped = 1,
        ),
        volume = WeeklyVolume(plannedMin = 120, executedMin = 90, percentOfPlan = 75, extraMin = 10),
        quality = null,
        intensity = null,
        intensitySessions = 0,
        readiness = null,
        sleep = null,
        observations = listOf(
            WeeklyReviewObservation(
                kind = WeeklyReviewObservationKind.ADHERENCE_PARTIAL,
                ruleId = "R-REVIEW-01",
                evidenceIds = emptyList(),
                confidence = DataConfidence.HIGH,
            ),
        ),
        ruleIds = listOf("R-REVIEW-01"),
        evidenceIds = emptyList(),
        confidence = DataConfidence.HIGH,
    )

    private fun awaitSettled(vm: CoachViewModel): CoachExplanationState = runBlocking {
        withTimeout(2000) {
            var state = vm.explanation.value
            while (state is CoachExplanationState.Loading ||
                (state is CoachExplanationState.Unavailable && consent.isConsented() && plans.value != null)
            ) {
                kotlinx.coroutines.delay(1)
                state = vm.explanation.value
            }
            state
        }
    }

    @Test
    fun `resposta valida vira Ready`() {
        consent.grant()
        plans.value = CoachPlanContext(plan(), "hash1")
        val vm = viewModel(
            FakeCoachApi(CoachRawResponse(title = "Foco", body = "Vamos manter os 30 min.")),
        )

        assertThat(awaitSettled(vm)).isInstanceOf(CoachExplanationState.Ready::class.java)
    }

    @Test
    fun `numero inventado vira Fallback com rationale do motor`() {
        consent.grant()
        plans.value = CoachPlanContext(plan(), "hash1")
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "T", body = "Corra a 6:10/km")))

        val state = awaitSettled(vm)
        assertThat(state).isInstanceOf(CoachExplanationState.Fallback::class.java)
        assertThat((state as CoachExplanationState.Fallback).planRationale).isEqualTo("Semana base")
    }

    @Test
    fun `falha de rede vira Fallback`() {
        consent.grant()
        plans.value = CoachPlanContext(plan(), "hash1")
        val vm = viewModel(FakeCoachApi(error = IOException("down")))

        assertThat(awaitSettled(vm)).isInstanceOf(CoachExplanationState.Fallback::class.java)
    }

    @Test
    fun `total semanal e valido mesmo fora das sessoes`() {
        consent.grant()
        plans.value = CoachPlanContext(plan().copy(weeklyMinutes = 114), "hash1")
        val vm = viewModel(
            FakeCoachApi(CoachRawResponse(title = "Foco", body = "114 min no total, mantidos.")),
        )

        assertThat(awaitSettled(vm)).isInstanceOf(CoachExplanationState.Ready::class.java)
    }

    @Test
    fun `replanejamento reemite a explicacao mesmo com o mesmo hash`(): Unit = runBlocking {
        consent.grant()
        plans.value = CoachPlanContext(plan(), "hash1", version = 1)
        val api = FakeCoachApi(CoachRawResponse(title = "Foco", body = "Ritmo controlado."))
        val vm = viewModel(api)
        assertThat(awaitSettled(vm)).isInstanceOf(CoachExplanationState.Ready::class.java)
        assertThat(api.calls).isEqualTo(1)

        plans.value = CoachPlanContext(
            plan = plan().copy(
                sessions = plan().sessions.map { it.copy(title = "Fácil ajustado", durationMin = 25) },
                weeklyMinutes = 25,
            ),
            inputsHash = "hash1",
            version = 2,
        )

        withTimeout(2_000) {
            while (api.calls < 2) {
                kotlinx.coroutines.delay(1)
            }
        }
        withTimeout(2_000) {
            while (vm.explanation.value !is CoachExplanationState.Ready) {
                kotlinx.coroutines.delay(1)
            }
        }
        assertThat(api.calls).isEqualTo(2)
    }

    @Test
    fun `progresso local aparece sem consentimento`(): Unit = runBlocking {
        progress.value = progressContext()
        val vm = viewModel(FakeCoachApi())

        withTimeout(2000) {
            while (vm.progress.value !is CoachProgressUiState.Ready) {
                kotlinx.coroutines.delay(1)
            }
        }
        assertThat(vm.progress.value).isInstanceOf(CoachProgressUiState.Ready::class.java)
    }

    @Test
    fun `progresso pronto agrega as semanas`(): Unit = runBlocking {
        consent.grant()
        progress.value = progressContext()
        val vm = viewModel(FakeCoachApi())

        withTimeout(2000) {
            while (vm.progress.value !is CoachProgressUiState.Ready) {
                kotlinx.coroutines.delay(1)
            }
        }
        val ready = vm.progress.value as CoachProgressUiState.Ready
        assertThat(ready.summary.availableWeeks).isEqualTo(1)
        assertThat(ready.summary.adherencePercent).isEqualTo(100)
    }

    private fun progressContext() = CoachProgressContext(
        weeks = listOf(
            com.meucoach.core.domain.review.WeeklyReview(
                weekStart = LocalDate.of(2026, 11, 9),
                weekEnd = LocalDate.of(2026, 11, 15),
                status = com.meucoach.core.domain.review.WeeklyReviewStatus.AVAILABLE,
                adherence = com.meucoach.core.domain.review.WeeklyAdherence(3, 3, 3, 0, 0, 0),
                volume = com.meucoach.core.domain.review.WeeklyVolume(114, 129, 113, 28),
                quality = null,
                intensity = null,
                intensitySessions = 0,
                readiness = null,
                sleep = null,
                observations = emptyList(),
                ruleIds = emptyList(),
                evidenceIds = emptyList(),
                confidence = com.meucoach.core.model.DataConfidence.HIGH,
            ),
        ),
        easyPercent28d = 46,
        intensitySessions28d = 4,
        profile = null,
        today = LocalDate.of(2026, 11, 16),
    )

    @Test
    fun `guardas locais aparecem sem consentimento`(): Unit = runBlocking {
        guards.value = CoachGuardsContext(listOf(recoveryGuard()))
        val vm = viewModel(FakeCoachApi())

        withTimeout(2000) {
            while (vm.guards.value !is CoachGuardsUiState.Ready) {
                kotlinx.coroutines.delay(1)
            }
        }
        assertThat(vm.guards.value).isInstanceOf(CoachGuardsUiState.Ready::class.java)
    }

    @Test
    fun `guardas prontas com consentimento`(): Unit = runBlocking {
        consent.grant()
        guards.value = CoachGuardsContext(listOf(recoveryGuard()))
        val vm = viewModel(FakeCoachApi())

        withTimeout(2000) {
            while (vm.guards.value !is CoachGuardsUiState.Ready) {
                kotlinx.coroutines.delay(1)
            }
        }
        val ready = vm.guards.value as CoachGuardsUiState.Ready
        assertThat(ready.context.guards).hasSize(1)
    }

    private fun recoveryGuard() = com.meucoach.core.domain.coach.CoachGuard(
        kind = com.meucoach.core.domain.coach.CoachGuardKind.RECOVERY,
        title = "Recuperação até 18/11.",
        detail = "Teto de 30 min, sem qualidade nem longão.",
    )

    @Test
    fun `next local aparece sem consentimento`(): Unit = runBlocking {
        next.value = nextContext()
        val vm = viewModel(FakeCoachApi())

        withTimeout(2000) {
            while (vm.next.value !is CoachNextUiState.Ready) {
                kotlinx.coroutines.delay(1)
            }
        }
        assertThat(vm.next.value).isInstanceOf(CoachNextUiState.Ready::class.java)
    }

    @Test
    fun `next vazio so quando existe plano e nada pendente`(): Unit = runBlocking {
        next.value = emptyNextContext(hasPlan = true)
        val vm = viewModel(FakeCoachApi())

        withTimeout(2000) {
            while (vm.next.value !is CoachNextUiState.Empty) {
                kotlinx.coroutines.delay(1)
            }
        }
        assertThat(vm.next.value).isInstanceOf(CoachNextUiState.Empty::class.java)
    }

    @Test
    fun `next sem plano vira onboarding`(): Unit = runBlocking {
        next.value = emptyNextContext(hasPlan = false)
        val vm = viewModel(FakeCoachApi())

        withTimeout(2000) {
            while (vm.next.value !is CoachNextUiState.Onboarding) {
                kotlinx.coroutines.delay(1)
            }
        }
        assertThat(vm.next.value).isInstanceOf(CoachNextUiState.Onboarding::class.java)
    }

    @Test
    fun `next pronto com a sessao vigente`(): Unit = runBlocking {
        consent.grant()
        next.value = nextContext()
        val vm = viewModel(FakeCoachApi())

        withTimeout(2000) {
            while (vm.next.value !is CoachNextUiState.Ready) {
                kotlinx.coroutines.delay(1)
            }
        }
        val ready = vm.next.value as CoachNextUiState.Ready
        val training = ready.context.subject as CoachNextSubject.Training
        assertThat(training.session.date).isEqualTo(LocalDate.of(2026, 11, 17))
        assertThat(ready.context.isToday).isFalse()
    }

    @Test
    fun `next com prova sem sessao fica pronto`(): Unit = runBlocking {
        next.value = raceNextContext()
        val vm = viewModel(FakeCoachApi())

        withTimeout(2000) {
            while (vm.next.value !is CoachNextUiState.Ready) {
                kotlinx.coroutines.delay(1)
            }
        }
        val ready = vm.next.value as CoachNextUiState.Ready
        assertThat(ready.context.subject).isInstanceOf(CoachNextSubject.Race::class.java)
    }

    private fun nextContext() = CoachNextContext(
        subject = CoachNextSubject.Training(
            PlannedSession(
                date = LocalDate.of(2026, 11, 17),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.PLANNED,
            ),
        ),
        today = LocalDate.of(2026, 11, 16),
        hasPlan = true,
        hasProfile = true,
    )

    private fun emptyNextContext(hasPlan: Boolean = true) = CoachNextContext(
        subject = null,
        today = LocalDate.of(2026, 11, 16),
        hasPlan = hasPlan,
        hasProfile = true,
    )

    private fun raceNextContext() = CoachNextContext(
        subject = CoachNextSubject.Race(
            com.meucoach.core.model.RaceEvent(
                id = "race-1",
                distance = com.meucoach.core.model.RaceDistance.FIVE_K,
                targetDate = LocalDate.of(2026, 11, 17),
                createdAt = Instant.parse("2026-11-16T12:00:00Z"),
                updatedAt = Instant.parse("2026-11-16T12:00:00Z"),
            ),
        ),
        today = LocalDate.of(2026, 11, 16),
        hasPlan = true,
        hasProfile = true,
    )

    @Test
    fun `sem consentimento nao chama a api`(): Unit = runBlocking {
        plans.value = CoachPlanContext(plan(), "hash1")
        val api = FakeCoachApi(error = IOException("nao deveria chamar"))
        val vm = viewModel(api)

        assertThat(vm.explanation.value).isInstanceOf(CoachExplanationState.Unavailable::class.java)
        assertThat(api.calls).isEqualTo(0)
        assertThat(database.coachMessageDao().getAll()).isEmpty()
    }

    @Test
    fun `retry dispara nova chamada`() {
        consent.grant()
        plans.value = CoachPlanContext(plan(), "hash1")
        val api = FakeCoachApi(CoachRawResponse(title = "Foco", body = "Vamos manter os 30 min."))
        val vm = viewModel(api)
        assertThat(awaitSettled(vm)).isInstanceOf(CoachExplanationState.Ready::class.java)
        assertThat(api.calls).isEqualTo(1)

        vm.retry()

        runBlocking {
            withTimeout(2000) {
                while (api.calls < 2) {
                    kotlinx.coroutines.delay(1)
                }
            }
        }
        assertThat(api.calls).isEqualTo(2)
    }

    @Test
    fun `resposta valida grava a mensagem do coach`(): Unit = runBlocking {
        consent.grant()
        plans.value = CoachPlanContext(plan(), "hash1")
        val vm = viewModel(
            FakeCoachApi(
                CoachRawResponse(title = "Foco", body = "Vamos manter os 30 min.", bullets = listOf("Base")),
            ),
        )
        assertThat(awaitSettled(vm)).isInstanceOf(CoachExplanationState.Ready::class.java)

        val message = database.coachMessageDao().getAll().single()

        assertThat(message.kind).isEqualTo("EXPLAIN")
        assertThat(message.inputsHash).isEqualTo("hash1")
        assertThat(message.title).isEqualTo("Foco")
        assertThat(message.bulletsJson).isEqualTo("[\"Base\"]")
    }

    @Test
    fun `fallback nao grava memoria`(): Unit = runBlocking {
        consent.grant()
        plans.value = CoachPlanContext(plan(), "hash1")
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "T", body = "Corra a 6:10/km")))

        assertThat(awaitSettled(vm)).isInstanceOf(CoachExplanationState.Fallback::class.java)
        assertThat(database.coachMessageDao().getAll()).isEmpty()
    }

    @Test
    fun `revisao valida grava mensagem e resumo da semana`(): Unit = runBlocking {
        consent.grant()
        reviews.value = weeklyReview()
        val vm = viewModel(
            FakeCoachApi(CoachRawResponse(title = "Semana passada", body = "3 de 4 sessões, 75% do volume.")),
        )

        assertThat(awaitReviewSettled(vm)).isInstanceOf(CoachReviewState.Ready::class.java)

        val message = database.coachMessageDao().getAll().single()
        assertThat(message.kind).isEqualTo("REVIEW")
        assertThat(message.inputsHash)
            .isEqualTo(com.meucoach.core.domain.coach.CoachReviewPromptBuilder.build(weeklyReview()).inputsHash)

        val summary = database.coachSummaryDao().getAll().single()
        assertThat(summary.weekStart).isEqualTo("2026-11-09")
        assertThat(summary.title).isEqualTo("Semana passada")
        assertThat(summary.inputsHash).isEqualTo(message.inputsHash)
    }

    @Test
    fun `revogar apaga a memoria do coach e preserva os treinos`(): Unit = runBlocking {
        planRepository.saveWeek(plan())
        consent.grant()
        plans.value = CoachPlanContext(plan(), "hash1")
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "Foco", body = "Vamos manter os 30 min.")))
        assertThat(awaitSettled(vm)).isInstanceOf(CoachExplanationState.Ready::class.java)
        assertThat(database.coachMessageDao().getAll()).hasSize(1)
        memory.recordSummary(
            weekStart = LocalDate.of(2026, 11, 9),
            inputsHash = "review-1",
            title = "Semana",
            body = "3 de 4",
            bullets = emptyList(),
        )
        assertThat(database.coachSummaryDao().getAll()).hasSize(1)

        vm.revoke()

        withTimeout(2_000) {
            while (database.coachMessageDao().getAll().isNotEmpty()) {
                kotlinx.coroutines.delay(1)
            }
        }
        assertThat(consent.isConsented()).isFalse()
        assertThat(vm.consented.value).isFalse()
        assertThat(vm.explanation.value).isInstanceOf(CoachExplanationState.Unavailable::class.java)
        assertThat(database.coachSummaryDao().getAll()).isEmpty()
        assertThat(database.trainingPlanDao().getPlan("2026-11-16")).isNotNull()
    }

    @Test
    fun `historico fica oculto sem consentimento`(): Unit = runBlocking {
        memory.recordMessage(CoachMessageKind.EXPLAIN, "hash-1", "Foco", "30 min", emptyList())
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "T", body = "ok")))

        assertThat(vm.history.value).isEqualTo(CoachHistoryState.Hidden)
        assertThat(database.coachMessageDao().getAll()).hasSize(1)
    }

    @Test
    fun `historico vazio explica quando nao ha memoria`() {
        consent.grant()
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "T", body = "ok")))

        assertThat(awaitHistory(vm)).isEqualTo(CoachHistoryState.Empty)
    }

    @Test
    fun `historico lista resumos e conversa com consentimento`(): Unit = runBlocking {
        consent.grant()
        memory.recordMessage(
            CoachMessageKind.EXPLAIN,
            "hash-1",
            "Foco",
            "30 min",
            listOf("Base"),
        )
        memory.recordSummary(LocalDate.of(2026, 11, 2), "review-1", "Semana 1", "3 de 4", emptyList())
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "T", body = "ok")))

        val ready = awaitHistory(vm) as CoachHistoryState.Ready

        assertThat(ready.messages.single().title).isEqualTo("Foco")
        assertThat(ready.messages.single().bullets).containsExactly("Base")
        assertThat(ready.summaries.single().weekStart).isEqualTo(LocalDate.of(2026, 11, 2))
    }

    @Test
    fun `apagar historico limpa apenas a memoria do coach`(): Unit = runBlocking {
        planRepository.saveWeek(plan())
        consent.grant()
        memory.recordMessage(CoachMessageKind.EXPLAIN, "hash-1", "Foco", "30 min", emptyList())
        memory.recordSummary(LocalDate.of(2026, 11, 2), "review-1", "Semana 1", "3 de 4", emptyList())
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "T", body = "ok")))
        assertThat(awaitHistory(vm)).isInstanceOf(CoachHistoryState.Ready::class.java)

        vm.clearHistory()

        awaitHistory(vm) { it is CoachHistoryState.Empty }
        assertThat(database.coachMessageDao().getAll()).isEmpty()
        assertThat(database.coachSummaryDao().getAll()).isEmpty()
        assertThat(database.trainingPlanDao().getPlan("2026-11-16")).isNotNull()
        assertThat(consent.isConsented()).isTrue()
    }

    @Test
    fun `historico oculta a revisao quando o resumo do mesmo payload existe`(): Unit = runBlocking {
        consent.grant()
        memory.recordMessage(CoachMessageKind.REVIEW, "review-1", "Revisão", "3 de 4", emptyList())
        memory.recordSummary(LocalDate.of(2026, 11, 2), "review-1", "Semana 1", "3 de 4", emptyList())
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "T", body = "ok")))

        val ready = awaitHistory(vm) as CoachHistoryState.Ready
        assertThat(ready.messages).isEmpty()
        assertThat(ready.summaries).hasSize(1)
    }

    @Test
    fun `historico nao repete a explicacao atual`(): Unit = runBlocking {
        consent.grant()
        memory.recordMessage(CoachMessageKind.EXPLAIN, "hash1", "Foco", "30 min", emptyList())
        plans.value = CoachPlanContext(plan(), "hash1")
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "Foco", body = "Vamos manter os 30 min.")))

        assertThat(awaitSettled(vm)).isInstanceOf(CoachExplanationState.Ready::class.java)
        awaitHistory(vm) { it is CoachHistoryState.Empty }
        assertThat(vm.history.value).isEqualTo(CoachHistoryState.Empty)
    }

    @Test
    fun `historico nao repete a explicacao atual quando a ia cai`(): Unit = runBlocking {
        consent.grant()
        memory.recordMessage(CoachMessageKind.EXPLAIN, "hash1", "Foco", "30 min", emptyList())
        plans.value = CoachPlanContext(plan(), "hash1")
        val vm = viewModel(FakeCoachApi(error = IOException("down")))

        assertThat(awaitSettled(vm)).isInstanceOf(CoachExplanationState.Fallback::class.java)
        awaitHistory(vm) { it is CoachHistoryState.Empty }
        assertThat(vm.history.value).isEqualTo(CoachHistoryState.Empty)
    }

    @Test
    fun `historico nao repete o resumo da semana revisada`(): Unit = runBlocking {
        consent.grant()
        val older = LocalDate.of(2026, 11, 2)
        memory.recordSummary(LocalDate.of(2026, 11, 9), "review-current", "Semana atual", "3 de 4", emptyList())
        memory.recordSummary(older, "review-old", "Semana anterior", "4 de 4", emptyList())
        reviews.value = weeklyReview()
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "Semana passada", body = "3 de 4 sessões.")))

        val ready = awaitHistory(vm) {
            it is CoachHistoryState.Ready && it.summaries.map { summary -> summary.weekStart } == listOf(older)
        } as CoachHistoryState.Ready
        assertThat(ready.summaries.map { it.weekStart }).containsExactly(older)
    }

    @Test
    fun `apagar mensagem individual mantem o restante e os treinos`(): Unit = runBlocking {
        planRepository.saveWeek(plan())
        consent.grant()
        memory.recordMessage(CoachMessageKind.EXPLAIN, "hash-1", "Foco", "30 min", emptyList())
        memory.recordSummary(LocalDate.of(2026, 11, 2), "review-1", "Semana 1", "3 de 4", emptyList())
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "T", body = "ok")))
        val ready = awaitHistory(vm) as CoachHistoryState.Ready

        vm.deleteHistoryMessage(ready.messages.single().id)

        val after = awaitHistory(vm) {
            it is CoachHistoryState.Ready && it.messages.isEmpty()
        } as CoachHistoryState.Ready
        assertThat(after.messages).isEmpty()
        assertThat(after.summaries).hasSize(1)
        assertThat(database.trainingPlanDao().getPlan("2026-11-16")).isNotNull()
    }

    @Test
    fun `apagar resumo individual mantem as mensagens`(): Unit = runBlocking {
        consent.grant()
        memory.recordMessage(CoachMessageKind.EXPLAIN, "hash-1", "Foco", "30 min", emptyList())
        memory.recordSummary(LocalDate.of(2026, 11, 2), "review-1", "Semana 1", "3 de 4", emptyList())
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "T", body = "ok")))
        val ready = awaitHistory(vm) as CoachHistoryState.Ready

        vm.deleteHistorySummary(ready.summaries.single().weekStart)

        val after = awaitHistory(vm) {
            it is CoachHistoryState.Ready && it.summaries.isEmpty()
        } as CoachHistoryState.Ready
        assertThat(after.summaries).isEmpty()
        assertThat(after.messages).hasSize(1)
    }

    @Test
    fun `codigo interno na explicacao vira Fallback sem gravar`(): Unit = runBlocking {
        consent.grant()
        plans.value = CoachPlanContext(plan(), "hash1")
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "Foco", body = "Regras R-PLAN-02 aplicadas.")))

        assertThat(awaitSettled(vm)).isInstanceOf(CoachExplanationState.Fallback::class.java)
        assertThat(database.coachMessageDao().getAll()).isEmpty()
    }

    @Test
    fun `codigo interno na revisao cai para o resumo do motor sem gravar`(): Unit = runBlocking {
        consent.grant()
        reviews.value = weeklyReview()
        val vm = viewModel(
            FakeCoachApi(CoachRawResponse(title = "T", body = "Semana com ADHERENCE_PARTIAL, R-REVIEW-01.")),
        )

        assertThat(awaitReviewSettled(vm)).isInstanceOf(CoachReviewState.Fallback::class.java)
        assertThat(vm.review.value).isNotNull
        assertThat(database.coachSummaryDao().getAll()).isEmpty()
    }

    @Test
    fun `revisao valida vira Ready com o resumo deterministico exposto`() {
        consent.grant()
        reviews.value = weeklyReview()
        val vm = viewModel(
            FakeCoachApi(CoachRawResponse(title = "Semana passada", body = "3 de 4 sessões, 75% do volume.")),
        )

        val state = awaitReviewSettled(vm)
        assertThat(state).isInstanceOf(CoachReviewState.Ready::class.java)
        assertThat((state as CoachReviewState.Ready).title).isEqualTo("Semana passada")
        assertThat(vm.review.value?.adherence?.done).isEqualTo(3)
    }

    @Test
    fun `revisao com porcentagem inventada cai para o resumo do motor`() {
        consent.grant()
        reviews.value = weeklyReview()
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "T", body = "O volume ficou em 140% do plano")))

        assertThat(awaitReviewSettled(vm)).isInstanceOf(CoachReviewState.Fallback::class.java)
        assertThat(vm.review.value).isNotNull
    }

    @Test
    fun `sem consentimento a revisao local aparece mas nao chama a api`() {
        reviews.value = weeklyReview()
        val api = FakeCoachApi(error = IOException("nao deveria chamar"))
        val vm = viewModel(api)

        assertThat(vm.review.value).isNotNull
        assertThat(vm.reviewState.value).isInstanceOf(CoachReviewState.Unavailable::class.java)
        assertThat(api.reviewCalls).isEqualTo(0)
    }

    @Test
    fun `acao rapida no Coach mostra a sugestao e o aceite pode ser desfeito`(): Unit = runBlocking {
        seedReplan()
        consent.grant()
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "T", body = "ok")))
        vm.requestReplan()

        val suggested = withTimeout(5_000) {
            vm.replan.first { !it.isLoading && it.suggestion != null }
        }
        assertThat(suggested.suggestion?.versionFrom).isEqualTo(1)
        assertThat(suggested.suggestion?.changes).isNotEmpty()

        vm.acceptReplan()
        val applied = withTimeout(5_000) {
            vm.replan.first { !it.isApplying && it.canUndo && it.message?.contains("ajustada") == true }
        }
        assertThat(applied.planVersion).isEqualTo(2)

        vm.undoReplan()
        val undone = withTimeout(5_000) {
            vm.replan.first { !it.isUndoing && it.message?.contains("desfeito") == true }
        }
        assertThat(undone.planVersion).isEqualTo(3)
        assertThat(undone.canUndo).isFalse()
        assertThat(planRepository.getWeekRecord(REPLAN_WEEK_START)?.version).isEqualTo(3)
    }

    @Test
    fun `acao rapida sem semana planejada explica o motivo`(): Unit = runBlocking {
        consent.grant()
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "T", body = "ok")))

        vm.requestReplan()

        val state = withTimeout(5_000) {
            vm.replan.first { !it.isLoading && it.message != null }
        }
        assertThat(state.message).contains("semana planejada")
        assertThat(state.suggestion).isNull()
    }

    @Test
    fun `perdi um treino lista o treino elegivel e avalia a selecao`(): Unit = runBlocking {
        seedReplan()
        consent.grant()
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "T", body = "ok")))

        vm.requestMissedSession()
        val picker = withTimeout(5_000) {
            vm.replan.first { !it.isLoading && it.missedSessions != null }
        }
        assertThat(picker.missedSessions!!.map { it.date })
            .containsExactly(REPLAN_WEEK_START, REPLAN_WEEK_START.plusDays(1))

        vm.replanMissed(REPLAN_WEEK_START)
        val suggested = withTimeout(5_000) {
            vm.replan.first { !it.isLoading && it.suggestion != null }
        }
        assertThat(suggested.suggestion?.requestNote).contains("perdido")
        assertThat(suggested.missedSessions).isNull()
    }

    @Test
    fun `menos tempo lista os dias futuros e nao persiste o override`(): Unit = runBlocking {
        seedReplan()
        consent.grant()
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "T", body = "ok")))
        val friday = REPLAN_WEEK_START.plusDays(4)

        vm.requestLessTime()
        val picker = withTimeout(5_000) {
            vm.replan.first { !it.isLoading && it.lessTimeDays != null }
        }
        assertThat(picker.lessTimeDays!!.map { it.date }).containsExactly(friday)
        assertThat(picker.lessTimeDays.single().currentMinutes).isEqualTo(30)

        vm.replanLessTime(listOf(LessTimeChange(friday, 20)))
        val suggested = withTimeout(5_000) {
            vm.replan.first { !it.isLoading && it.suggestion != null }
        }
        assertThat(suggested.suggestion?.requestNote).contains("20 min")
        assertThat(suggested.lessTimeDays).isNull()

        vm.acceptReplan()
        val applied = withTimeout(5_000) {
            vm.replan.first { !it.isApplying && it.planVersion == 2 && it.canUndo }
        }
        assertThat(applied.suggestion).isNull()
        assertThat(planRepository.getWeekRecord(REPLAN_WEEK_START)!!.availabilityHash)
            .isEqualTo(PlanReplanner.availabilityHash(replanProfile().weeklyAvailability))
        assertThat(profiles.getProfile()!!.weeklyAvailability.minutesOn(DayOfWeek.FRIDAY))
            .isEqualTo(30)
    }

    @Test
    fun `menos tempo recusa um valor que nao reduz sem chamar o motor`(): Unit = runBlocking {
        seedReplan()
        consent.grant()
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "T", body = "ok")))
        val friday = REPLAN_WEEK_START.plusDays(4)

        vm.requestLessTime()
        withTimeout(5_000) { vm.replan.first { !it.isLoading && it.lessTimeDays != null } }
        vm.replanLessTime(listOf(LessTimeChange(friday, 30)))

        val state = withTimeout(5_000) { vm.replan.first { it.message != null } }
        assertThat(state.message).contains("diminuir")
        assertThat(state.suggestion).isNull()
        assertThat(planRepository.getWeekRecord(REPLAN_WEEK_START)!!.version).isEqualTo(1)
    }

    @Test
    fun `menos tempo remove um dia e reduz outro sem persistir o override`(): Unit = runBlocking {
        val thursday = REPLAN_WEEK_START.plusDays(3)
        val friday = REPLAN_WEEK_START.plusDays(4)
        val profile = replanProfile().copy(
            weeklyAvailability = WeeklyAvailability(
                mapOf(
                    DayOfWeek.MONDAY to 40,
                    DayOfWeek.TUESDAY to 35,
                    DayOfWeek.WEDNESDAY to 40,
                    DayOfWeek.THURSDAY to 25,
                    DayOfWeek.FRIDAY to 30,
                ),
            ),
        )
        profiles.saveProfile(profile)
        checkIns.save(replanCheckIn())
        planRepository.saveWeek(replanPlan())
        planRepository.captureAvailabilityBaseline(
            REPLAN_WEEK_START,
            PlanReplanner.availabilityHash(profile.weeklyAvailability),
        )
        consent.grant()
        val vm = viewModel(FakeCoachApi(CoachRawResponse(title = "T", body = "ok")))

        vm.requestLessTime()
        val picker = withTimeout(5_000) {
            vm.replan.first { !it.isLoading && it.lessTimeDays != null }
        }
        assertThat(picker.lessTimeDays!!.map { it.date }).containsExactly(thursday, friday)

        vm.replanLessTime(
            listOf(
                LessTimeChange(thursday, 0),
                LessTimeChange(friday, 20),
            ),
        )
        val suggested = withTimeout(5_000) {
            vm.replan.first { !it.isLoading && it.suggestion != null }
        }
        assertThat(suggested.suggestion?.requestNote)
            .contains("sem treino")
            .contains("20 min")

        vm.acceptReplan()
        withTimeout(5_000) { vm.replan.first { !it.isApplying && it.planVersion == 2 } }

        val persisted = profiles.getProfile()!!.weeklyAvailability.minutesByDay
        assertThat(persisted[DayOfWeek.THURSDAY]).isEqualTo(25)
        assertThat(persisted[DayOfWeek.FRIDAY]).isEqualTo(30)
        assertThat(planRepository.getWeekRecord(REPLAN_WEEK_START)!!.availabilityHash)
            .isEqualTo(PlanReplanner.availabilityHash(profile.weeklyAvailability))
    }

    private fun seedReplan(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        checkIns.save(replanCheckIn())
        planRepository.saveWeek(
            replanPlan(
                sessions = listOf(
                    easySession(REPLAN_WEEK_START, durationMin = 40),
                    easySession(REPLAN_WEEK_START.plusDays(1), durationMin = 35),
                    easySession(REPLAN_TODAY, durationMin = 40),
                    qualitySession(REPLAN_WEEK_START.plusDays(4)),
                ),
            ),
        )
        planRepository.captureAvailabilityBaseline(
            REPLAN_WEEK_START,
            PlanReplanner.availabilityHash(replanProfile().weeklyAvailability),
        )
    }

    private fun awaitReviewSettled(vm: CoachViewModel): CoachReviewState = runBlocking {
        withTimeout(2000) {
            var state = vm.reviewState.value
            while (state is CoachReviewState.Loading ||
                (state is CoachReviewState.Unavailable && consent.isConsented() && reviews.value != null)
            ) {
                kotlinx.coroutines.delay(1)
                state = vm.reviewState.value
            }
            state
        }
    }

    private fun awaitHistory(
        vm: CoachViewModel,
        predicate: (CoachHistoryState) -> Boolean = {
            it is CoachHistoryState.Empty ||
                it is CoachHistoryState.Ready ||
                it is CoachHistoryState.Error
        },
    ): CoachHistoryState = runBlocking {
        withTimeout(2_000) {
            var state = vm.history.value
            while (!predicate(state)) {
                kotlinx.coroutines.delay(1)
                state = vm.history.value
            }
            state
        }
    }
}
