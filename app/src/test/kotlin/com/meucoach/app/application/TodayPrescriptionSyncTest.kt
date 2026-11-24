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
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Sex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TodayPrescriptionSyncTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var checkIns: CheckInRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var prescriptions: DailyPrescriptionRepository
    private lateinit var sync: TodayPrescriptionSync
    private val today = LocalDate.of(2026, 11, 4)
    private val weekStart = LocalDate.of(2026, 11, 2)
    private val clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC)
    private val scope = CoroutineScope(Dispatchers.Default)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        profiles = ProfileRepository(database, clock)
        checkIns = CheckInRepository(database, clock)
        plans = TrainingPlanRepository(database, clock)
        prescriptions = DailyPrescriptionRepository(database, clock)
        val raceEvents = RaceEventRepository(database, clock)
        val time = FakeTimeProvider(clock.instant(), ZoneOffset.UTC)
        val signals = TrainingSignalSource(
            checkIns = checkIns,
            metrics = DailyMetricsRepository(database, clock),
            activities = ActivityRepository(database = database, clock = clock, zoneId = ZoneOffset.UTC),
            executions = SessionExecutionRepository(database, clock),
        )
        val refresh = RefreshTodayPrescription(
            profiles = profiles,
            plans = plans,
            raceEvents = raceEvents,
            signals = signals,
            getTodayDecision = GetTodayDecision(raceEvents, GetTodayPrescription(prescriptions)),
            time = time,
        )
        sync = TodayPrescriptionSync(
            signals = signals,
            profiles = profiles,
            plans = plans,
            races = raceEvents,
            refresh = refresh,
            time = time,
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    @Test
    fun `a check-in change appends a new day version without opening Hoje`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        plans.saveWeek(plan())
        checkIns.save(checkIn(sleepQuality = 5, fatigue = 1, soreness = 1, stress = 1))

        sync.start(scope)
        val first = awaitPrescription()
        assertThat(first.adjustment.name).isEqualTo("KEEP")

        checkIns.save(checkIn(sleepQuality = 1, fatigue = 5, soreness = 5, stress = 5))

        val adjusted = awaitPrescription { it.id != first.id }
        assertThat(adjusted.adjustment.name).isNotEqualTo("KEEP")
        assertThat(prescriptions.getVersions(today)).hasSizeGreaterThanOrEqualTo(2)
    }

    private suspend fun awaitPrescription(predicate: (DailyPrescription) -> Boolean = { true }): DailyPrescription =
        withTimeout(5_000) {
            while (true) {
                val latest = prescriptions.getLatest(today)
                if (latest != null && predicate(latest)) return@withTimeout latest
                delay(10)
            }
            @Suppress("UNREACHABLE_CODE")
            throw IllegalStateException("unreachable")
        }

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1990, 1, 1),
        sex = Sex.MALE,
        experience = Experience.INTERMEDIATE,
        timezone = "UTC",
    )

    private fun checkIn(sleepQuality: Int, fatigue: Int, soreness: Int, stress: Int) = DailyCheckIn(
        date = today,
        sleepQuality = sleepQuality,
        fatigue = fatigue,
        soreness = soreness,
        stress = stress,
    )

    private fun plan() = WeeklyPlan(
        weekStart = weekStart,
        sessions = listOf(
            PlannedSession(
                date = today,
                type = WorkoutType.EASY,
                title = "Rodagem",
                durationMin = 40,
                rationale = "Base.",
                ruleIds = listOf("R-PLAN-02"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.PLANNED,
            ),
        ),
        weeklyMinutes = 40,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Semana base.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        createdOn = weekStart,
    )
}
