package com.meucoach.app.time

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.application.GetTodayPrescription
import com.meucoach.app.ui.checkin.CheckInViewModel
import com.meucoach.app.ui.today.TodayViewModel
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.DailyPrescriptionRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimeDrivenStateTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: MeuCoachDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room
            .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `check-in at 23 59 stays on the current day and 00 01 shows the new day`(): Unit = runBlocking {
        val zone = ZoneId.of("America/Sao_Paulo")
        val clock = Clock.fixed(Instant.parse("2026-11-07T02:59:00Z"), ZoneOffset.UTC)
        val repository = CheckInRepository(database, clock)
        val time = FakeTimeProvider(Instant.parse("2026-11-07T02:59:00Z"), zone)
        val viewModel = CheckInViewModel(repository, time)

        val before = withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }
        assertThat(before.date).isEqualTo(LocalDate.of(2026, 11, 6))

        viewModel.update { it.copy(fatigue = 5) }
        viewModel.save()
        withTimeout(5_000) { viewModel.uiState.first { it.isSaved } }
        assertThat(repository.get(LocalDate.of(2026, 11, 6))).isNotNull
        assertThat(repository.get(LocalDate.of(2026, 11, 7))).isNull()

        time.setNow(Instant.parse("2026-11-07T03:01:00Z"))
        val after = withTimeout(5_000) {
            viewModel.uiState.first { it.date == LocalDate.of(2026, 11, 7) && !it.isLoading }
        }
        assertThat(after.existing).isFalse()
        assertThat(after.form.fatigue).isEqualTo(3)

        viewModel.update { it.copy(fatigue = 2) }
        viewModel.save()
        withTimeout(5_000) { viewModel.uiState.first { it.isSaved } }
        assertThat(repository.get(LocalDate.of(2026, 11, 7))).isNotNull
        assertThat(repository.get(LocalDate.of(2026, 11, 6))?.fatigue).isEqualTo(5)
    }

    @Test
    fun `timezone change recalculates the local check-in date`(): Unit = runBlocking {
        val clock = Clock.fixed(Instant.parse("2026-11-07T03:30:00Z"), ZoneOffset.UTC)
        val repository = CheckInRepository(database, clock)
        val time = FakeTimeProvider(Instant.parse("2026-11-07T03:30:00Z"), ZoneId.of("America/Sao_Paulo"))
        val viewModel = CheckInViewModel(repository, time)

        withTimeout(5_000) {
            viewModel.uiState.first { !it.isLoading && it.date == LocalDate.of(2026, 11, 7) }
        }

        time.setZone(ZoneId.of("America/New_York"))
        val state = withTimeout(5_000) {
            viewModel.uiState.first { it.date == LocalDate.of(2026, 11, 6) && !it.isLoading }
        }

        assertThat(state.date).isEqualTo(LocalDate.of(2026, 11, 6))
        assertThat(time.now()).isEqualTo(Instant.parse("2026-11-07T03:30:00Z"))
    }

    @Test
    fun `today reloads the plan when the week rolls over`(): Unit = runBlocking {
        val clock = Clock.fixed(Instant.parse("2026-11-08T12:00:00Z"), ZoneOffset.UTC)
        val checkIns = CheckInRepository(database, clock)
        val metrics = DailyMetricsRepository(database, clock)
        val profiles = ProfileRepository(database, clock)
        val activities = ActivityRepository(
            database = database,
            clock = clock,
            zoneId = ZoneId.of("America/Sao_Paulo"),
        )
        val plans = TrainingPlanRepository(database, clock)
        val prescriptions = DailyPrescriptionRepository(database, clock)
        val sunday = LocalDate.of(2026, 11, 8)
        profiles.saveProfile(profile())
        checkIns.save(DailyCheckIn(sunday, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2))

        val time = FakeTimeProvider(Instant.parse("2026-11-08T12:00:00Z"), ZoneId.of("America/Sao_Paulo"))
        val viewModel = TodayViewModel(
            checkIns,
            metrics,
            profiles,
            activities,
            plans,
            time,
            GetTodayPrescription(prescriptions),
        )

        val sundayState = withTimeout(5_000) {
            viewModel.uiState.first { it.today == sunday && it.plan?.sessions?.isNotEmpty() == true }
        }
        assertThat(sundayState.plan?.weekStart).isEqualTo(sunday.weekStart())

        time.setNow(Instant.parse("2026-11-09T12:00:00Z"))
        val monday = LocalDate.of(2026, 11, 9)
        val mondayState = withTimeout(5_000) {
            viewModel.uiState.first { it.today == monday && it.plan?.weekStart == monday }
        }

        assertThat(mondayState.today).isEqualTo(monday)
        assertThat(mondayState.plan?.weekStart).isEqualTo(monday.weekStart())
        assertThat(mondayState.plan?.weekStart?.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        assertThat(plans.getWeek(monday)?.weekStart).isEqualTo(monday)
    }

    private fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    @Test
    fun `the profile timezone is the single source for the local day`(): Unit = runBlocking {
        val clock = Clock.fixed(Instant.parse("2026-11-07T03:30:00Z"), ZoneOffset.UTC)
        val profiles = ProfileRepository(database, clock)
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.Dispatchers.Unconfined + kotlinx.coroutines.SupervisorJob(),
        )
        val provider = ProfileTimeProvider(profiles, clock, scope)
        try {
            profiles.saveProfile(profile(timezone = "America/New_York"))
            withTimeout(5_000) { provider.zoneId.first { it.id == "America/New_York" } }
            assertThat(provider.currentDate()).isEqualTo(LocalDate.of(2026, 11, 6))

            profiles.saveProfile(profile(timezone = "America/Sao_Paulo"))
            withTimeout(5_000) { provider.zoneId.first { it.id == "America/Sao_Paulo" } }

            assertThat(provider.currentDate()).isEqualTo(LocalDate.of(2026, 11, 7))
            assertThat(provider.now()).isEqualTo(Instant.parse("2026-11-07T03:30:00Z"))
        } finally {
            scope.cancel()
        }
    }

    private fun profile(timezone: String = "America/Sao_Paulo") = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        timezone = timezone,
        weeklyAvailability = WeeklyAvailability(
            mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.FRIDAY to 40,
                DayOfWeek.SUNDAY to 90,
            ),
        ),
    )
}
