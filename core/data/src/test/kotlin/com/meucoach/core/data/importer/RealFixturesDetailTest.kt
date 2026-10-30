package com.meucoach.core.data.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.ImportJobStatus
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.model.ActivitySource
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
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

/** Opens the seven sanitized real workouts end to end, from bytes to detail data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RealFixturesDetailTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: ActivityRepository
    private lateinit var service: ImportService
    private var nextId = 0

    @Before
    fun setUp() {
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
                idGenerator = { "activity-${++nextId}" },
            )
        service =
            ImportService(
                database = database,
                repository = repository,
                clock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC),
                jobIdGenerator = { "job-${++nextId}" },
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `imports and opens all four fit workouts`(): Unit = runBlocking {
        val files = listOf(
            "Zepp20261022195619.fit",
            "Zepp20261024051710.fit",
            "Zepp20261026202359.fit",
            "Zepp20261027052000.fit",
        )

        files.forEach { fileName ->
            val result = service.import(request("/fit/$fileName", fileName, ActivitySource.FIT))
            val imported = result as ImportResult.Imported
            assertThat(imported.source).isEqualTo(ActivitySource.FIT)

            val activity = repository.openActivity(imported.activityId)
            assertThat(activity).isNotNull
            assertThat(activity!!.imported.laps).isNotEmpty()
            assertThat(activity.imported.samples).isNotEmpty()
            assertThat(activity.sampleWarning).isNull()
            assertThat(activity.imported.durationSec).isPositive()
            assertThat(activity.imported.distanceM).isPositive()
        }

        assertThat(repository.listActivitySummaries()).hasSize(files.size)
        assertThat(database.importJobDao().getAll().map { it.status })
            .containsOnly(ImportJobStatus.DONE)
    }

    @Test
    fun `imports and opens all three tcx workouts with expected summaries`(): Unit = runBlocking {
        val expected = listOf(
            TcxExpectation("Zepp20261008052132.tcx", distanceM = 5_016.0, durationSec = 2_064, cadence = 185),
            TcxExpectation("Zepp20261013052204.tcx", distanceM = 5_037.0, durationSec = 1_976, cadence = 186),
            TcxExpectation("Zepp20261016053227.tcx", distanceM = 5_119.0, durationSec = 2_008, cadence = 188),
        )

        expected.forEach { expectation ->
            val result = service.import(
                request("/tcx/${expectation.fileName}", expectation.fileName, ActivitySource.TCX),
            )
            val imported = result as ImportResult.Imported
            assertThat(imported.source).isEqualTo(ActivitySource.TCX)

            val activity = repository.openActivity(imported.activityId)
            assertThat(activity).isNotNull
            assertThat(activity!!.sampleWarning).isNull()
            assertThat(activity.imported.distanceM).isEqualTo(expectation.distanceM)
            assertThat(activity.imported.durationSec).isEqualTo(expectation.durationSec)
            assertThat(activity.imported.averageCadenceSpm).isEqualTo(expectation.cadence)
            assertThat(activity.imported.laps).isNotEmpty()
            assertThat(activity.imported.samples).isNotEmpty()
        }

        assertThat(repository.listActivitySummaries()).hasSize(expected.size)
        assertThat(database.importJobDao().getAll().map { it.status })
            .containsOnly(ImportJobStatus.DONE)
    }

    private fun request(path: String, fileName: String, source: ActivitySource): ImportRequest {
        val stream = checkNotNull(javaClass.getResourceAsStream(path)) { "Missing test fixture $path" }
        val mime = if (source == ActivitySource.FIT) "application/vnd.garmin.fit" else "application/vnd.garmin.tcx+xml"
        return ImportRequest(
            bytes = stream.use { it.readBytes() },
            fileName = fileName,
            mime = mime,
            rawFilePath = "imports/$fileName",
        )
    }

    private data class TcxExpectation(
        val fileName: String,
        val distanceM: Double,
        val durationSec: Int,
        val cadence: Int,
    )
}
