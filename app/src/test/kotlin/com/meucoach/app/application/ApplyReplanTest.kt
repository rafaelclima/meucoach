package com.meucoach.app.application

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.load.ActivitySourceStatus
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.model.CompletionEvidence
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApplyReplanTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var checkIns: CheckInRepository
    private lateinit var metrics: DailyMetricsRepository
    private lateinit var activities: ActivityRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var prescriptions: DailyPrescriptionRepository
    private lateinit var getPrescription: GetTodayPrescription
    private lateinit var evaluate: EvaluateReplan
    private lateinit var apply: ApplyReplan
    private val clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC)
    private val timeProvider = FakeTimeProvider(clock.instant(), ZoneOffset.UTC)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room
            .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        profiles = ProfileRepository(database, clock)
        checkIns = CheckInRepository(database, clock)
        metrics = DailyMetricsRepository(database, clock)
        activities = ActivityRepository(database = database, clock = clock, zoneId = ZoneOffset.UTC)
        plans = TrainingPlanRepository(database, clock)
        prescriptions = DailyPrescriptionRepository(database, clock)
        getPrescription = GetTodayPrescription(prescriptions)
        val contexts = ReplanContextBuilder(
            plans = plans,
            profiles = profiles,
            checkIns = checkIns,
            metrics = metrics,
            activities = activities,
            timeProvider = timeProvider,
        )
        evaluate = EvaluateReplan(contexts)
        apply = ApplyReplan(plans, contexts, evaluate)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `accepts the revision without touching today, the past or protected sessions`(): Unit = runBlocking {
        val plan = replanPlan(
            sessions = listOf(
                easySession(REPLAN_WEEK_START, durationMin = 40),
                easySession(REPLAN_WEEK_START.plusDays(1), durationMin = 35),
                easySession(REPLAN_TODAY, durationMin = 40),
                easySession(
                    REPLAN_WEEK_START.plusDays(3),
                    status = SessionStatus.DONE,
                    evidence = CompletionEvidence.MANUAL,
                ),
                qualitySession(REPLAN_WEEK_START.plusDays(4)),
            ),
        )
        profiles.saveProfile(replanProfile())
        checkIns.save(replanCheckIn())
        plans.saveWeek(plan)
        val before = plans.getWeek(REPLAN_WEEK_START)!!
        val beforePrescription = prescriptionFor(before)

        val evaluated = evaluate(explicit = true) as EvaluateReplanResult.Suggestion
        val outcome = apply(
            ApplyReplanRequest(
                suggestion = evaluated.suggestion,
                candidate = evaluated.candidate,
                today = REPLAN_TODAY,
                explicit = true,
            ),
        )

        assertThat(outcome).isInstanceOf(ApplyReplanResult.Applied::class.java)
        assertThat((outcome as ApplyReplanResult.Applied).version).isEqualTo(2)

        val after = plans.getWeek(REPLAN_WEEK_START)!!
        assertThat(after.sessions.filter { !it.date.isAfter(REPLAN_TODAY) })
            .isEqualTo(before.sessions.filter { !it.date.isAfter(REPLAN_TODAY) })
        val protectedBefore = before.sessions.first { it.date == REPLAN_WEEK_START.plusDays(3) }
        val protectedAfter = after.sessions.first { it.date == REPLAN_WEEK_START.plusDays(3) }
        assertThat(protectedAfter).isEqualTo(protectedBefore)
        assertThat(protectedAfter.status).isEqualTo(SessionStatus.DONE)
        assertThat(after.sessions.first { it.date == REPLAN_WEEK_START.plusDays(4) }.title)
            .isEqualTo("Fácil e conversável")

        val afterPrescription = prescriptionFor(after)
        assertThat(afterPrescription.id).isEqualTo(beforePrescription.id)
        assertThat(prescriptions.getVersions(REPLAN_TODAY)).hasSize(1)

        val revision = plans.getLatestRevision(REPLAN_WEEK_START)!!
        assertThat(revision.fromVersion).isEqualTo(1)
        assertThat(revision.toVersion).isEqualTo(2)
        assertThat(revision.replacedSessions.map { it.date })
            .containsExactly(REPLAN_WEEK_START.plusDays(4))
        assertThat(plans.getWeekRecord(REPLAN_WEEK_START)!!.version).isEqualTo(2)
    }

    @Test
    fun `rejects an outdated suggestion instead of applying it`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        checkIns.save(replanCheckIn())
        plans.saveWeek(replanPlan())
        val evaluated = evaluate(explicit = true) as EvaluateReplanResult.Suggestion
        val request = ApplyReplanRequest(
            suggestion = evaluated.suggestion,
            candidate = evaluated.candidate,
            today = REPLAN_TODAY,
            explicit = true,
        )
        assertThat(apply(request)).isInstanceOf(ApplyReplanResult.Applied::class.java)

        val stale = apply(request)

        assertThat(stale).isInstanceOf(ApplyReplanResult.Stale::class.java)
        assertThat((stale as ApplyReplanResult.Stale).reevaluated)
            .isInstanceOf(EvaluateReplanResult.NoChange::class.java)
        assertThat(plans.getWeekRecord(REPLAN_WEEK_START)!!.version).isEqualTo(2)
        assertThat(database.weeklyPlanRevisionDao().getAll()).hasSize(1)
    }

    @Test
    fun `re-evaluates when the inputs changed before acceptance`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        checkIns.save(replanCheckIn())
        plans.saveWeek(replanPlan())
        val evaluated = evaluate(explicit = true) as EvaluateReplanResult.Suggestion

        checkIns.save(replanCheckIn().copy(sleepQuality = 1, fatigue = 5))
        val outcome = apply(
            ApplyReplanRequest(
                suggestion = evaluated.suggestion,
                candidate = evaluated.candidate,
                today = REPLAN_TODAY,
                explicit = true,
            ),
        )

        assertThat(outcome).isInstanceOf(ApplyReplanResult.Stale::class.java)
        val fresh = (outcome as ApplyReplanResult.Stale).reevaluated
        assertThat(fresh).isInstanceOf(EvaluateReplanResult.Suggestion::class.java)
        assertThat((fresh as EvaluateReplanResult.Suggestion).suggestion.expectedInputsHash)
            .isNotEqualTo(evaluated.suggestion.expectedInputsHash)
        assertThat(plans.getWeekRecord(REPLAN_WEEK_START)!!.version).isEqualTo(1)
        assertThat(database.weeklyPlanRevisionDao().getAll()).isEmpty()
    }

    @Test
    fun `a deleted week is reported as unavailable without writing`(): Unit = runBlocking {
        profiles.saveProfile(replanProfile())
        checkIns.save(replanCheckIn())
        plans.saveWeek(replanPlan())
        val evaluated = evaluate(explicit = true) as EvaluateReplanResult.Suggestion
        plans.deleteWeek(REPLAN_WEEK_START)

        val outcome = apply(
            ApplyReplanRequest(
                suggestion = evaluated.suggestion,
                candidate = evaluated.candidate,
                today = REPLAN_TODAY,
                explicit = true,
            ),
        )

        assertThat(outcome).isInstanceOf(ApplyReplanResult.Stale::class.java)
        assertThat((outcome as ApplyReplanResult.Stale).reevaluated)
            .isInstanceOf(EvaluateReplanResult.Unavailable::class.java)
        assertThat(database.weeklyPlanRevisionDao().getAll()).isEmpty()
    }

    private suspend fun prescriptionFor(plan: WeeklyPlan) = getPrescription(
        TodayPrescriptionRequest(
            date = REPLAN_TODAY,
            profile = replanProfile(),
            readiness = readinessResult(replanCheckIn(), metrics.getAll(), REPLAN_TODAY),
            load = buildLoadSnapshot(
                activities = activities.listActivitySummaries(),
                profile = replanProfile(),
                today = REPLAN_TODAY,
                sourceStatus = ActivitySourceStatus(
                    lastImportedAt = activities.lastImportedAt(),
                    checkedAt = clock.instant(),
                ),
            ),
            plan = plan,
        ),
    )
}
