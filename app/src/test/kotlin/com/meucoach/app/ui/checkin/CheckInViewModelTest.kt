package com.meucoach.app.ui.checkin

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.model.DailyCheckIn
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
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CheckInViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: CheckInRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = CheckInRepository(
            database,
            Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `starts with neutral answers`(): Unit = runBlocking {
        val state = CheckInViewModel(repository).uiState.first { !it.isLoading }

        assertThat(state.existing).isFalse()
        assertThat(state.form.sleepQuality).isEqualTo(3)
        assertThat(state.form.fatigue).isEqualTo(3)
    }

    @Test
    fun `loads an existing check-in`(): Unit = runBlocking {
        val today = LocalDate.now()
        repository.save(
            DailyCheckIn(today, sleepQuality = 5, fatigue = 1, soreness = 2, stress = 2),
        )

        val state = CheckInViewModel(repository).uiState.first { !it.isLoading }

        assertThat(state.existing).isTrue()
        assertThat(state.form.sleepQuality).isEqualTo(5)
        assertThat(state.form.fatigue).isEqualTo(1)
    }

    @Test
    fun `loads the recent check-in history`(): Unit = runBlocking {
        val today = LocalDate.now()
        repository.save(DailyCheckIn(today.minusDays(1), sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2))
        repository.save(DailyCheckIn(today.minusDays(2), sleepQuality = 3, fatigue = 3, soreness = 2, stress = 3))
        repository.save(DailyCheckIn(today, sleepQuality = 5, fatigue = 1, soreness = 1, stress = 1))

        val state = CheckInViewModel(repository).uiState.first { it.history.size == 3 }

        assertThat(state.history.map { it.date })
            .containsExactly(today, today.minusDays(1), today.minusDays(2))
        assertThat(state.history.first().hooperSum).isEqualTo(4)
    }

    @Test
    fun `history refreshes after saving`(): Unit = runBlocking {
        val viewModel = CheckInViewModel(repository)
        viewModel.uiState.first { !it.isLoading }
        viewModel.update { it.copy(fatigue = 5) }

        viewModel.save()
        val state = withTimeout(5_000) {
            viewModel.uiState.first { it.isSaved && it.history.any { entry -> entry.date == LocalDate.now() } }
        }

        assertThat(state.history.first().fatigue).isEqualTo(5)
    }

    @Test
    fun `saves the check-in`(): Unit = runBlocking {
        val viewModel = CheckInViewModel(repository)
        viewModel.uiState.first { !it.isLoading }
        viewModel.update { it.copy(fatigue = 4, illnessOrInjury = true) }

        viewModel.save()
        withTimeout(5_000) { viewModel.uiState.first { it.isSaved } }

        val saved = repository.get(LocalDate.now())
        assertThat(saved?.fatigue).isEqualTo(4)
        assertThat(saved?.illnessOrInjury).isTrue()
        assertThat(viewModel.uiState.value.existing).isTrue()
    }
}
