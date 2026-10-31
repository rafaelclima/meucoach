package com.meucoach.app.ui.trends

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
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
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrendsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: MeuCoachDatabase
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
        profiles = ProfileRepository(database, clock)
        plans = TrainingPlanRepository(database, clock)
        activities =
            ActivityRepository(
                database = database,
                clock = clock,
                zoneId = ZoneId.of("America/Sao_Paulo"),
                idGenerator = { "activity-${System.nanoTime()}" },
            )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `builds the load snapshot from activities and profile`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        activities.saveImportedActivity(activity(), "sha")

        val state = TrendsViewModel(profiles, activities, plans).uiState.first { !it.isLoading }

        assertThat(state.snapshot).isNotNull
        assertThat(state.snapshot?.hasData).isTrue()
        assertThat(state.snapshot?.ctl).isGreaterThan(0.0)
        assertThat(state.snapshot?.daily).hasSize(42)
        assertThat(state.activityTrends?.totalKilometers).isEqualTo(5.0)
    }

    @Test
    fun `returns no snapshot without a profile`(): Unit = runBlocking {
        val state = TrendsViewModel(profiles, activities, plans).uiState.first { !it.isLoading }

        assertThat(state.profile).isNull()
        assertThat(state.snapshot).isNull()
    }

    @Test
    fun `reports missing load without activities`(): Unit = runBlocking {
        profiles.saveProfile(profile())

        val state = TrendsViewModel(profiles, activities, plans).uiState.first { !it.isLoading }

        assertThat(state.snapshot?.hasData).isFalse()
        assertThat(state.snapshot?.acwr).isNull()
        assertThat(state.intensity).isNull()
    }

    @Test
    fun `measures the intensity split from stored samples`(): Unit = runBlocking {
        profiles.saveProfile(profile().copy(lactateThresholdHeartRate = 160))
        activities.saveImportedActivity(
            activity().copy(
                samples = listOf(
                    ActivitySample(offsetSec = 0, heartRate = 140),
                    ActivitySample(offsetSec = 600, heartRate = 140),
                ),
            ),
            "sha",
        )

        val state = TrendsViewModel(profiles, activities, plans).uiState.first { !it.isLoading }

        assertThat(state.intensity).isNotNull
        assertThat(state.intensity?.easySec).isEqualTo(600)
        assertThat(state.intensity?.sessions).isEqualTo(1)
        assertThat(state.intensity?.confidence).isEqualTo(DataConfidence.LOW)
    }

    @Test
    fun `leaves intensity empty without a calibrated threshold`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        activities.saveImportedActivity(
            activity().copy(
                samples = listOf(
                    ActivitySample(offsetSec = 0, heartRate = 140),
                    ActivitySample(offsetSec = 600, heartRate = 140),
                ),
            ),
            "sha",
        )

        val state = TrendsViewModel(profiles, activities, plans).uiState.first { !it.isLoading }

        assertThat(state.intensity).isNull()
    }

    @Test
    fun `summarizes plan completion from stored sessions`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val today = LocalDate.now()
        val monday = today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
        plans.saveWeek(
            WeeklyPlan(
                weekStart = monday,
                sessions = listOf(
                    session(monday, SessionStatus.DONE),
                    session(today, SessionStatus.SKIPPED),
                ),
                weeklyMinutes = 60,
                longRunMinutes = null,
                qualityCount = 0,
                rationale = "Plano.",
                confidence = DataConfidence.MEDIUM,
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
            ),
        )

        val state = TrendsViewModel(profiles, activities, plans).uiState.first { !it.isLoading }

        assertThat(state.planCompletion?.planned).isEqualTo(2)
        assertThat(state.planCompletion?.done).isEqualTo(1)
        assertThat(state.planCompletion?.skipped).isEqualTo(1)
        assertThat(state.planCompletion?.completionRatio).isEqualTo(0.5)
    }

    @Test
    fun `reloads activity trends with the selected period`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val viewModel = TrendsViewModel(profiles, activities, plans)
        val initial = viewModel.uiState.first { !it.isLoading }

        assertThat(initial.period).isEqualTo(TrendsPeriod.WEEKS_6)
        assertThat(initial.activityTrends?.weeks).hasSize(6)
        assertThat(initial.activityTrends?.heatmap).isNotEmpty()
        assertThat(initial.activityTrends?.consistency?.windowDays).isEqualTo(42)

        viewModel.selectPeriod(TrendsPeriod.WEEKS_26)
        val updated = viewModel.uiState.first {
            it.period == TrendsPeriod.WEEKS_26 && it.activityTrends?.weeks?.size == 26
        }

        assertThat(updated.activityTrends?.weeks).hasSize(26)
        assertThat(updated.activityTrends?.consistency?.windowDays).isEqualTo(182)
    }

    private fun session(date: LocalDate, status: SessionStatus) = PlannedSession(
        date = date,
        type = WorkoutType.EASY,
        title = "Fácil",
        durationMin = 30,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = status,
    )

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        thresholdPace = Pace.fromSecondsPerKm(360),
        timezone = "America/Sao_Paulo",
    )

    private fun activity() = ImportedActivity(
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = Instant.now().minusSeconds(24 * 3_600),
        durationSec = 1_800,
        distanceM = 5_000.0,
        averageHeartRate = 150,
        averagePace = Pace.fromSecondsPerKm(360),
    )
}
