package com.meucoach.app.ui.level

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.application.EvaluateLevelGraduation
import com.meucoach.app.application.EvaluateLevelGraduationResult
import com.meucoach.app.application.EvaluateReplan
import com.meucoach.app.application.LevelGraduationContextBuilder
import com.meucoach.app.application.LevelGraduationEvaluator
import com.meucoach.app.application.ReplanContextBuilder
import com.meucoach.app.application.TrainingSignalSource
import com.meucoach.app.level.LevelGraduationSuggestionStore
import com.meucoach.app.replan.ProfilePlanEvaluationStore
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionClosureReason
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
import com.meucoach.core.model.Sport
import com.meucoach.core.model.VdotSource
import com.meucoach.core.model.WeeklyAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LevelGraduationViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: MeuCoachDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var checkIns: CheckInRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var activities: ActivityRepository
    private lateinit var prescriptions: DailyPrescriptionRepository
    private lateinit var store: LevelGraduationSuggestionStore
    private lateinit var planEvaluations: ProfilePlanEvaluationStore
    private lateinit var viewModel: LevelGraduationViewModel
    private val clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC)
    private val timeProvider = FakeTimeProvider(clock.instant(), ZoneOffset.UTC)

    private val today: LocalDate = LocalDate.of(2026, 11, 4)
    private val weekStart: LocalDate = LocalDate.of(2026, 11, 2)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        shadowOf(Looper.getMainLooper()).idle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        profiles = ProfileRepository(database, clock)
        checkIns = CheckInRepository(database, clock)
        plans = TrainingPlanRepository(database, clock)
        val metrics = DailyMetricsRepository(database, clock)
        activities = ActivityRepository(database = database, clock = clock, zoneId = ZoneOffset.UTC)
        val executions = SessionExecutionRepository(database, clock)
        prescriptions = DailyPrescriptionRepository(database, clock)
        val signals = TrainingSignalSource(
            checkIns = checkIns,
            metrics = metrics,
            activities = activities,
            executions = executions,
        )
        val raceEvents = RaceEventRepository(database, clock)
        val graduationContexts = LevelGraduationContextBuilder(
            profiles = profiles,
            plans = plans,
            checkIns = checkIns,
            signals = signals,
            raceEvents = raceEvents,
            timeProvider = timeProvider,
        )
        val replanContexts = ReplanContextBuilder(
            plans = plans,
            profiles = profiles,
            checkIns = checkIns,
            signals = signals,
            raceEvents = raceEvents,
            timeProvider = timeProvider,
        )
        val evaluateReplan = EvaluateReplan(replanContexts, plans)
        store = LevelGraduationSuggestionStore(context, "level-view-${System.nanoTime()}")
        planEvaluations = ProfilePlanEvaluationStore()
        viewModel = LevelGraduationViewModel(
            profiles = profiles,
            evaluateGraduation = EvaluateLevelGraduation(graduationContexts),
            evaluateReplan = evaluateReplan,
            planEvaluations = planEvaluations,
            store = store,
            timeProvider = timeProvider,
        )
    }

    @After
    fun tearDown() {
        shadowOf(Looper.getMainLooper()).idle()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh surfaces a beginner to intermediate suggestion`(): Unit = runBlocking {
        seedGraduatable()

        viewModel.refresh()
        val state = awaitSuggestion()

        assertThat(state.suggestion?.from).isEqualTo(Experience.BEGINNER)
        assertThat(state.suggestion?.to).isEqualTo(Experience.INTERMEDIATE)
        assertThat(state.suggestion?.ruleIds).containsExactly("R-LVL-01")
        assertThat(state.suggestion?.reasons).isNotEmpty()
    }

    @Test
    fun `accept persists the new level and keeps done closed and prescription intact`(): Unit = runBlocking {
        seedGraduatable()
        seedCurrentWeekWithHistory()
        val weekBefore = plans.getWeek(weekStart)
        val prescriptionBefore = prescriptions.getLatest(today)
        val versionBefore = plans.getWeekRecord(weekStart)?.version

        viewModel.refresh()
        awaitSuggestion()

        viewModel.accept()
        val done = withTimeout(5_000) {
            viewModel.uiState.first { !it.isApplying && it.message != null }
        }

        assertThat(profiles.getProfile()?.experience).isEqualTo(Experience.INTERMEDIATE)
        assertThat(done.suggestion).isNull()
        assertThat(done.message).contains("Intermediário")
        assertThat(plans.getWeekRecord(weekStart)?.version).isEqualTo(versionBefore)
        val weekAfter = plans.getWeek(weekStart)
        assertThat(weekAfter?.sessions?.map { it.status }).isEqualTo(weekBefore?.sessions?.map { it.status })
        assertThat(weekAfter?.sessions?.map { it.completionEvidence })
            .isEqualTo(weekBefore?.sessions?.map { it.completionEvidence })
        val prescriptionAfter = prescriptions.getLatest(today)
        assertThat(prescriptionAfter?.id).isEqualTo(prescriptionBefore?.id)
        assertThat(prescriptionAfter?.inputsHash).isEqualTo(prescriptionBefore?.inputsHash)
        assertThat(planEvaluations.result.value).isNotNull()
    }

    @Test
    fun `a declined proposal does not reappear but a new hash does`(): Unit = runBlocking {
        seedGraduatable()
        viewModel.refresh()
        awaitSuggestion()

        viewModel.decline()
        assertThat(viewModel.uiState.value.suggestion).isNull()
        viewModel.refresh()
        assertThat(awaitIdle().suggestion).isNull()

        activities.saveImportedActivity(
            ImportedActivity(
                source = ActivitySource.FIT,
                sport = Sport.RUNNING,
                startTime = Instant.parse("2026-11-03T12:00:00Z"),
                durationSec = 1_560,
                distanceM = 5_200.0,
                averageHeartRate = 172,
                averagePace = Pace.fromSecondsPerKm(355),
            ),
        )
        viewModel.refresh()
        assertThat(awaitSuggestion().suggestion).isNotNull()
    }

    @Test
    fun `a suppressed suggestion stays hidden only until the next day`(): Unit = runBlocking {
        seedGraduatable()
        viewModel.refresh()
        awaitSuggestion()

        viewModel.suppress()
        assertThat(viewModel.uiState.value.suggestion).isNull()
        viewModel.refresh()
        assertThat(awaitIdle().suggestion).isNull()

        timeProvider.setNow(Instant.parse("2026-11-05T12:00:00Z"))
        viewModel.refresh()
        assertThat(awaitSuggestion().suggestion).isNotNull()
    }

    @Test
    fun `an advanced profile never suggests a downgrade`(): Unit = runBlocking {
        seedGraduatable(experience = Experience.ADVANCED)

        viewModel.refresh()
        val state = awaitIdle()

        assertThat(state.suggestion).isNull()
        assertThat(state.message).isNull()
        assertThat(profiles.getProfile()?.experience).isEqualTo(Experience.ADVANCED)
    }

    @Test
    fun `an evaluation failure surfaces an error message`(): Unit = runBlocking {
        val failing = object : LevelGraduationEvaluator {
            override suspend fun invoke(): EvaluateLevelGraduationResult = throw IllegalStateException("boom")
        }
        val failingViewModel = LevelGraduationViewModel(
            profiles = profiles,
            evaluateGraduation = failing,
            evaluateReplan = EvaluateReplan(
                ReplanContextBuilder(
                    plans = plans,
                    profiles = profiles,
                    checkIns = checkIns,
                    signals = TrainingSignalSource(
                        checkIns = checkIns,
                        metrics = DailyMetricsRepository(database, clock),
                        activities = activities,
                        executions = SessionExecutionRepository(database, clock),
                    ),
                    raceEvents = RaceEventRepository(database, clock),
                    timeProvider = timeProvider,
                ),
                plans,
            ),
            planEvaluations = ProfilePlanEvaluationStore(),
            store = LevelGraduationSuggestionStore(
                ApplicationProvider.getApplicationContext<Context>(),
                "level-failing-${System.nanoTime()}",
            ),
            timeProvider = timeProvider,
        )

        failingViewModel.refresh()
        val state = withTimeout(5_000) {
            failingViewModel.uiState.first { !it.isLoading && it.message != null }
        }

        assertThat(state.suggestion).isNull()
        assertThat(state.message).contains("Não foi possível avaliar")
    }

    private suspend fun awaitSuggestion(): LevelGraduationUiState = withTimeout(5_000) {
        viewModel.uiState.first { !it.isLoading && it.suggestion != null }
    }

    private suspend fun awaitIdle(): LevelGraduationUiState = withTimeout(5_000) {
        viewModel.uiState.first { !it.isLoading }
    }

    private suspend fun seedGraduatable(experience: Experience = Experience.BEGINNER) {
        profiles.saveProfile(baseProfile(experience))
        checkIns.save(
            DailyCheckIn(
                date = today,
                sleepQuality = 4,
                fatigue = 2,
                soreness = 1,
                stress = 2,
            ),
        )
        listOf(
            Instant.parse("2026-10-20T12:00:00Z"),
            Instant.parse("2026-10-27T12:00:00Z"),
            Instant.parse("2026-11-02T12:00:00Z"),
        ).forEach { start ->
            activities.saveImportedActivity(
                ImportedActivity(
                    source = ActivitySource.FIT,
                    sport = Sport.RUNNING,
                    startTime = start,
                    durationSec = 1_500,
                    distanceM = 5_000.0,
                    averageHeartRate = 170,
                    averagePace = Pace.fromSecondsPerKm(360),
                ),
            )
        }
        if (experience == Experience.ADVANCED) return
        listOf(
            weekStart.minusWeeks(4) to 65,
            weekStart.minusWeeks(3) to null,
            weekStart.minusWeeks(2) to null,
            weekStart.minusWeeks(1) to null,
        ).forEach { (start, longMin) ->
            plans.saveWeek(strongWeek(start, longMin))
        }
    }

    private suspend fun seedCurrentWeekWithHistory() {
        val sessions = listOf(
            PlannedSession(
                date = weekStart,
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.DONE,
                completionEvidence = CompletionEvidence.ACTIVITY,
                activityId = "act-done-1",
            ),
            PlannedSession(
                date = weekStart.plusDays(1),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.CLOSED,
                closureReason = SessionClosureReason.USER_SKIPPED,
                closedAt = Instant.parse("2026-11-03T12:00:00Z"),
            ),
            PlannedSession(
                date = today,
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
            ),
            PlannedSession(
                date = weekStart.plusDays(4),
                type = WorkoutType.LONG,
                title = "Longão",
                durationMin = 60,
                rationale = "Volume.",
                ruleIds = listOf("R-PLAN-03"),
                evidenceIds = listOf("EV-PFITZINGER-2008"),
            ),
        )
        plans.saveWeek(
            WeeklyPlan(
                weekStart = weekStart,
                sessions = sessions,
                weeklyMinutes = sessions.sumOf { it.durationMin ?: 0 },
                longRunMinutes = 60,
                qualityCount = 0,
                rationale = "Semana vigente sintética.",
                confidence = DataConfidence.MEDIUM,
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                createdOn = weekStart,
            ),
        )
        prescriptions.append(
            DailyPrescription(
                date = today,
                plannedSessionId = null,
                plannedType = WorkoutType.EASY,
                plannedTitle = "Fácil",
                plannedDurationMin = 30,
                prescribedType = WorkoutType.EASY,
                title = "Fácil",
                prescribedDurationMin = 30,
                targets = PrescriptionTargets(),
                adjustment = PrescriptionAdjustment.KEEP,
                rationale = "Prescrição vigente sintética.",
                ruleIds = listOf("R-PRESC-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                confidence = DataConfidence.MEDIUM,
                engineVersion = "daily-prescription-v5",
                inputsHash = "rx-hash",
            ),
        )
    }

    private fun baseProfile(experience: Experience) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(360),
        vdot = 35.0,
        vdotSource = VdotSource.PERFORMANCE,
        vdotCalibratedAt = LocalDate.of(2026, 10, 25),
        vdotReferenceDistanceM = 5_000.0,
        vdotReferenceDurationSec = 1_500,
        experience = experience,
        timezone = "UTC",
        weeklyAvailability = WeeklyAvailability(
            mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.SATURDAY to 90,
            ),
        ),
    )

    private fun strongWeek(start: LocalDate, longRunMin: Int?): WeeklyPlan {
        val sessions = (0 until 3).map { index ->
            PlannedSession(
                date = start.plusDays(index.toLong()),
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base sintética.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.DONE,
                completionEvidence = CompletionEvidence.ACTIVITY,
                activityId = "act-$start-$index",
            )
        } + PlannedSession(
            date = start.plusDays(5),
            type = WorkoutType.LONG,
            title = "Longão",
            durationMin = longRunMin ?: 30,
            rationale = "Longão sintético.",
            ruleIds = listOf("R-PLAN-03"),
            evidenceIds = listOf("EV-PFITZINGER-2008"),
            status = SessionStatus.DONE,
            completionEvidence = CompletionEvidence.ACTIVITY,
            activityId = "act-$start-long",
        )
        return WeeklyPlan(
            weekStart = start,
            sessions = sessions,
            weeklyMinutes = sessions.sumOf { it.durationMin ?: 0 },
            longRunMinutes = longRunMin,
            qualityCount = 0,
            rationale = "Semana sintética.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
            createdOn = start,
        )
    }
}
