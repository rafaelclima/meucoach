package com.meucoach.app.ui.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
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
        userData = UserDataRepository(database, clock)
        viewModel = DataManagementViewModel(userData, context)
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

    private suspend fun seedProfileAndActivity() {
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
    }
}
