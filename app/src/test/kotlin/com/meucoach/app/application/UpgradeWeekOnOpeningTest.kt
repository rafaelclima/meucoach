package com.meucoach.app.application

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.health.HealthSyncGate
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.replan.PlanUpgrade
import com.meucoach.core.domain.replan.ReplanTrigger
import com.meucoach.core.model.WeeklyAvailability
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * C4-48 (ADR 0048 §2.1–§2.2, FR-409): on opening the week upgrades itself
 * when the engine moved on or when the sync data arrived after an incomplete
 * generation. Anything else stays untouched for the user.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpgradeWeekOnOpeningTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var plans: TrainingPlanRepository
    private lateinit var upgrade: UpgradeWeekOnOpening
    private val clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC)
    private val timeProvider = FakeTimeProvider(clock.instant(), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 4)
    private val weekStart = LocalDate.of(2026, 11, 2)
    private val friday = weekStart.plusDays(4)
    private val sunday = weekStart.plusDays(6)
    private val ready = HealthSyncGate.Ready(clock.instant())

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room
            .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val profiles = ProfileRepository(database, clock)
        val checkIns = CheckInRepository(database, clock)
        val metrics = DailyMetricsRepository(database, clock)
        val activities = ActivityRepository(database = database, clock = clock, zoneId = ZoneOffset.UTC)
        plans = TrainingPlanRepository(database, clock)
        val contexts = ReplanContextBuilder(
            plans = plans,
            profiles = profiles,
            checkIns = checkIns,
            signals = TrainingSignalSource(
                checkIns = checkIns,
                metrics = metrics,
                activities = activities,
                executions = SessionExecutionRepository(database, clock),
            ),
            raceEvents = RaceEventRepository(database, clock),
            timeProvider = timeProvider,
        )
        upgrade = UpgradeWeekOnOpening(contexts, plans)
        runBlocking {
            profiles.saveProfile(
                replanProfile().copy(
                    weeklyAvailability = WeeklyAvailability(
                        mapOf(
                            DayOfWeek.MONDAY to 40,
                            DayOfWeek.WEDNESDAY to 40,
                            DayOfWeek.FRIDAY to 40,
                            DayOfWeek.SUNDAY to 40,
                        ),
                    ),
                ),
            )
            checkIns.save(replanCheckIn())
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `upgrades a stale week when the engine moved on`(): Unit = runBlocking {
        plans.saveWeek(storedWeek(engineVersion = "weekly-plan-v5"))

        val result = upgrade(ready)

        assertThat(result).isEqualTo(
            OpeningUpgradeResult.Upgraded(2, ReplanTrigger.ENGINE_UPGRADE),
        )
        val record = plans.getWeekRecord(weekStart)!!
        assertThat(record.version).isEqualTo(2)
        assertThat(record.generatorVersion).isEqualTo(WeeklyPlanGenerator.ENGINE_VERSION)
        val byDate = record.plan.sessions.associateBy { it.date }
        assertThat(byDate[today]?.durationMin).isEqualTo(40)
        assertThat(byDate[friday]?.durationMin).isEqualTo(40)
        assertThat(byDate[sunday]?.durationMin).isEqualTo(40)
        assertThat(plans.getLatestRevision(weekStart)!!.trigger)
            .isEqualTo(PlanUpgrade.ENGINE_UPGRADE_TRIGGER)
    }

    @Test
    fun `re-evaluates an incomplete week when the data arrives`(): Unit = runBlocking {
        plans.saveWeek(
            storedWeek(engineVersion = WeeklyPlanGenerator.ENGINE_VERSION).copy(
                ruleIds = listOf("R-PLAN-03", "R-DATA-01"),
            ),
        )

        val result = upgrade(ready)

        assertThat(result).isEqualTo(
            OpeningUpgradeResult.Upgraded(2, ReplanTrigger.DATA_READY),
        )
        val record = plans.getWeekRecord(weekStart)!!
        assertThat(record.version).isEqualTo(2)
        assertThat(record.plan.sessions.first { it.date == friday }.durationMin).isEqualTo(40)
        assertThat(plans.getLatestRevision(weekStart)!!.trigger)
            .isEqualTo(ReplanTrigger.DATA_READY.name)
    }

    @Test
    fun `keeps an incomplete week while the sync has failed`(): Unit = runBlocking {
        plans.saveWeek(
            storedWeek(engineVersion = WeeklyPlanGenerator.ENGINE_VERSION).copy(
                ruleIds = listOf("R-PLAN-03", "R-DATA-01"),
            ),
        )

        val result = upgrade(HealthSyncGate.Failed("Sem rede."))

        assertThat(result).isEqualTo(OpeningUpgradeResult.NotNeeded)
        assertThat(plans.getWeekRecord(weekStart)!!.version).isEqualTo(1)
        assertThat(database.weeklyPlanRevisionDao().getAll()).isEmpty()
    }

    @Test
    fun `leaves a current and complete week alone`(): Unit = runBlocking {
        plans.saveWeek(storedWeek(engineVersion = WeeklyPlanGenerator.ENGINE_VERSION))

        val result = upgrade(ready)

        assertThat(result).isEqualTo(OpeningUpgradeResult.NotNeeded)
        assertThat(plans.getWeekRecord(weekStart)!!.version).isEqualTo(1)
        assertThat(database.weeklyPlanRevisionDao().getAll()).isEmpty()
    }

    @Test
    fun `waits for the gate instead of upgrading blind`(): Unit = runBlocking {
        plans.saveWeek(storedWeek(engineVersion = "weekly-plan-v5"))

        val result = upgrade(HealthSyncGate.Syncing)

        assertThat(result).isEqualTo(OpeningUpgradeResult.GateOpen)
        assertThat(plans.getWeekRecord(weekStart)!!.version).isEqualTo(1)
        assertThat(database.weeklyPlanRevisionDao().getAll()).isEmpty()
    }

    @Test
    fun `reports no week when nothing was ever generated`(): Unit = runBlocking {
        assertThat(upgrade(ready)).isEqualTo(OpeningUpgradeResult.NoWeek)
    }

    private fun storedWeek(engineVersion: String): WeeklyPlan = replanPlan(
        sessions = listOf(
            easySession(today, durationMin = 40),
            easySession(friday, durationMin = 60),
            easySession(sunday, durationMin = 40).copy(type = WorkoutType.LONG, title = "Longão"),
        ),
    ).copy(engineVersion = engineVersion)
}
