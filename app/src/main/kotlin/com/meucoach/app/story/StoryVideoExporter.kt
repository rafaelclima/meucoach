package com.meucoach.app.story

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.meucoach.core.designsystem.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

data class StoryExportResult(val uri: Uri, val savedToGallery: Boolean)

/**
 * Canvas + MediaCodec MVP for the story video. The encoder uses Image input
 * (YUV420 flexible), so it does not need an EGL surface or a new dependency.
 * Devices whose H.264 encoder lacks that color format return a clear error.
 */
class StoryVideoExporter @Inject constructor(@ApplicationContext private val context: Context) {
    private val typeface: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.inter_variable) ?: Typeface.DEFAULT
    }

    suspend fun export(plan: StoryPlan, palette: StoryPalette, onProgress: (Float) -> Unit = {}): StoryExportResult =
        withContext(Dispatchers.Default) {
            val output = File(
                File(context.cacheDir, "stories").apply { mkdirs() },
                "meucoach-story-${System.currentTimeMillis()}.mp4",
            )
            encode(plan, palette, output, onProgress)
            publish(output)
        }

    private fun encode(plan: StoryPlan, palette: StoryPalette, output: File, onProgress: (Float) -> Unit) {
        val codecInfo = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { info ->
            info.isEncoder &&
                info.supportedTypes.any { it.equals(MIME, ignoreCase = true) } &&
                info.getCapabilitiesForType(MIME)
                    .colorFormats
                    .contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        } ?: error("Nenhum encoder H.264 com YUV420 flexível neste aparelho.")

        val codec = MediaCodec.createByCodecName(codecInfo.name)
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var trackIndex = -1
        val info = MediaCodec.BufferInfo()
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val renderer = StoryFrameRenderer(palette, typeface)
        val yuvSize = WIDTH * HEIGHT * 3 / 2
        val frameCount = (plan.durationSec * FPS).roundToInt().coerceAtLeast(1)

        fun drain(endOfStream: Boolean) {
            var idle = 0
            while (true) {
                val outIndex = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000L else 0L)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream || idle++ > 50) return
                    }

                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }

                    outIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outIndex)
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (buffer != null && info.size > 0 && !isConfig) {
                            check(muxerStarted) { "Muxer não iniciou antes do primeiro frame." }
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, buffer, info)
                        }
                        val end = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outIndex, false)
                        if (end) return
                    }
                }
            }
        }

        try {
            val format = MediaFormat.createVideoFormat(MIME, WIDTH, HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            for (frame in 0 until frameCount) {
                renderer.render(canvas, plan, frame.toFloat() / FPS, WIDTH, HEIGHT)
                val inputIndex = codec.dequeueInputBuffer(10_000L)
                check(inputIndex >= 0) { "Encoder sem buffer de entrada." }
                val image = codec.getInputImage(inputIndex)
                    ?: error("Encoder sem entrada de imagem YUV.")
                fillImage(bitmap, image)
                codec.queueInputBuffer(
                    inputIndex,
                    0,
                    yuvSize,
                    frame * 1_000_000L / FPS,
                    0,
                )
                drain(endOfStream = false)
                if (frame % 12 == 0) onProgress(frame.toFloat() / frameCount)
            }

            val inputIndex = codec.dequeueInputBuffer(10_000L)
            check(inputIndex >= 0) { "Encoder sem buffer para finalizar." }
            codec.queueInputBuffer(
                inputIndex,
                0,
                0,
                frameCount * 1_000_000L / FPS,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
            )
            drain(endOfStream = true)
            onProgress(1f)
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun fillImage(bitmap: Bitmap, image: Image) {
        val pixels = IntArray(WIDTH * HEIGHT)
        bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes.getOrNull(2)
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                val color = pixels[y * WIDTH + x]
                val red = (color shr 16) and 0xff
                val green = (color shr 8) and 0xff
                val blue = color and 0xff
                val yValue = ((66 * red + 129 * green + 25 * blue + 128) shr 8) + 16
                yPlane.buffer.put(y * yPlane.rowStride + x * yPlane.pixelStride, yValue.toByte())
                if (y % 2 == 0 && x % 2 == 0) {
                    val uValue = ((-38 * red - 74 * green + 112 * blue + 128) shr 8) + 128
                    val vValue = ((112 * red - 94 * green - 18 * blue + 128) shr 8) + 128
                    val uIndex = (y / 2) * uPlane.rowStride + (x / 2) * uPlane.pixelStride
                    uPlane.buffer.put(uIndex, uValue.toByte())
                    if (vPlane == null) {
                        uPlane.buffer.put(uIndex + uPlane.pixelStride, vValue.toByte())
                    } else {
                        val vIndex = (y / 2) * vPlane.rowStride + (x / 2) * vPlane.pixelStride
                        vPlane.buffer.put(vIndex, vValue.toByte())
                    }
                }
            }
        }
    }

    private fun publish(file: File): StoryExportResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MOVIES}/MeuCoach",
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Não foi possível reservar o vídeo na galeria.")
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Não foi possível gravar o vídeo na galeria.")
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return StoryExportResult(uri = uri, savedToGallery = true)
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return StoryExportResult(uri = uri, savedToGallery = false)
    }

    private companion object {
        const val MIME = "video/avc"
        const val WIDTH = 720
        const val HEIGHT = 1280
        const val FPS = 24
        const val BITRATE = 4_000_000
    }
}
