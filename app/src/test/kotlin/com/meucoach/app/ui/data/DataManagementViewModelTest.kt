package com.meucoach.app.ui.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.data.DataResetResult
import com.meucoach.app.data.DataResetter
import com.meucoach.app.firstrun.FirstRunMetricsStore
import com.meucoach.app.importer.ImportStaging
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.health.HealthSyncStore
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.UserDataRepository
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
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataManagementViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: MeuCoachDatabase
    private lateinit var userData: UserDataRepository
    private lateinit var activities: ActivityRepository
    private lateinit var staging: ImportStaging
    private lateinit var firstRunMetrics: FirstRunMetricsStore
    private lateinit var viewModel: DataManagementViewModel
    private val clock = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        userData = UserDataRepository(
            database,
            HealthSyncStore(context, prefsName = "data-management-${System.nanoTime()}"),
            clock,
        )
        firstRunMetrics = FirstRunMetricsStore(
            context,
            clock,
            prefsName = "data-management-metrics-${System.nanoTime()}",
        )
        activities = ActivityRepository(
            database = database,
            clock = clock,
            zoneId = ZoneId.of("America/Sao_Paulo"),
            idGenerator = { "activity-1" },
        )
        staging = ImportStaging(context)
        viewModel = DataManagementViewModel(
            userData = userData,
            activities = activities,
            staging = staging,
            resetCoordinator = object : DataResetter {
                override suspend fun reset() = DataResetResult(
                    deletion = userData.deleteAllData(),
                    epoch = "epoch-test",
                )
            },
            firstRunMetrics = firstRunMetrics,
            context = context,
            timeProvider = FakeTimeProvider(clock.instant(), ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `exports the backup to the chosen document`(): Unit = runBlocking {
        seedProfileAndActivity()
        val file = File(context.cacheDir, "backup.json").apply { delete() }

        viewModel.export(Uri.fromFile(file))

        val state = viewModel.uiState.first { !it.isExporting }
        assertThat(state.error).isNull()
        assertThat(state.message).contains("Backup exportado")
        val export = JSONObject(file.readText())
        assertThat(export.getJSONArray("activities").length()).isEqualTo(1)
        assertThat(export.getJSONObject("profile").getString("id")).isEqualTo("athlete-1")
        assertThat(export.has("firstRun")).isTrue()
    }

    @Test
    fun `reports a write failure without crashing`(): Unit = runBlocking {
        seedProfileAndActivity()
        val directory = File(context.cacheDir, "not-a-file").apply { mkdirs() }

        viewModel.export(Uri.fromFile(directory))

        val state = viewModel.uiState.first { !it.isExporting }
        assertThat(state.message).isNull()
        assertThat(state.error).isNotNull()
    }

    @Test
    fun `deletes everything and summarizes what was removed`(): Unit = runBlocking {
        seedProfileAndActivity()

        viewModel.deleteAllData()

        val state = viewModel.uiState.first { !it.isDeleting }
        assertThat(state.message)
            .isEqualTo(
                "Dados apagados deste aparelho: 1 corrida(s), 0 check-in(s), " +
                    "0 dia(s) de métricas e 0 plano(s). O app volta para a configuração inicial.",
            )
        assertThat(database.activityDao().getAllIncludingDeleted()).isEmpty()
        assertThat(database.profileDao().get()).isNull()
    }

    @Test
    fun `lists, restores and permanently deletes trash items`(): Unit = runBlocking {
        val activityId = seedProfileAndActivity()
        activities.deleteActivity(activityId)

        val trash = viewModel.uiState.first { it.trash.isNotEmpty() }.trash.single()
        assertThat(trash.id).isEqualTo(activityId)
        assertThat(trash.daysRemaining).isEqualTo(7)

        viewModel.restoreActivity(activityId)
        viewModel.uiState.first { it.trash.isEmpty() }
        assertThat(database.activityDao().findById(activityId)).isNotNull

        activities.deleteActivity(activityId)
        viewModel.uiState.first { it.trash.isNotEmpty() }
        viewModel.deletePermanently(activityId)
        viewModel.uiState.first { it.trash.isEmpty() }

        assertThat(database.activityDao().findByIdIncludingDeleted(activityId)).isNull()
    }

    @Test
    fun `default export excludes trash and the toggle includes it`(): Unit = runBlocking {
        val activityId = seedProfileAndActivity()
        activities.deleteActivity(activityId)
        val file = File(context.cacheDir, "backup.json").apply { delete() }

        viewModel.export(Uri.fromFile(file))
        viewModel.uiState.first { !it.isExporting && it.message != null }
        val default = JSONObject(file.readText())
        assertThat(default.getJSONArray("activities").length()).isZero()

        viewModel.toggleIncludeTrash()
        viewModel.export(Uri.fromFile(file))
        viewModel.uiState.first { !it.isExporting && it.message != null }
        val complete = JSONObject(file.readText())
        assertThat(complete.getJSONArray("activities").length()).isEqualTo(1)
    }

    private suspend fun seedProfileAndActivity(): String {
        ProfileRepository(database, clock).saveProfile(
            AthleteProfile(
                id = "athlete-1",
                birthDate = LocalDate.of(1988, 1, 1),
                sex = Sex.MALE,
                timezone = "America/Sao_Paulo",
            ),
        )
        ActivityRepository(
            database = database,
            clock = clock,
            zoneId = ZoneId.of("America/Sao_Paulo"),
            idGenerator = { "activity-1" },
        ).saveImportedActivity(
            ImportedActivity(
                source = ActivitySource.FIT,
                sport = Sport.RUNNING,
                startTime = Instant.parse("2026-11-02T10:00:00Z"),
                durationSec = 1_800,
                distanceM = 5_000.0,
                averagePace = Pace.fromSecondsPerKm(360),
            ),
        )
        return "activity-1"
    }
}
