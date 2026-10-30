package com.meucoach.app.ui.activities

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.importer.ImportCoordinator
import com.meucoach.app.importer.ImportStaging
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Sport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class ActivitiesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: ActivityRepository
    private lateinit var staging: ImportStaging
    private lateinit var requests: MutableList<androidx.work.OneTimeWorkRequest>
    private lateinit var viewModel: ActivitiesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, ImportStaging.RELATIVE_DIR).deleteRecursively()
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
            )
        staging = ImportStaging(context, ImportStaging.MAX_IMPORT_BYTES, dispatcher)
        requests = mutableListOf()
        val coordinator = ImportCoordinator(staging) { request -> requests += request }
        viewModel = ActivitiesViewModel(repository, coordinator)
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, ImportStaging.RELATIVE_DIR).deleteRecursively()
        Dispatchers.resetMain()
    }

    @Test
    fun `starts loading and exposes an empty library`() {
        runBlocking {
            val state = withTimeout(TIMEOUT_MILLIS) {
                viewModel.uiState.first { !it.isLoading }
            }

            assertThat(state.activities).isEmpty()
            assertThat(state.recentImports).isEmpty()
            assertThat(state.message).isNull()
        }
    }

    @Test
    fun `exposes saved activities and recent import jobs`() {
        runBlocking {
            val collector = launch { viewModel.uiState.collect() }
            repository.saveImportedActivity(importedActivity(), "sha")

            val state = withTimeout(TIMEOUT_MILLIS) {
                viewModel.uiState.first { it.activities.isNotEmpty() }
            }

            assertThat(state.activities).hasSize(1)
            assertThat(state.activities.single().source).isEqualTo(ActivitySource.FIT)
            collector.cancel()
        }
    }

    @Test
    fun `stages selected files and publishes a queue message`() {
        runBlocking {
            val collector = launch { viewModel.uiState.collect() }
            val source = sourceFile("corrida.gpx", gpx())

            viewModel.import(listOf(Uri.fromFile(source)))
            val state = withTimeout(TIMEOUT_MILLIS) {
                viewModel.uiState.first { it.message != null }
            }

            assertThat(requests).hasSize(1)
            assertThat(state.message).contains("1 arquivo na fila")
            assertThat(state.isImporting).isFalse()
            collector.cancel()
        }
    }

    @Test
    fun `reports files that could not be staged`() {
        runBlocking {
            val collector = launch { viewModel.uiState.collect() }
            val missing = File(context.cacheDir, "sumiu.fit")

            viewModel.import(listOf(Uri.fromFile(missing)))
            val state = withTimeout(TIMEOUT_MILLIS) {
                viewModel.uiState.first { it.message != null }
            }

            assertThat(requests).isEmpty()
            assertThat(state.message).contains("sumiu.fit")
            collector.cancel()
        }
    }

    @Test
    fun `clears the message after it is shown`() {
        runBlocking {
            val collector = launch { viewModel.uiState.collect() }
            viewModel.import(listOf(Uri.fromFile(sourceFile("corrida.gpx", gpx()))))
            withTimeout(TIMEOUT_MILLIS) { viewModel.uiState.first { it.message != null } }

            viewModel.consumeMessage()

            val state = withTimeout(TIMEOUT_MILLIS) { viewModel.uiState.first { it.message == null } }
            assertThat(state.message).isNull()
            collector.cancel()
        }
    }

    @Test
    fun `ignores an empty selection`() {
        runBlocking {
            viewModel.import(emptyList())
            assertThat(requests).isEmpty()
            assertThat(viewModel.uiState.value.message).isNull()
        }
    }

    private fun importedActivity() = ImportedActivity(
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-10-24T08:17:10Z"),
        durationSec = 1_859,
        distanceM = 5_106.0,
        averageHeartRate = 150,
        maxHeartRate = 170,
        parserVersion = "1.0.0",
    )

    private fun sourceFile(name: String, bytes: ByteArray): File = File(context.cacheDir, name).apply {
        parentFile?.mkdirs()
        writeBytes(bytes)
    }

    private fun gpx(): ByteArray =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="meucoach.test" xmlns="http://www.topografix.com/GPX/1/1">
          <trk>
            <type>running</type>
            <trkseg>
              <trkpt lat="-5.79" lon="-35.21"><time>2026-10-24T08:17:10Z</time></trkpt>
              <trkpt lat="-5.79" lon="-35.20"><time>2026-10-24T08:18:10Z</time></trkpt>
            </trkseg>
          </trk>
        </gpx>
        """.trimIndent().toByteArray()

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
