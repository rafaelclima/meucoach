package com.meucoach.app.coach

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.application.replanCheckIn
import com.meucoach.app.application.replanProfile
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.NewRaceEvent
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.coach.CoachNextSubject
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoachNextSourceTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private lateinit var database: MeuCoachDatabase
    private lateinit var plans: TrainingPlanRepository
    private lateinit var prescriptions: DailyPrescriptionRepository
    private lateinit var raceEvents: RaceEventRepository
    private lateinit var profiles: ProfileRepository
    private lateinit var checkIns: CheckInRepository
    private lateinit var metrics: DailyMetricsRepository
    private lateinit var activities: ActivityRepository
    private lateinit var executions: SessionExecutionRepository
    private lateinit var source: CoachNextSource

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room
            .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(Instant.parse("2026-11-16T12:00:00Z"), ZoneOffset.UTC)
        plans = TrainingPlanRepository(database, clock)
        prescriptions = DailyPrescriptionRepository(database, clock)
        raceEvents = RaceEventRepository(database, clock)
        profiles = ProfileRepository(database, clock)
        checkIns = CheckInRepository(database, clock)
        metrics = DailyMetricsRepository(database, clock)
        activities = ActivityRepository(database = database, clock = clock)
        executions = SessionExecutionRepository(database, clock)
        source = sourceAt(Instant.parse("2026-11-16T12:00:00Z"))
    }

    private fun sourceAt(instant: Instant) = RoomCoachNextSource(
        plans = plans,
        prescriptions = prescriptions,
        raceEvents = raceEvents,
        profiles = profiles,
        signals = RoomCoachSignalsSource(
            checkIns = checkIns,
            metrics = metrics,
            activities = activities,
            executions = executions,
        ),
        time = FakeTimeProvider(instant, ZoneOffset.UTC),
    )

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `devolve hoje pendente com a prescricao vigente`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        plans.saveWeek(plan(todayStatus = SessionStatus.PLANNED))
        prescriptions.append(prescription(monday))

        val next = source.observeNext().first { it.subject != null }

        assertThat(next.subject).isEqualTo(CoachNextSubject.Training(planSession(monday)))
        assertThat(next.isToday).isTrue()
        assertThat(next.prescription?.date).isEqualTo(monday)
        assertThat(next.hasPlan).isTrue()
        assertThat(next.hasProfile).isTrue()
    }

    @Test
    fun `sem pendencia hoje devolve a proxima futura sem prescricao`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        plans.saveWeek(plan(todayStatus = SessionStatus.DONE))

        val next = source.observeNext().first { it.subject != null }

        assertThat(next.subject).isEqualTo(CoachNextSubject.Training(planSession(monday.plusDays(2))))
        assertThat(next.isToday).isFalse()
        assertThat(next.prescription).isNull()
    }

    @Test
    fun `sem plano nao ha proximo e o perfil sinaliza onboarding`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())

        val next = source.observeNext().first()

        assertThat(next.subject).isNull()
        assertThat(next.hasPlan).isFalse()
        assertThat(next.hasProfile).isTrue()
    }

    @Test
    fun `prova amanha sem sessao na data vira o proximo sem prescricao`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        plans.saveWeek(plan(todayStatus = SessionStatus.DONE))
        raceEvents.create(NewRaceEvent(RaceDistance.FIVE_K, monday.plusDays(1)))
        prescriptions.append(prescription(monday.plusDays(1)))

        val next = source.observeNext().first { it.subject is CoachNextSubject.Race }

        val race = next.subject as CoachNextSubject.Race
        assertThat(race.event.targetDate).isEqualTo(monday.plusDays(1))
        assertThat(next.isToday).isFalse()
        assertThat(next.prescription).isNull()
    }

    @Test
    fun `prova no domingo sem sessao na data nao cai em semana concluida`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        plans.saveWeek(
            plan(todayStatus = SessionStatus.DONE).let { plan ->
                plan.copy(
                    sessions = plan.sessions.map {
                        it.copy(
                            status = SessionStatus.DONE,
                            completionEvidence = CompletionEvidence.MANUAL,
                        )
                    },
                )
            },
        )
        raceEvents.create(NewRaceEvent(RaceDistance.TEN_K, monday.plusDays(6)))

        val saturday = sourceAt(Instant.parse("2026-11-21T12:00:00Z"))
        val next = saturday.observeNext().first { it.subject != null }

        val race = next.subject as CoachNextSubject.Race
        assertThat(race.event.targetDate).isEqualTo(monday.plusDays(6))
        assertThat(next.isToday).isFalse()
        assertThat(next.hasPlan).isTrue()
    }

    @Test
    fun `prova hoje sem sessao nao cai em semana concluida`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        plans.saveWeek(plan(todayStatus = SessionStatus.DONE))
        raceEvents.create(NewRaceEvent(RaceDistance.TEN_K, monday))

        val next = source.observeNext().first { it.subject != null }

        assertThat(next.subject).isInstanceOf(CoachNextSubject.Race::class.java)
        assertThat(next.isToday).isTrue()
    }

    @Test
    fun `hoje carrega a prontidao e o sono do check-in`(): Unit = runBlocking {
        plans.saveWeek(plan(todayStatus = SessionStatus.PLANNED))
        checkIns.save(replanCheckIn(monday))
        metrics.saveAll(listOf(DailyMetrics(date = monday, sleepDurationMin = 340)))

        val next = source.observeNext().first { it.subject != null }

        assertThat(next.hasCheckIn).isTrue()
        assertThat(next.sleepMinutes).isEqualTo(340)
        assertThat(next.readiness?.level).isEqualTo(ReadinessLevel.AMBER)
        assertThat(next.readiness?.flags).contains(ReadinessFlag.SHORT_SLEEP)
        assertThat(next.readiness?.flags).doesNotContain(ReadinessFlag.INSUFFICIENT_DATA)
    }

    @Test
    fun `sem check-in a prontidao fica ausente e nao vira moderada inventada`(): Unit = runBlocking {
        plans.saveWeek(plan(todayStatus = SessionStatus.PLANNED))

        val next = source.observeNext().first { it.subject != null }

        assertThat(next.hasCheckIn).isFalse()
        assertThat(next.readiness?.flags).contains(ReadinessFlag.INSUFFICIENT_DATA)
        assertThat(next.readiness?.flags).contains(ReadinessFlag.SUBJECTIVE_MISSING)
    }

    @Test
    fun `hoje feito carrega a sessao de hoje mesmo concluida`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        plans.saveWeek(plan(todayStatus = SessionStatus.DONE))

        val next = source.observeNext().first { it.subject != null }

        assertThat(next.subject).isEqualTo(CoachNextSubject.Training(planSession(monday.plusDays(2))))
        assertThat(next.todaySession?.date).isEqualTo(monday)
        assertThat(next.todaySession?.status).isEqualTo(SessionStatus.DONE)
    }

    @Test
    fun `hoje pendente carrega a sessao de hoje ainda aberta`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        plans.saveWeek(plan(todayStatus = SessionStatus.PLANNED))

        val next = source.observeNext().first { it.subject != null }

        assertThat(next.todaySession?.date).isEqualTo(monday)
        assertThat(next.todaySession?.status).isEqualTo(SessionStatus.PLANNED)
    }

    @Test
    fun `sem plano nao ha sessao de hoje`(): Unit = runBlocking {
        val next = source.observeNext().first()

        assertThat(next.todaySession).isNull()
    }

    @Test
    fun `importacao de hoje sinaliza o turn de carga`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        plans.saveWeek(plan(todayStatus = SessionStatus.PLANNED))
        activities.saveImportedActivity(activity())

        val next = source.observeNext().first { it.subject != null }

        assertThat(next.importedToday).isTrue()
    }

    @Test
    fun `sem importacao nao sinaliza importacao de hoje`(): Unit = runBlocking {
        plans.saveWeek(plan(todayStatus = SessionStatus.PLANNED))

        val next = source.observeNext().first { it.subject != null }

        assertThat(next.importedToday).isFalse()
    }

    private fun plan(todayStatus: SessionStatus) = WeeklyPlan(
        weekStart = monday,
        sessions = listOf(
            planSession(monday, todayStatus),
            planSession(monday.plusDays(2)),
        ),
        weeklyMinutes = 65,
        longRunMinutes = null,
        qualityCount = 1,
        rationale = "Semana base",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        createdOn = monday,
        engineVersion = WeeklyPlanGenerator.ENGINE_VERSION,
    )

    private fun planSession(date: LocalDate, status: SessionStatus = SessionStatus.PLANNED) = PlannedSession(
        date = date,
        type = if (date == monday.plusDays(2)) WorkoutType.QUALITY else WorkoutType.EASY,
        title = if (date == monday.plusDays(2)) "Ritmo forte" else "Fácil",
        durationMin = if (date == monday.plusDays(2)) 35 else 30,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = status,
        completionEvidence = if (status == SessionStatus.DONE) {
            CompletionEvidence.MANUAL
        } else {
            null
        },
    )

    private fun activity() = ImportedActivity(
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-11-16T10:00:00Z"),
        durationSec = 1_800,
        distanceM = 5_000.0,
        averageHeartRate = 150,
        averagePace = Pace.fromSecondsPerKm(360),
    )

    private fun prescription(date: LocalDate) = DailyPrescription(
        date = date,
        plannedSessionId = null,
        plannedType = WorkoutType.EASY,
        plannedTitle = "Fácil",
        plannedDurationMin = 30,
        prescribedType = WorkoutType.EASY,
        title = "Fácil",
        prescribedDurationMin = 30,
        targets = PrescriptionTargets(primary = TargetKind.RPE),
        adjustment = PrescriptionAdjustment.KEEP,
        rationale = "Dia verde.",
        ruleIds = listOf("R-PRESC-01"),
        evidenceIds = listOf("EV-SAW-2016"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = "daily-prescription-v6",
        inputsHash = "test-hash",
    )
}
