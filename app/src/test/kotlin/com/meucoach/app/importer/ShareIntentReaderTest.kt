package com.meucoach.app.importer

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShareIntentReaderTest {
    private val first = Uri.parse("content://downloads/run.fit")
    private val second = Uri.parse("content://downloads/run.tcx")

    @Test
    fun `reads a single shared stream`() {
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, first)

        assertThat(ShareIntentReader.uris(intent)).containsExactly(first)
    }

    @Test
    fun `reads multiple shared streams`() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))

        assertThat(ShareIntentReader.uris(intent)).containsExactly(first, second)
    }

    @Test
    fun `reads clip data when the stream extra is missing`() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.clipData = ClipData.newRawUri("corrida", first)

        assertThat(ShareIntentReader.uris(intent)).containsExactly(first)
    }

    @Test
    fun `removes duplicates and ignores other actions`() {
        val duplicate = Intent(Intent.ACTION_SEND_MULTIPLE)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, first))

        assertThat(ShareIntentReader.uris(duplicate)).containsExactly(first)
        assertThat(ShareIntentReader.uris(Intent(Intent.ACTION_VIEW))).isEmpty()
        assertThat(ShareIntentReader.uris(null)).isEmpty()
    }
}
