package com.meucoach.app.ui.today

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
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
                idGenerator = { "activity-1" },
            )
        plans = TrainingPlanRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `generates and persists the current week once`(): Unit = runBlocking {
        val today = LocalDate.now()
        profiles.saveProfile(profile(today))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )

        val viewModel = TodayViewModel(checkIns, metrics, profiles, activities, plans)
        val state = withTimeout(5_000) {
            viewModel.uiState.first { it.plan != null && it.plan.sessions.isNotEmpty() }
        }

        val stored = plans.getWeek(state.plan!!.weekStart)
        assertThat(stored).isNotNull
        assertThat(stored?.sessions).hasSameSizeAs(state.plan.sessions)
    }

    @Test
    fun `uses completed previous week to add a second quality`(): Unit = runBlocking {
        val today = LocalDate.now()
        val weekStart = today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
        profiles.saveProfile(profile(today, experience = com.meucoach.core.model.Experience.INTERMEDIATE))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        plans.saveWeek(previousWeek(weekStart.minusWeeks(1)))

        val viewModel = TodayViewModel(checkIns, metrics, profiles, activities, plans)
        val state = withTimeout(5_000) {
            viewModel.uiState.first { it.plan?.qualityCount == 2 }
        }

        assertThat(state.plan?.qualityCount).isEqualTo(2)
        assertThat(state.plan?.rationale).contains("dose de qualidade")
    }

    @Test
    fun `deloads after three consecutive planned weeks`(): Unit = runBlocking {
        val today = LocalDate.now()
        val weekStart = today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
        profiles.saveProfile(profile(today))
        checkIns.save(
            DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
        )
        (1L..3L).forEach { weeksAgo ->
            plans.saveWeek(previousWeek(weekStart.minusWeeks(weeksAgo)))
        }

        val viewModel = TodayViewModel(checkIns, metrics, profiles, activities, plans)
        val state = withTimeout(5_000) {
            viewModel.uiState.first { it.plan?.rationale?.contains("Deload") == true }
        }

        assertThat(state.plan?.ruleIds).contains("R-PLAN-04")
        assertThat(state.plan?.evidenceIds).contains("EV-ISSURIN-2010")
        assertThat(state.plan?.rationale).contains("semana 4 do bloco")
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

        val viewModel = TodayViewModel(checkIns, metrics, profiles, activities, plans)
        viewModel.swapSession(weekStart, weekStart.plusDays(1), today = weekStart)
        withTimeout(5_000) {
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
