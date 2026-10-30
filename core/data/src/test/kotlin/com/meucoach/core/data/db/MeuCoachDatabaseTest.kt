package com.meucoach.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.entity.ImportJobStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MeuCoachDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: MeuCoachDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `inserts and reads an activity aggregate`() {
        runBlocking {
            val activity = testActivity()
            val lap = testLap()
            val blob = testBlob()
            val job = testJob()

            database.activityDao().insert(activity)
            database.lapDao().insertAll(listOf(lap))
            database.sampleBlobDao().upsert(blob)
            database.importJobDao().insert(job)

            assertThat(database.activityDao().findById(activity.id)).isEqualTo(activity)
            assertThat(database.activityDao().getAll()).containsExactly(activity)
            assertThat(database.activityDao().observeAll().first()).containsExactly(activity)
            assertThat(database.lapDao().getForActivity(activity.id)).containsExactly(lap)
            assertThat(database.sampleBlobDao().getForActivity(activity.id)).isEqualTo(blob)
            assertThat(database.importJobDao().findById(job.id)).isEqualTo(job)
        }
    }

    @Test
    fun `soft delete hides the activity but keeps its children`() {
        runBlocking {
            val activity = testActivity()
            database.activityDao().insert(activity)
            database.lapDao().insertAll(listOf(testLap()))
            database.sampleBlobDao().upsert(testBlob())

            assertThat(database.activityDao().softDelete(activity.id, 42L)).isEqualTo(1)
            assertThat(database.activityDao().softDelete(activity.id, 43L)).isEqualTo(0)
            assertThat(database.activityDao().findById(activity.id)).isNull()
            assertThat(database.activityDao().findByIdIncludingDeleted(activity.id)?.deletedAt).isEqualTo(42L)
            assertThat(database.activityDao().getAll()).isEmpty()
            assertThat(database.lapDao().getForActivity(activity.id)).hasSize(1)
            assertThat(database.sampleBlobDao().getForActivity(activity.id)).isNotNull()
        }
    }

    @Test
    fun `hard delete cascades laps and samples and detaches import jobs`() {
        runBlocking {
            val activity = testActivity()
            val job = testJob()
            database.activityDao().insert(activity)
            database.lapDao().insertAll(listOf(testLap()))
            database.sampleBlobDao().upsert(testBlob())
            database.importJobDao().insert(job)

            assertThat(database.activityDao().hardDelete(activity.id)).isEqualTo(1)
            assertThat(database.activityDao().findByIdIncludingDeleted(activity.id)).isNull()
            assertThat(database.lapDao().getForActivity(activity.id)).isEmpty()
            assertThat(database.sampleBlobDao().getForActivity(activity.id)).isNull()
            assertThat(database.importJobDao().findById(job.id)?.activityId).isNull()
        }
    }

    @Test
    fun `source file hash is unique when present`() {
        runBlocking {
            database.activityDao().insert(testActivity(id = "first", sourceFileSha256 = "hash"))

            val failure =
                runCatching {
                    database.activityDao().insert(testActivity(id = "second", sourceFileSha256 = "hash"))
                }.exceptionOrNull()

            assertThat(failure).isInstanceOf(android.database.sqlite.SQLiteConstraintException::class.java)
        }
    }

    @Test
    fun `import job status is persisted as text`() {
        runBlocking {
            val job = testJob(activityId = null).copy(status = ImportJobStatus.FAILED)
            database.importJobDao().insert(job)

            assertThat(database.importJobDao().findById(job.id)?.status).isEqualTo(ImportJobStatus.FAILED)
        }
    }
}
