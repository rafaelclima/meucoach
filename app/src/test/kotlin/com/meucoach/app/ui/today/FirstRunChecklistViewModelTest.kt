package com.meucoach.app.ui.today

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.firstrun.FirstRunMetricsStore
import com.meucoach.app.ui.tutorial.TutorialStore
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.health.HealthConnectReadResult
import com.meucoach.core.data.health.HealthConnectStatus
import com.meucoach.core.data.health.HealthDailySource
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.model.DailyCheckIn
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
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirstRunChecklistViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: MeuCoachDatabase
    private lateinit var store: TutorialStore
    private lateinit var metrics: FirstRunMetricsStore
    private lateinit var checkInRepository: CheckInRepository
    private lateinit var activityRepository: ActivityRepository
    private var currentViewModel: FirstRunChecklistViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(TutorialStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = TutorialStore(context)
        metrics = FirstRunMetricsStore(context, prefsName = "checklist-metrics-${System.nanoTime()}")
        database = Room
            .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        checkInRepository = CheckInRepository(database)
        activityRepository = ActivityRepository(database)
    }

    @After
    fun tearDown() {
        currentViewModel?.viewModelScope?.cancel()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `shows the checklist when nothing is done`() {
        val viewModel = viewModel()

        val state = runBlocking {
            withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }
        }

        assertThat(state.completed).isZero()
        assertThat(state.shouldShow).isTrue()
    }

    @Test
    fun `marks the check-in as completed`() {
        runBlocking {
            checkInRepository.save(
                DailyCheckIn(LocalDate.now(), sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2),
            )
        }
        val viewModel = viewModel()

        val state = runBlocking {
            withTimeout(5_000) { viewModel.uiState.first { !it.isLoading && it.hasCheckedIn } }
        }

        assertThat(state.completed).isEqualTo(1)
        assertThat(state.shouldShow).isTrue()
    }

    @Test
    fun `marks health as connected`() {
        val viewModel = viewModel(healthConnected = true)

        val state = runBlocking {
            withTimeout(5_000) { viewModel.uiState.first { !it.isLoading && it.healthConnected } }
        }

        assertThat(state.completed).isEqualTo(1)
    }

    @Test
    fun `dismisses and persists the checklist`() {
        val viewModel = viewModel()
        runBlocking { withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } } }

        viewModel.dismiss()

        assertThat(viewModel.uiState.value.shouldShow).isFalse()
        assertThat(store.isChecklistDismissed()).isTrue()
    }

    @Test
    fun `hides the checklist when all three steps are done`() {
        val state = FirstRunChecklistState(
            isLoading = false,
            hasCheckedIn = true,
            hasActivities = true,
            healthConnected = true,
        )

        assertThat(state.completed).isEqualTo(3)
        assertThat(state.shouldShow).isFalse()
    }

    private fun viewModel(healthConnected: Boolean = false): FirstRunChecklistViewModel = FirstRunChecklistViewModel(
        checkInRepository = checkInRepository,
        activityRepository = activityRepository,
        healthSource = FakeChecklistHealthSource(healthConnected),
        store = store,
        metrics = metrics,
    ).also { currentViewModel = it }
}

private class FakeChecklistHealthSource(private val connected: Boolean) : HealthDailySource {
    override val requiredPermissions: Set<String> = setOf("sleep")

    override suspend fun status(): HealthConnectStatus = HealthConnectStatus.AVAILABLE

    override suspend fun grantedPermissions(): Set<String> = if (connected) requiredPermissions else emptySet()

    override suspend fun read(from: LocalDate, to: LocalDate, zoneId: ZoneId): HealthConnectReadResult =
        HealthConnectReadResult(days = emptyList(), missingPermissions = emptySet())
}
