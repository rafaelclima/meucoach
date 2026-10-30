package com.meucoach.app.ui.trends

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.model.DailyCheckIn
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
class ReadinessHistoryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: MeuCoachDatabase
    private lateinit var checkIns: CheckInRepository
    private lateinit var metrics: DailyMetricsRepository

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
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `exposes check-in history`(): Unit = runBlocking {
        val today = LocalDate.now()
        checkIns.save(DailyCheckIn(today, sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2))

        val state = ReadinessHistoryViewModel(checkIns, metrics).uiState.first { !it.isLoading }

        assertThat(state.days).hasSize(1)
        assertThat(state.days.single().date).isEqualTo(today)
        assertThat(state.days.single().hasCheckIn).isTrue()
    }

    @Test
    fun `starts with an empty history`(): Unit = runBlocking {
        val state = ReadinessHistoryViewModel(checkIns, metrics).uiState.first { !it.isLoading }

        assertThat(state.days).isEmpty()
    }
}
