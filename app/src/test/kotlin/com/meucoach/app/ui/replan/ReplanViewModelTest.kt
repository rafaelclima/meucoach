package com.meucoach.app.ui.replan

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.application.ApplyReplan
import com.meucoach.app.application.EvaluateReplan
import com.meucoach.app.application.REPLAN_TODAY
import com.meucoach.app.application.REPLAN_WEEK_START
import com.meucoach.app.application.ReplanContextBuilder
import com.meucoach.app.application.easySession
import com.meucoach.app.application.qualitySession
import com.meucoach.app.application.replanCheckIn
import com.meucoach.app.application.replanPlan
import com.meucoach.app.application.replanProfile
import com.meucoach.app.replan.ReplanSuggestionStore
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.DailyMetricsRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.replan.PlanReplanner
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReplanViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: MeuCoachDatabase
    private lateinit var profiles: ProfileRepository
    private lateinit var checkIns: CheckInRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var store: ReplanSuggestionStore
    private lateinit var viewModel: ReplanViewModel
    private val clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC)
    private val timeProvider = FakeTimeProvider(clock.instant(), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        shadowOf(Looper.getMainLooper()).idle()
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room
            .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        profiles = ProfileRepository(database, clock)
        checkIns = CheckInRepository(database, clock)
        plans = TrainingPlanRepository(database, clock)
        val metrics = DailyMetricsRepository(database, clock)
        val activities = ActivityRepository(database = database, clock = clock, zoneId = ZoneOffset.UTC)
        val contexts = ReplanContextBuilder(
            plans = plans,
            profiles = profiles,
            checkIns = checkIns,
            metrics = metrics,
            activities = activities,
            timeProvider = timeProvider,
        )
        val evaluate = EvaluateReplan(contexts, plans)
        store = ReplanSuggestionStore(context, "replan-view-${System.nanoTime()}")
        viewModel = ReplanViewModel(
            plans = plans,
            evaluateReplan = evaluate,
            applyReplan = ApplyReplan(plans, contexts, evaluate),
            store = store,
            timeProvider = timeProvider,
        )
    }

    @After
    fun tearDown() {
        shadowOf(Looper.getMainLooper()).idle()
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh offers a suggestion when two sessions were missed`(): Unit = runBlocking {
        seed()

        viewModel.refresh()
        val state = awaitSuggestion()

        assertThat(state.planVersion).isEqualTo(1)
        assertThat(state.suggestion?.versionFrom).isEqualTo(1)
        assertThat(state.suggestion?.versionTo).isEqualTo(2)
        assertThat(state.suggestion?.changes).isNotEmpty()
    }

    @Test
    fun `a suppressed suggestion stays hidden but the explicit request still works`(): Unit = runBlocking {
        seed()
        viewModel.refresh()
        awaitSuggestion()

        viewModel.suppress()
        assertThat(viewModel.uiState.value.suggestion).isNull()
        assertThat(viewModel.uiState.value.message).isNotNull()

        viewModel.refresh()
        val automatic = awaitIdle()
        assertThat(automatic.suggestion).isNull()

        viewModel.replanNow()
        assertThat(awaitSuggestion().suggestion).isNotNull()
    }

    @Test
    fun `a declined proposal does not reappear on refresh`(): Unit = runBlocking {
        seed()
        viewModel.refresh()
        awaitSuggestion()

        viewModel.decline()
        viewModel.refresh()

        assertThat(awaitIdle().suggestion).isNull()
    }

    @Test
    fun `accept applies the revision and exposes the new version and diff`(): Unit = runBlocking {
        seed()
        viewModel.refresh()
        awaitSuggestion()

        viewModel.accept()
        val state = withTimeout(5_000) {
            viewModel.uiState.first { !it.isApplying && it.message?.contains("Semana ajustada") == true }
        }

        assertThat(state.planVersion).isEqualTo(2)
        assertThat(state.lastChange?.toVersion).isEqualTo(2)
        assertThat(state.lastChange?.changes).isNotEmpty()
        assertThat(plans.getWeekRecord(REPLAN_WEEK_START)?.version).isEqualTo(2)
    }

    @Test
    fun `accept re-evaluates when the inputs changed`(): Unit = runBlocking {
        seed()
        viewModel.refresh()
        awaitSuggestion()

        checkIns.save(replanCheckIn().copy(sleepQuality = 1, fatigue = 5))
        viewModel.accept()
        val state = withTimeout(5_000) {
            viewModel.uiState.first {
                !it.isApplying && it.message?.contains("dados mudaram") == true
            }
        }

        assertThat(state.suggestion).isNotNull
        assertThat(state.planVersion).isEqualTo(1)
        assertThat(plans.getWeekRecord(REPLAN_WEEK_START)?.version).isEqualTo(1)
    }

    @Test
    fun `an explicit request without a week explains why`(): Unit = runBlocking {
        viewModel.replanNow()

        val state = withTimeout(5_000) {
            viewModel.uiState.first { !it.isLoading && it.message != null }
        }

        assertThat(state.message).contains("semana planejada")
        assertThat(state.suggestion).isNull()
    }

    private suspend fun awaitSuggestion(): ReplanUiState = withTimeout(5_000) {
        viewModel.uiState.first { !it.isLoading && it.suggestion != null }
    }

    private suspend fun awaitIdle(): ReplanUiState = withTimeout(5_000) {
        viewModel.uiState.first { !it.isLoading }
    }

    private suspend fun seed() {
        profiles.saveProfile(replanProfile())
        checkIns.save(replanCheckIn())
        plans.saveWeek(
            replanPlan(
                sessions = listOf(
                    easySession(REPLAN_WEEK_START, durationMin = 40),
                    easySession(REPLAN_WEEK_START.plusDays(1), durationMin = 35),
                    easySession(REPLAN_TODAY, durationMin = 40),
                    qualitySession(REPLAN_WEEK_START.plusDays(4)),
                ),
            ),
        )
        plans.captureAvailabilityBaseline(
            REPLAN_WEEK_START,
            PlanReplanner.availabilityHash(replanProfile().weeklyAvailability),
        )
    }
}
