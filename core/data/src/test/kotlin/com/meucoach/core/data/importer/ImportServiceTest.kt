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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportServiceTest {
    private lateinit var context: Context
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: ActivityRepository
    private lateinit var service: ImportService
    private val fixedClock = Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC)
    private var nextActivityId = 0
    private var nextJobId = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository =
            ActivityRepository(
                database = database,
                clock = fixedClock,
                zoneId = ZoneId.of("America/Sao_Paulo"),
                idGenerator = { "activity-${++nextActivityId}" },
            )
        service =
            ImportService(
                database = database,
                repository = repository,
                clock = fixedClock,
                jobIdGenerator = { "job-${++nextJobId}" },
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `imports a fit file and records a done job`() {
        runBlocking {
            val bytes = fixture("/fit/Zepp20261022195619.fit")

            val result = service.import(
                ImportRequest(
                    bytes = bytes,
                    fileName = "run.fit",
                    mime = "application/vnd.garmin.fit",
                    rawFilePath = "imports/run.fit",
                ),
            )

            assertThat(result).isInstanceOf(ImportResult.Imported::class.java)
            val imported = result as ImportResult.Imported
            assertThat(imported.source).isEqualTo(ActivitySource.FIT)
            assertThat(imported.activityId).isEqualTo("activity-1")
            assertThat(imported.jobId).isEqualTo("job-1")
            val job = database.importJobDao().findById(imported.jobId)
            assertThat(job?.status).isEqualTo(ImportJobStatus.DONE)
            assertThat(job?.activityId).isEqualTo("activity-1")
            assertThat(job?.parserVersion).isEqualTo("1.0.0")
            assertThat(job?.sizeBytes).isEqualTo(bytes.size.toLong())
            assertThat(job?.sha256).isEqualTo(Sha256.hex(bytes))
            assertThat(repository.listActivitySummaries()).hasSize(1)
            val stored = repository.openActivity(imported.activityId)
            assertThat(stored?.rawFilePath).isEqualTo("imports/run.fit")
            assertThat(stored?.imported?.source).isEqualTo(ActivitySource.FIT)
        }
    }

    @Test
    fun `imports tcx and gpx files`() {
        runBlocking {
            val tcx = service.import(
                ImportRequest(fixture("/tcx/Zepp20261008052132.tcx"), "run.tcx"),
            )
            val gpx = service.import(
                ImportRequest(fixture("/gpx/synthetic-extensions.gpx"), "run.gpx"),
            )

            assertThat(tcx).isInstanceOf(ImportResult.Imported::class.java)
            assertThat(gpx).isInstanceOf(ImportResult.Imported::class.java)
            assertThat((tcx as ImportResult.Imported).source).isEqualTo(ActivitySource.TCX)
            assertThat((gpx as ImportResult.Imported).source).isEqualTo(ActivitySource.GPX)
            assertThat(repository.listActivitySummaries()).hasSize(2)
            assertThat(database.importJobDao().getAll()).allSatisfy {
                assertThat(it.status).isEqualTo(ImportJobStatus.DONE)
            }
        }
    }

    @Test
    fun `duplicate file hash returns the existing activity and records a duplicate job`() {
        runBlocking {
            val bytes = fixture("/fit/Zepp20261022195619.fit")
            val request = ImportRequest(bytes, "run.fit")

            val first = service.import(request)
            val second = service.import(request)

            assertThat(first).isInstanceOf(ImportResult.Imported::class.java)
            assertThat(second).isInstanceOf(ImportResult.Duplicate::class.java)
            val existingId = (first as ImportResult.Imported).activityId
            assertThat((second as ImportResult.Duplicate).activityId).isEqualTo(existingId)
            assertThat(repository.listActivitySummaries()).hasSize(1)
            val jobs = database.importJobDao().getAll().sortedBy { it.id }
            assertThat(jobs.map { it.status })
                .containsExactly(ImportJobStatus.DONE, ImportJobStatus.DUPLICATE)
            assertThat(jobs.last().activityId).isEqualTo(existingId)
        }
    }

    @Test
    fun `detects fit by magic when the file has no extension`() {
        runBlocking {
            val result = service.import(
                ImportRequest(fixture("/fit/Zepp20261022195619.fit"), "upload"),
            )

            assertThat(result).isInstanceOf(ImportResult.Imported::class.java)
            assertThat((result as ImportResult.Imported).source).isEqualTo(ActivitySource.FIT)
        }
    }

    @Test
    fun `detects gpx by root element when the file has no extension`() {
        runBlocking {
            val result = service.import(
                ImportRequest(fixture("/gpx/synthetic-extensions.gpx"), "upload"),
            )

            assertThat(result).isInstanceOf(ImportResult.Imported::class.java)
            assertThat((result as ImportResult.Imported).source).isEqualTo(ActivitySource.GPX)
        }
    }

    @Test
    fun `rejects unsupported files and records a failed job`() {
        runBlocking {
            val result = service.import(
                ImportRequest("not a workout".toByteArray(), "notes.txt", "text/plain"),
            )

            assertThat(result).isInstanceOf(ImportResult.Failed::class.java)
            val failed = result as ImportResult.Failed
            assertThat(failed.code).isEqualTo(ImportErrorCode.UNSUPPORTED_FORMAT)
            assertThat(failed.message).contains("FIT")
            val job = database.importJobDao().findById(failed.jobId)
            assertThat(job?.status).isEqualTo(ImportJobStatus.FAILED)
            assertThat(job?.errorCode).isEqualTo(ImportErrorCode.UNSUPPORTED_FORMAT.name)
            assertThat(repository.listActivitySummaries()).isEmpty()
        }
    }

    @Test
    fun `malformed fit fails without persisting an activity`() {
        runBlocking {
            val result = service.import(
                ImportRequest("not a fit file".toByteArray(), "broken.fit"),
            )

            assertThat(result).isInstanceOf(ImportResult.Failed::class.java)
            val failed = result as ImportResult.Failed
            assertThat(failed.code).isEqualTo(ImportErrorCode.PARSE_ERROR)
            assertThat(failed.message).isNotBlank()
            val job = database.importJobDao().findById(failed.jobId)
            assertThat(job?.status).isEqualTo(ImportJobStatus.FAILED)
            assertThat(job?.errorCode).isEqualTo(ImportErrorCode.PARSE_ERROR.name)
            assertThat(repository.listActivitySummaries()).isEmpty()
        }
    }

    @Test
    fun `hashes bytes with sha256`() {
        assertThat(Sha256.hex("abc".toByteArray()))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }

    private fun fixture(path: String): ByteArray {
        val stream = checkNotNull(javaClass.getResourceAsStream(path)) {
            "Missing test fixture $path"
        }
        return stream.use { it.readBytes() }
    }
}
