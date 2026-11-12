package com.meucoach.core.data.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.ImportJobStatus
import com.meucoach.core.data.db.testJob
import kotlinx.coroutines.CancellationException
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
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportJobGuardTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var guard: ImportJobGuard
    private val fixedClock = Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        guard = ImportJobGuard(database.importJobDao(), fixedClock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `closes the job when the pipeline throws`() {
        runBlocking {
            val job = pendingJob()
            database.importJobDao().insert(job)

            val result = guard.run(job) { throw IllegalStateException("boom") }

            assertThat(result).isInstanceOf(ImportResult.Failed::class.java)
            val stored = checkNotNull(database.importJobDao().findById(job.id))
            assertThat(stored.status).isEqualTo(ImportJobStatus.FAILED)
            assertThat(stored.errorCode).isEqualTo(ImportErrorCode.STORAGE_ERROR.name)
            assertThat(stored.errorMessage).isEqualTo(ImportJobGuard.UNEXPECTED_MESSAGE)
            assertThat(stored.finishedAt).isEqualTo(fixedClock.millis())
        }
    }

    @Test
    fun `cancellation keeps the job for WorkManager to resume`() {
        runBlocking {
            val job = pendingJob()
            database.importJobDao().insert(job)

            val failure = runCatching {
                guard.run(job) { throw CancellationException("parado") }
            }.exceptionOrNull()

            assertThat(failure).isInstanceOf(CancellationException::class.java)
            assertThat(database.importJobDao().findById(job.id)?.status).isEqualTo(ImportJobStatus.PARSING)
        }
    }

    @Test
    fun `returns the pipeline result untouched`() {
        runBlocking {
            val job = pendingJob()
            database.importJobDao().insert(job)

            val result = guard.run(job) { ImportResult.Skipped }

            assertThat(result).isEqualTo(ImportResult.Skipped)
            assertThat(database.importJobDao().findById(job.id)?.status).isEqualTo(ImportJobStatus.PARSING)
        }
    }

    private fun pendingJob() = testJob(activityId = null).copy(
        status = ImportJobStatus.PARSING,
        errorCode = null,
        errorMessage = null,
        finishedAt = null,
    )
}
