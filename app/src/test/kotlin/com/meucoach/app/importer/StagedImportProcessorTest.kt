package com.meucoach.app.importer

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.ImportJobStatus
import com.meucoach.core.data.epoch.DataEpochStore
import com.meucoach.core.data.importer.ImportResult
import com.meucoach.core.data.importer.ImportService
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.ProfileRepository
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StagedImportProcessorTest {
    private lateinit var context: Context
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: ActivityRepository
    private lateinit var staging: ImportStaging
    private lateinit var processor: StagedImportProcessor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, ImportStaging.RELATIVE_DIR).deleteRecursively()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = ActivityRepository(database)
        staging = ImportStaging(context)
        processor = StagedImportProcessor(staging, ImportService(database, repository))
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, ImportStaging.RELATIVE_DIR).deleteRecursively()
    }

    @Test
    fun `imports a staged gpx and deletes the copy`() {
        runBlocking {
            val staged = staging.stage(Uri.fromFile(sourceFile("corrida.gpx", gpx())))

            val result = processor.process(staged.relativePath, staged.displayName, staged.mime)

            assertThat(result).isInstanceOf(ImportResult.Imported::class.java)
            assertThat(repository.listActivitySummaries()).hasSize(1)
            assertThat(database.importJobDao().getAll().single().status).isEqualTo(ImportJobStatus.DONE)
            assertThat(File(context.filesDir, staged.relativePath)).doesNotExist()
        }
    }

    @Test
    fun `keeps the failed job and deletes the staged copy`() {
        runBlocking {
            val staged = staging.stage(Uri.fromFile(sourceFile("notas.txt", "não é treino".toByteArray())))

            val result = processor.process(staged.relativePath, staged.displayName, staged.mime)

            assertThat(result).isInstanceOf(ImportResult.Failed::class.java)
            assertThat(database.importJobDao().getAll().single().status).isEqualTo(ImportJobStatus.FAILED)
            assertThat(File(context.filesDir, staged.relativePath)).doesNotExist()
        }
    }

    @Test
    fun `skips and cleans staging when there is no profile`() {
        runBlocking {
            val clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC)
            val guarded = StagedImportProcessor(
                staging = staging,
                importService = ImportService(database, repository),
                profiles = ProfileRepository(database, clock),
                epochStore = DataEpochStore(context, clock, prefsName = "import-epoch-${System.nanoTime()}"),
            )
            val staged = staging.stage(Uri.fromFile(sourceFile("corrida.gpx", gpx())))

            val result = guarded.process(staged.relativePath, staged.displayName, staged.mime)

            assertThat(result).isEqualTo(ImportResult.Skipped)
            assertThat(File(context.filesDir, staged.relativePath)).doesNotExist()
            assertThat(repository.listActivitySummaries()).isEmpty()
            assertThat(database.importJobDao().getAll()).isEmpty()
        }
    }

    @Test
    fun `throws when the staged file disappeared`() {
        assertThatThrownBy {
            runBlocking { processor.process("imports/sumiu.fit", "sumiu.fit", null) }
        }.isInstanceOf(IOException::class.java)
    }

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
}
