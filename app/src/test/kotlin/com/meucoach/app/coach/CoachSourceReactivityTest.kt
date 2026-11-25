package com.meucoach.app.coach

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.application.TrainingSignalSource
import com.meucoach.app.application.WeeklyReviewAssembler
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.plannedSessionId
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.NewRaceEvent
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventOutcome
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.SaveActivityResult
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.coach.CoachGuardKind
import com.meucoach.core.domain.feedback.ComparisonVerdict
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.Sex
import com.meucoach.core.model.Sport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Segundo emissão em Room in-memory (Bloco B). Each test starts the real
 * source, writes through the real repository and asserts the cards change
 * without reopening the app. All data is synthetic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoachSourceReactivityTest {
    private val today = LocalDate.of(2026, 11, 18)
    private val currentMonday = LocalDate.of(2026, 11, 16)
    private val closedMonday = LocalDate.of(2026, 11, 9)
    private lateinit var database: MeuCoachDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var checkIns: CheckInRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var activities: ActivityRepository
    private lateinit var executions: SessionExecutionRepository
    private lateinit var raceEvents: RaceEventRepository
    private lateinit var prescriptions: DailyPrescriptionRepository
    private lateinit var next: CoachNextSource
    private lateinit var guards: CoachGuardsSource
    private lateinit var progress: CoachProgressSource
    private lateinit var review: CoachReviewSource
    private lateinit var planSource: CoachPlanSource
    private var activitySequence = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room
            .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC)
        profiles = ProfileRepository(database, clock)
        checkIns = CheckInRepository(database, clock)
        plans = TrainingPlanRepository(database, clock)
        executions = SessionExecutionRepository(database, clock)
        raceEvents = RaceEventRepository(database, clock)
        activities = ActivityRepository(
            database = database,
            clock = clock,
            zoneId = ZoneOffset.UTC,
            idGenerator = { "activity-${activitySequence++}" },
        )
        val metrics = DailyMetricsRepository(database, clock)
        val signals = TrainingSignalSource(checkIns, metrics, activities, executions)
        val coachSignals = RoomCoachSignalsSource(checkIns, metrics, activities, executions)
        prescriptions = DailyPrescriptionRepository(database, clock)
        val assembler = WeeklyReviewAssembler(plans, prescriptions, signals)
        val time = FakeTimeProvider(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC)
        next = RoomCoachNextSource(plans, prescriptions, raceEvents, profiles, coachSignals, time)
        guards = RoomCoachGuardsSource(
            profiles = profiles,
            plans = plans,
            raceEvents = raceEvents,
            signals = signals,
            coachSignals = coachSignals,
            time = time,
            clock = clock,
        )
        progress = RoomCoachProgressSource(
            profiles = profiles,
            plans = plans,
            activities = activities,
            raceEvents = raceEvents,
            assembler = assembler,
            coachSignals = coachSignals,
            time = time,
        )
        review = RoomCoachReviewSource(
            profiles = profiles,
            activities = activities,
            plans = plans,
            raceEvents = raceEvents,
            assembler = assembler,
            coachSignals = coachSignals,
            time = time,
        )
        planSource = RoomCoachPlanSource(plans, time)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `next carrega prescricao de hoje e execucao vinculada`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        plans.saveWeek(currentPlan())
        prescriptions.append(prescription(today))
        val imported = activity(today)
        val saved = activities.saveImportedActivity(imported) as SaveActivityResult.Saved
        executions.recordActivityExecution(
            plannedSessionId = plannedSessionId(currentMonday, today),
            activityId = saved.id,
            startedAt = imported.startTime,
            durationSec = imported.durationSec,
            distanceM = imported.distanceM,
            rpe = 6,
        )

        val context = withTimeout(5_000) {
            next.observeNext().first { it.todayExecution != null }
        }

        assertThat(context.todayPrescription?.prescribedDurationMin).isEqualTo(28)
        assertThat(context.todayExecution?.durationMin).isEqualTo(30)
        assertThat(context.todayExecution?.distanceKm).isEqualTo(5.0)
        assertThat(context.todayExecution?.rpe).isEqualTo(6)
        assertThat(context.todayExecution?.comparison?.durationVerdict)
            .isEqualTo(ComparisonVerdict.WITHIN)
    }

    @Test
    fun `next carrega a ultima execucao quando hoje e descanso`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val tuesday = currentMonday.plusDays(1)
        val restPlan = currentPlan().let { plan ->
            plan.copy(sessions = plan.sessions.filter { it.date != today })
        }
        plans.saveWeek(restPlan)
        prescriptions.append(prescription(tuesday))
        val imported = activity(tuesday)
        val saved = activities.saveImportedActivity(imported) as SaveActivityResult.Saved
        executions.recordActivityExecution(
            plannedSessionId = plannedSessionId(currentMonday, tuesday),
            activityId = saved.id,
            startedAt = imported.startTime,
            durationSec = imported.durationSec,
            distanceM = imported.distanceM,
            rpe = 6,
        )

        val context = withTimeout(5_000) {
            next.observeNext().first { it.lastExecution != null }
        }

        assertThat(context.todaySession).isNull()
        assertThat(context.todayExecution).isNull()
        assertThat(context.lastSession?.date).isEqualTo(tuesday)
        assertThat(context.lastPrescription?.date).isEqualTo(tuesday)
        assertThat(context.lastExecution?.date).isEqualTo(tuesday)
        assertThat(context.lastExecution?.durationMin).isEqualTo(30)
        assertThat(context.lastExecution?.distanceKm).isEqualTo(5.0)
        assertThat(context.lastExecution?.rpe).isEqualTo(6)
        assertThat(context.lastExecution?.comparison?.durationVerdict)
            .isEqualTo(ComparisonVerdict.WITHIN)
    }

    @Test
    fun `importar corrida reemite guardas, progresso e revisao`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        plans.saveWeek(closedPlan())
        val guardValues = mutableListOf<CoachGuardsContext>()
        val progressValues = mutableListOf<CoachProgressContext>()
        val reviewValues = mutableListOf<WeeklyReview?>()
        val jobs = collectAll(guardValues, progressValues, reviewValues)
        awaitValue(guardValues) { it.guards.any { guard -> guard.kind == CoachGuardKind.CALIBRATION } }
        awaitValue(progressValues) { it.weeks.size == 4 }
        awaitValue(reviewValues) { it != null }

        activities.saveImportedActivity(activity(closedMonday.plusDays(1)))

        val printed = awaitValue(guardValues) { it.guards.any { guard -> guard.detail.contains("1 de 28") } }
        val updatedProgress = awaitValue(progressValues) {
            it.weeks.last().volume?.executedMin == 30
        }
        val updatedReview = awaitValue(reviewValues) { it?.volume?.executedMin == 30 }

        assertThat(printed.guards.map { it.kind }).contains(CoachGuardKind.CALIBRATION)
        assertThat(updatedProgress.weeks.last().volume?.extraMin).isEqualTo(30)
        assertThat(updatedReview?.volume?.extraMin).isEqualTo(30)
        jobs.forEach(Job::cancel)
    }

    @Test
    fun `check-in reemite guardas, progresso e revisao`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        plans.saveWeek(closedPlan())
        val guardValues = mutableListOf<CoachGuardsContext>()
        val progressValues = mutableListOf<CoachProgressContext>()
        val reviewValues = mutableListOf<WeeklyReview?>()
        val jobs = collectAll(guardValues, progressValues, reviewValues)
        awaitValue(guardValues) { it.guards.isNotEmpty() }
        awaitValue(progressValues) { it.weeks.size == 4 }
        awaitValue(reviewValues) { it != null }
        val guardsBefore = guardValues.size
        val progressBefore = progressValues.size
        val reviewBefore = reviewValues.size

        checkIns.save(
            DailyCheckIn(
                date = today,
                sleepQuality = 4,
                fatigue = 2,
                soreness = 1,
                stress = 2,
            ),
        )

        awaitUntil { guardValues.size > guardsBefore }
        awaitUntil { progressValues.size > progressBefore }
        awaitUntil { reviewValues.size > reviewBefore }
        jobs.forEach(Job::cancel)
    }

    @Test
    fun `concluir treino atualiza adesao e volume`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        plans.saveWeek(closedPlan())
        val guardValues = mutableListOf<CoachGuardsContext>()
        val progressValues = mutableListOf<CoachProgressContext>()
        val reviewValues = mutableListOf<WeeklyReview?>()
        val jobs = collectAll(guardValues, progressValues, reviewValues)
        awaitValue(guardValues)
        val initialProgress = awaitValue(progressValues) {
            it.weeks.last().adherence?.done == 0
        }
        val initialReview = awaitValue(reviewValues) { it?.adherence?.done == 0 }
        val guardsBefore = guardValues.size
        val date = closedMonday.plusDays(1)

        val saved = activities.saveImportedActivity(activity(date)) as SaveActivityResult.Saved
        executions.recordActivityExecution(
            plannedSessionId = plannedSessionId(closedMonday, date),
            activityId = saved.id,
            startedAt = Instant.parse("2026-11-10T10:00:00Z"),
            durationSec = 1_800,
            distanceM = 5_000.0,
        )

        val updatedProgress = awaitValue(progressValues) {
            it.weeks.last().adherence?.done == 1 && it.weeks.last().volume?.executedMin == 30
        }
        val updatedReview = awaitValue(reviewValues) {
            it?.adherence?.done == 1 && it.volume?.executedMin == 30
        }

        assertThat(initialProgress.weeks.last().volume?.executedMin).isNull()
        assertThat(initialReview?.volume?.executedMin).isNull()
        assertThat(updatedProgress.weeks.last().volume?.extraMin).isNull()
        assertThat(updatedReview?.volume?.extraMin).isNull()
        awaitUntil { guardValues.size > guardsBefore }
        jobs.forEach(Job::cancel)
    }

    @Test
    fun `atualizar a semana reemite explicacao guardas progresso e revisao`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        plans.saveWeek(closedPlan())
        plans.saveWeek(currentPlan())
        val guardValues = mutableListOf<CoachGuardsContext>()
        val progressValues = mutableListOf<CoachProgressContext>()
        val reviewValues = mutableListOf<WeeklyReview?>()
        val planValues = mutableListOf<CoachPlanContext?>()
        val jobs = collectAll(guardValues, progressValues, reviewValues) + listOf(
            launch { planSource.observeCurrent().collect { planValues += it } },
        )
        awaitValue(guardValues)
        awaitValue(progressValues) { it.weeks.size == 4 }
        awaitValue(reviewValues) { it != null }
        awaitValue(planValues) { it?.version == 1 }
        val guardsBefore = guardValues.size

        assertThat(
            plans.updateSessions(
                currentMonday,
                currentPlan().sessions.map { it.copy(title = "Fácil 25", durationMin = 25) },
            ),
        ).isTrue()
        assertThat(
            plans.updateSessions(
                closedMonday,
                closedPlan().sessions.map { it.copy(durationMin = 45) },
            ),
        ).isTrue()

        val updatedPlan = awaitValue(planValues) {
            it?.plan?.sessions?.all { session -> session.durationMin == 25 } == true
        }
        val updatedProgress = awaitValue(progressValues) {
            it.weeks.last().volume?.plannedMin == 90
        }
        val updatedReview = awaitValue(reviewValues) { it?.volume?.plannedMin == 90 }

        assertThat(updatedPlan!!.plan.sessions.map { it.title }).containsOnly("Fácil 25")
        assertThat(updatedProgress.weeks.last().volume?.plannedMin).isEqualTo(90)
        assertThat(updatedReview?.volume?.plannedMin).isEqualTo(90)
        awaitUntil { guardValues.size > guardsBefore }
        jobs.forEach(Job::cancel)
    }

    @Test
    fun `prova na semana fechada reemite revisao`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        plans.saveWeek(closedPlan())
        val reviewValues = mutableListOf<WeeklyReview?>()
        val job = launch { review.observeLastClosedWeek().collect { reviewValues += it } }
        val initial = awaitValue(reviewValues) { it?.adherence?.planned == 2 }

        val outcome = raceEvents.create(NewRaceEvent(RaceDistance.FIVE_K, closedMonday.plusDays(3)))

        assertThat(outcome).isInstanceOf(RaceEventOutcome.Saved::class.java)
        assertThat(initial?.adherence?.planned).isEqualTo(2)
        val updated = awaitValue(reviewValues) { it?.adherence?.planned == 1 }
        assertThat(updated?.adherence?.planned).isEqualTo(1)
        job.cancel()
    }

    private fun kotlinx.coroutines.CoroutineScope.collectAll(
        guardValues: MutableList<CoachGuardsContext>,
        progressValues: MutableList<CoachProgressContext>,
        reviewValues: MutableList<WeeklyReview?>,
    ): List<Job> = listOf(
        launch { guards.observeGuards().collect { guardValues += it } },
        launch { progress.observeProgress().collect { progressValues += it } },
        launch { review.observeLastClosedWeek().collect { reviewValues += it } },
    )

    private suspend fun <T> awaitValue(values: List<T>, predicate: (T) -> Boolean) = withTimeout(5_000) {
        while (true) {
            values.firstOrNull(predicate)?.let { return@withTimeout it }
            delay(1)
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private suspend fun awaitValue(values: MutableList<CoachGuardsContext>) =
        awaitValue(values) { it.guards.isNotEmpty() }

    private suspend fun awaitUntil(condition: () -> Boolean) = withTimeout(5_000) {
        while (!condition()) {
            delay(1)
        }
    }

    private fun activity(date: LocalDate) = ImportedActivity(
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(36_000),
        durationSec = 1_800,
        distanceM = 5_000.0,
        averageHeartRate = 150,
        averagePace = Pace.fromSecondsPerKm(360),
    )

    private fun prescription(date: LocalDate) = DailyPrescription(
        date = date,
        plannedSessionId = plannedSessionId(currentMonday, date),
        plannedType = WorkoutType.EASY,
        plannedTitle = "Fácil",
        plannedDurationMin = 30,
        prescribedType = WorkoutType.EASY,
        title = "Fácil",
        prescribedDurationMin = 28,
        targets = PrescriptionTargets(primary = TargetKind.RPE, rpe = 4),
        adjustment = PrescriptionAdjustment.KEEP,
        rationale = "Dia fácil.",
        ruleIds = listOf("R-PRESC-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        confidence = DataConfidence.HIGH,
        engineVersion = "daily-prescription-v6",
        inputsHash = "hash-presc",
    )

    private fun closedPlan() = plan(
        weekStart = closedMonday,
        dates = listOf(closedMonday.plusDays(1), closedMonday.plusDays(3)),
    )

    private fun currentPlan() = plan(
        weekStart = currentMonday,
        dates = listOf(currentMonday.plusDays(1), today, currentMonday.plusDays(3)),
    )

    private fun plan(weekStart: LocalDate, dates: List<LocalDate>) = WeeklyPlan(
        weekStart = weekStart,
        sessions = dates.map { date ->
            PlannedSession(
                date = date,
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.PLANNED,
            )
        },
        weeklyMinutes = dates.size * 30,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Semana base",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        createdOn = weekStart,
        engineVersion = WeeklyPlanGenerator.ENGINE_VERSION,
    )

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        thresholdPace = Pace.fromSecondsPerKm(360),
        timezone = "UTC",
    )
}
