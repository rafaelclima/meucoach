package com.meucoach.app.importer

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.work.OneTimeWorkRequest
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportCoordinatorTest {
    private lateinit var context: Context
    private lateinit var staging: ImportStaging
    private lateinit var requests: MutableList<OneTimeWorkRequest>
    private lateinit var coordinator: ImportCoordinator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, ImportStaging.RELATIVE_DIR).deleteRecursively()
        staging = ImportStaging(context)
        requests = mutableListOf()
        coordinator = ImportCoordinator(staging) { request -> requests += request }
    }

    @After
    fun tearDown() {
        File(context.filesDir, ImportStaging.RELATIVE_DIR).deleteRecursively()
    }

    @Test
    fun `stages each file and enqueues one request per file`() {
        runBlocking {
            val first = sourceFile("corrida.fit", byteArrayOf(1))
            val second = sourceFile("corrida.tcx", byteArrayOf(2))

            val result = coordinator.enqueue(listOf(Uri.fromFile(first), Uri.fromFile(second)))

            assertThat(result.queued).isEqualTo(2)
            assertThat(result.failures).isEmpty()
            assertThat(requests).hasSize(2)
            assertThat(requests.map { it.tags }).allSatisfy { tags ->
                assertThat(tags).contains(ImportWorker.TAG)
            }
            val inputs = requests.map { it.workSpec.input }
            assertThat(inputs.map { it.getString(ImportWorker.KEY_DISPLAY_NAME) })
                .containsExactlyInAnyOrder("corrida.fit", "corrida.tcx")
            assertThat(inputs.map { it.getString(ImportWorker.KEY_RELATIVE_PATH) })
                .allSatisfy { path ->
                    assertThat(path).startsWith("${ImportStaging.RELATIVE_DIR}/")
                    assertThat(File(context.filesDir, path!!)).exists()
                }
        }
    }

    @Test
    fun `ignores the same uri selected twice`() {
        runBlocking {
            val uri = Uri.fromFile(sourceFile("corrida.fit", byteArrayOf(1)))

            val result = coordinator.enqueue(listOf(uri, uri))

            assertThat(result.queued).isEqualTo(1)
            assertThat(requests).hasSize(1)
        }
    }

    @Test
    fun `reports files that cannot be opened and keeps the valid ones`() {
        runBlocking {
            val valid = sourceFile("corrida.fit", byteArrayOf(1))
            val missing = File(context.cacheDir, "sumiu.fit")

            val result = coordinator.enqueue(listOf(Uri.fromFile(missing), Uri.fromFile(valid)))

            assertThat(result.queued).isEqualTo(1)
            assertThat(result.failures).hasSize(1)
            val failure = result.failures.single()
            assertThat(failure.fileName).isEqualTo("sumiu.fit")
            assertThat(failure.message).isNotBlank()
            assertThat(requests).hasSize(1)
        }
    }

    private fun sourceFile(name: String, bytes: ByteArray): File = File(context.cacheDir, name).apply {
        parentFile?.mkdirs()
        writeBytes(bytes)
    }
}
