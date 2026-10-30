package com.meucoach.app.importer

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportStagingTest {
    private lateinit var context: Context
    private lateinit var staging: ImportStaging

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, ImportStaging.RELATIVE_DIR).deleteRecursively()
        staging = ImportStaging(context)
    }

    @After
    fun tearDown() {
        File(context.filesDir, ImportStaging.RELATIVE_DIR).deleteRecursively()
    }

    @Test
    fun `copies a file into private storage and reads it back`() {
        runBlocking {
            val source = sourceFile("corrida.fit", byteArrayOf(1, 2, 3))

            val staged = staging.stage(Uri.fromFile(source))

            assertThat(staged.relativePath).startsWith("${ImportStaging.RELATIVE_DIR}/")
            assertThat(staged.displayName).isEqualTo("corrida.fit")
            assertThat(staged.mime).isNull()
            assertThat(staged.sizeBytes).isEqualTo(3)
            assertThat(staging.read(staged.relativePath)).containsExactly(1, 2, 3)
            assertThat(File(context.filesDir, staged.relativePath)).exists()
        }
    }

    @Test
    fun `deletes the staged copy`() {
        runBlocking {
            val staged = staging.stage(Uri.fromFile(sourceFile("corrida.gpx", byteArrayOf(9))))

            assertThat(staging.delete(staged.relativePath)).isTrue()
            assertThat(File(context.filesDir, staged.relativePath)).doesNotExist()
            assertThat(staging.delete(staged.relativePath)).isFalse()
        }
    }

    @Test
    fun `rejects files above the configured limit`() {
        val limited = ImportStaging(context, maxBytes = 2)

        assertThatThrownBy {
            runBlocking { limited.stage(Uri.fromFile(sourceFile("grande.fit", byteArrayOf(1, 2, 3)))) }
        }
            .isInstanceOf(ImportStagingException::class.java)
            .hasMessageContaining("2 bytes")
    }

    @Test
    fun `rejects empty files`() {
        assertThatThrownBy {
            runBlocking { staging.stage(Uri.fromFile(sourceFile("vazio.fit", ByteArray(0)))) }
        }
            .isInstanceOf(ImportStagingException::class.java)
            .hasMessageContaining("vazio")
    }

    @Test
    fun `rejects paths outside the imports directory`() {
        assertThatThrownBy {
            runBlocking { staging.read("../meucoach.db") }
        }
            .isInstanceOf(ImportStagingException::class.java)
            .hasMessageContaining("inválido")
    }

    private fun sourceFile(name: String, bytes: ByteArray): File = File(context.cacheDir, name).apply {
        parentFile?.mkdirs()
        writeBytes(bytes)
    }
}
