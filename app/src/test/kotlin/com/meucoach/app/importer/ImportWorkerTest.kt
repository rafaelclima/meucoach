package com.meucoach.app.importer

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.ImportJobStatus
import com.meucoach.core.data.importer.ImportResult
import com.meucoach.core.data.importer.ImportService
import com.meucoach.core.data.repository.ActivityRepository
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/**
 * A stalled import is the worst failure mode for a local-first app: the file
 * sits in the list as "Lendo" forever and WorkManager keeps retrying a
 * permanent error. These tests pin the terminal outcome of each failure class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportWorkerTest {
    private lateinit var context: Context
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: ActivityRepository
    private lateinit var staging: ImportStaging
    private lateinit var processor: StagedImportProcessor
    private lateinit var notifier: ImportNotifier

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Shadows.shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        File(context.filesDir, ImportStaging.RELATIVE_DIR).deleteRecursively()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = ActivityRepository(database)
        staging = ImportStaging(context)
        processor = StagedImportProcessor(staging, ImportService(database, repository))
        notifier = ImportNotifier(context)
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, ImportStaging.RELATIVE_DIR).deleteRecursively()
    }

    @Test
    fun `fails instead of retrying when the staged file disappeared`() {
        runBlocking {
            val result = worker("imports/sumiu.gpx").doWork()

            assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
            assertThat(database.importJobDao().getAll()).isEmpty()
        }
    }

    @Test
    fun `fails instead of retrying a path outside the staging directory`() {
        runBlocking {
            val result = worker("../fora.gpx").doWork()

            assertThat(result).isInstanceOf(ListenableWorker.Result.Failure::class.java)
            assertThat(database.importJobDao().getAll()).isEmpty()
        }
    }

    @Test
    fun `tells the user why the import gave up`() {
        runBlocking {
            worker("imports/sumiu.gpx").doWork()

            val manager = context.getSystemService(NotificationManager::class.java)
            val notification = Shadows.shadowOf(manager).allNotifications.single()
            val shadow = Shadows.shadowOf(notification)
            assertThat(shadow.contentTitle.toString()).isEqualTo("Não foi possível importar")
            assertThat(shadow.contentText.toString()).contains("Importe o arquivo de novo")
        }
    }

    @Test
    fun `imports a staged file and records a done job`() {
        runBlocking {
            val source = File(context.cacheDir, "corrida.gpx").apply {
                parentFile?.mkdirs()
                writeBytes(gpx())
            }
            val staged = staging.stage(Uri.fromFile(source))

            val result = worker(staged.relativePath).doWork()

            assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
            assertThat(database.importJobDao().getAll().single().status).isEqualTo(ImportJobStatus.DONE)
            assertThat(File(context.filesDir, staged.relativePath)).doesNotExist()
        }
    }

    private fun worker(relativePath: String, attempts: Int = 0): ImportWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = if (workerClassName == ImportWorker::class.java.name) {
                ImportWorker(appContext, workerParameters, processor, notifier)
            } else {
                null
            }
        }
        val data = Data.Builder()
            .putString(ImportWorker.KEY_RELATIVE_PATH, relativePath)
            .putString(ImportWorker.KEY_DISPLAY_NAME, "corrida.gpx")
            .build()
        return TestListenableWorkerBuilder<ImportWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(data)
            .setRunAttemptCount(attempts)
            .build()
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
}
