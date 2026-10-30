package com.meucoach.app.ui.activitydetail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.domain.insights.ActivityInsightRules
import com.meucoach.core.model.ActivityLap
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActivityDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: ActivityRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository =
            ActivityRepository(
                database = database,
                clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC),
                zoneId = ZoneId.of("America/Sao_Paulo"),
                idGenerator = { "activity-1" },
            )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `loads an activity by id`() {
        runBlocking {
            repository.saveImportedActivity(importedActivity(), "sha")

            val viewModel = viewModel("activity-1")
            val state = viewModel.uiState.first { !it.isLoading }

            assertThat(state.notFound).isFalse()
            assertThat(state.activity?.id).isEqualTo("activity-1")
            assertThat(state.activity?.imported?.distanceM).isEqualTo(5_000.0)
            assertThat(state.insights.map { it.ruleId }).containsExactly(ActivityInsightRules.PACING)
        }
    }

    @Test
    fun `marks a missing activity as not found`() {
        runBlocking {
            val state = viewModel("missing").uiState.first { !it.isLoading }

            assertThat(state.notFound).isTrue()
            assertThat(state.activity).isNull()
        }
    }

    @Test
    fun `requires an activity id`() {
        assertThatThrownBy { ActivityDetailViewModel(SavedStateHandle(), repository) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    private fun viewModel(activityId: String) = ActivityDetailViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(ActivityDetailViewModel.ARG_ACTIVITY_ID to activityId),
        ),
        repository = repository,
    )

    private fun importedActivity() = ImportedActivity(
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-10-24T08:17:10Z"),
        durationSec = 1_800,
        distanceM = 5_000.0,
        averageHeartRate = 150,
        maxHeartRate = 170,
        averagePace = Pace.fromSecondsPerKm(360),
        averageCadenceSpm = 170,
        averagePowerW = 250,
        elevationGainM = 40.0,
        movingTimeSec = 1_750,
        parserVersion = "1.0.0",
        laps = listOf(
            ActivityLap(
                index = 0,
                durationSec = 1_000,
                distanceM = 2_500.0,
                averageHeartRate = 150,
            ),
            ActivityLap(
                index = 1,
                durationSec = 800,
                distanceM = 2_500.0,
                averageHeartRate = 155,
            ),
        ),
    )
}
