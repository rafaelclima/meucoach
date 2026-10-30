package com.meucoach.app.ui.calibration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.domain.calibration.CalibrationTier
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.AthleteProfile
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
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalibrationViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: MeuCoachDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var activities: ActivityRepository

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
    fun `loads the profile and counts recent efforts`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        activities.saveImportedActivity(activity(daysAgo = 10), "sha-1")
        activities.saveImportedActivity(activity(daysAgo = 20).copy(averageHeartRate = null), "sha-2")

        val state = viewModel().uiState.first { !it.isLoading }

        assertThat(state.profile?.id).isEqualTo("athlete-1")
        assertThat(state.recentEfforts).isEqualTo(1)
    }

    @Test
    fun `calculates a tier A result from a recent race`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val viewModel = viewModel()
        viewModel.uiState.first { !it.isLoading }
        fillRace(viewModel)

        viewModel.calculate()

        val result = viewModel.uiState.value.result
        assertThat(result).isNotNull
        assertThat(result?.tier).isEqualTo(CalibrationTier.A)
        assertThat(result?.vdot).isNotNull
        assertThat(result?.lthr).isEqualTo(181)
    }

    @Test
    fun `rejects invalid input and clears the previous result`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val viewModel = viewModel()
        viewModel.uiState.first { !it.isLoading }
        fillRace(viewModel)
        viewModel.calculate()
        assertThat(viewModel.uiState.value.result).isNotNull

        viewModel.update { it.copy(time = "abc") }
        assertThat(viewModel.uiState.value.result).isNull()
        viewModel.calculate()

        assertThat(viewModel.uiState.value.result).isNull()
        assertThat(viewModel.uiState.value.errors.time).isNotNull()
    }

    @Test
    fun `saves calibration values into the profile`(): Unit = runBlocking {
        profiles.saveProfile(profile())
        val viewModel = viewModel()
        viewModel.uiState.first { !it.isLoading }
        fillRace(viewModel)
        viewModel.calculate()

        viewModel.save()
        withTimeout(5_000) { viewModel.uiState.first { it.isSaved } }

        val saved = profiles.getProfile()
        assertThat(saved?.vdot).isNotNull
        assertThat(saved?.lactateThresholdHeartRate).isEqualTo(181)
        assertThat(saved?.thresholdPace).isNotNull
        assertThat(saved?.maxHeartRateObserved).isEqualTo(187)
    }

    private fun viewModel() = CalibrationViewModel(profiles, activities)

    private fun fillRace(viewModel: CalibrationViewModel) {
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))
        viewModel.update {
            it.copy(
                distanceKm = "5",
                time = "30:59",
                date = date,
                averageHeartRate = "181",
            )
        }
    }

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        timezone = "America/Sao_Paulo",
    )

    private fun activity(daysAgo: Long) = ImportedActivity(
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = Instant.now().minusSeconds(daysAgo * 24 * 3_600),
        durationSec = 1_800,
        distanceM = 5_000.0,
        averageHeartRate = 150,
        averagePace = Pace.fromSecondsPerKm(360),
    )
}
