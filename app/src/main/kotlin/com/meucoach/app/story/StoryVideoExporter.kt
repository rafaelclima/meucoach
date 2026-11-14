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
import android.view.Surface
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.meucoach.core.designsystem.R
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryRecipe
import com.meucoach.core.domain.story.StorySegmentType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.math.roundToInt

data class StoryExportResult(
    val uri: Uri,
    val savedToGallery: Boolean,
    val coverUri: Uri? = null,
    val posterUri: Uri? = null,
)

/**
 * Publication settings shared by the story encoder. Instagram re-encodes
 * everything; a 1080p30 master at 16 Mbps with declared BT.709/SDR survives
 * that pass visibly better than the old 10 Mbps file with implicit color.
 */
internal object StoryEncoderSettings {
    const val BITRATE = 16_000_000
}

/**
 * Applies the publication contract to the encoder format.
 *
 * Profile/level and VBR are only declared when the device encoder reports
 * support; otherwise the encoder keeps its defaults and the export still
 * runs. Color keys are always present because they describe the Canvas input.
 */
internal fun MediaFormat.applyStoryEncoderSettings(supportsHighProfile41: Boolean, supportsVbr: Boolean) {
    setInteger(MediaFormat.KEY_BIT_RATE, StoryEncoderSettings.BITRATE)
    setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
    setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
    setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
    if (supportsHighProfile41) {
        setInteger(
            MediaFormat.KEY_PROFILE,
            MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
        )
        setInteger(
            MediaFormat.KEY_LEVEL,
            MediaCodecInfo.CodecProfileLevel.AVCLevel41,
        )
    }
    if (supportsVbr) {
        setInteger(
            MediaFormat.KEY_BITRATE_MODE,
            MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR,
        )
    }
}

/**
 * Contract for rendering the story MP4 out of a [StoryPlan]. An interface so
 * the background export (ADR 0018) can be tested without the codec pipeline.
 */
interface StoryVideoExporter {
    /**
     * Renders every frame and encodes it. [isCancelled] is polled before each
     * frame, so a cancelled export never starts another encode round. The
     * returned file is owned by the exporter and must not be deleted by callers.
     */
    suspend fun export(
        plan: StoryPlan,
        palette: StoryPalette,
        recipe: StoryRecipe = StoryRecipe(),
        mapBackground: StoryMapSnapshot? = null,
        overviewMap: StoryMapSnapshot? = null,
        isCancelled: () -> Boolean = { false },
        onProgress: suspend (progress: Float) -> Unit = {},
    ): StoryExportResult

    companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1920

        /**
         * The map is rendered ~2.25× larger than the video and drawn down into
         * the frame, so the overview and the 1.65× flyover stay sharp. Raising
         * it further costs ~10 MB of ARGB per step; validate on a low-RAM
         * device before touching this again.
         */
        const val MAP_SUPERSAMPLE = 2.25f

        fun mapWidth(): Int = (WIDTH * MAP_SUPERSAMPLE).toInt()
        fun mapHeight(): Int = (HEIGHT * MAP_SUPERSAMPLE).toInt()
    }
}

/**
 * Immutable inputs shared by the video, cover and poster passes of one export.
 */
private data class StoryRenderJob(val plan: StoryPlan, val palette: StoryPalette, val recipe: StoryRecipe)

/** Follow and overview snapshots, already carrying native route pixels. */
private data class StoryMapSnapshots(val follow: StoryMapSnapshot?, val overview: StoryMapSnapshot?)

/**
 * Canvas + MediaCodec MVP for the story video. The encoder uses Image input
 * (YUV420 flexible), so it does not need an EGL surface or a new dependency.
 * Devices whose H.264 encoder lacks that color format return a clear error.
 */
class CanvasStoryVideoExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profiles: com.meucoach.core.data.repository.ProfileRepository,
    private val photos: com.meucoach.app.profile.ProfilePhotoStore,
) : StoryVideoExporter {
    private val typeface: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.inter_variable) ?: Typeface.DEFAULT
    }

    private val defaultLogoBitmap: Bitmap? by lazy { StoryBranding.logo(context) }

    override suspend fun export(
        plan: StoryPlan,
        palette: StoryPalette,
        recipe: StoryRecipe,
        mapBackground: StoryMapSnapshot?,
        overviewMap: StoryMapSnapshot?,
        isCancelled: () -> Boolean,
        onProgress: suspend (progress: Float) -> Unit,
    ): StoryExportResult {
        // EGL contexts are bound to the thread that made them current. Progress
        // callbacks suspend the coroutine, so a shared pool could resume the
        // loop on another thread and break the encoder Surface (EGL_BAD_ACCESS).
        val encoderThread = Executors
            .newSingleThreadExecutor { runnable -> Thread(runnable, "story-encoder") }
            .asCoroutineDispatcher()
        return try {
            withContext(encoderThread) {
                exportOnEncoderThread(
                    StoryRenderJob(plan, palette, recipe),
                    StoryMapSnapshots(mapBackground, overviewMap),
                    isCancelled,
                    onProgress,
                )
            }
        } finally {
            encoderThread.close()
        }
    }

    private suspend fun exportOnEncoderThread(
        renderJob: StoryRenderJob,
        maps: StoryMapSnapshots,
        isCancelled: () -> Boolean,
        onProgress: suspend (progress: Float) -> Unit,
    ): StoryExportResult {
        // Cancellation must be polled synchronously: the encode loop never
        // suspends, so simply cancelling this coroutine would not stop it.
        val coroutineJob = requireNotNull(currentCoroutineContext()[Job]) { "Export precisa de um Job ativo." }
        val cancelled = { !coroutineJob.isActive }
        val directory = File(context.cacheDir, "stories").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val output = File(directory, StoryFileNames.video(renderJob.plan, stamp))
        val avatar = runCatching {
            profiles.getProfile()?.avatarPath?.let { photos.load(it, maxSize = 256) }
        }.getOrNull()
        return try {
            encode(renderJob, maps, output, cancelled, onProgress, avatar)
            val cover = runCatching {
                writeCover(renderJob, maps, directory, stamp, avatar)
            }.getOrNull()
            // The poster is an extra: a failure (or a plan without a route)
            // still leaves a completed video and cover.
            val poster = runCatching {
                writePoster(renderJob, directory, stamp)
            }.getOrNull()
            publish(output, cover, poster)
        } catch (error: Throwable) {
            // A partial MP4 is unreadable: a cancelled or failed export leaves
            // nothing in the cache for the user to stumble on.
            runCatching { output.delete() }
            throw error
        }
    }

    /**
     * Still cover for the feed and for the story thumbnail. Rendering the final
     * frame with the same renderer guarantees the cover matches the video.
     */
    private fun writeCover(
        renderJob: StoryRenderJob,
        maps: StoryMapSnapshots,
        directory: File,
        stamp: Long,
        avatar: Bitmap?,
    ): File {
        val file = File(directory, StoryFileNames.cover(renderJob.plan, stamp))
        val bitmap = Bitmap.createBitmap(StoryVideoExporter.WIDTH, StoryVideoExporter.HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            val finalStart = renderJob.plan.segments.lastOrNull { it.type == StorySegmentType.FINAL }?.startSec
                ?: (renderJob.plan.durationSec - 1f).coerceAtLeast(0f)
            val timeSec = finalStart + 1f
            val canvas = Canvas(bitmap)
            StoryFrameRenderer(
                renderJob.palette,
                typeface,
                defaultLogoBitmap,
                maps.follow,
                avatar,
                maps.overview,
                renderJob.recipe,
            ).render(canvas, renderJob.plan, timeSec, StoryVideoExporter.WIDTH, StoryVideoExporter.HEIGHT)
            StoryFilm.drawGrain(canvas, StoryVideoExporter.WIDTH, StoryVideoExporter.HEIGHT, timeSec)
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Não foi possível salvar a capa do story."
                }
            }
        } finally {
            bitmap.recycle()
        }
        return file
    }

    /**
     * 4:5 poster for the feed (VIS-05). Independent from the codec, so a
     * poster failure never fails the export; [writePoster] returns null when
     * the renderer has no route or the PNG cannot be written.
     */
    private fun writePoster(renderJob: StoryRenderJob, directory: File, stamp: Long): File? {
        val file = File(directory, StoryFileNames.poster(renderJob.plan, stamp))
        return try {
            val written = file.outputStream().use { output ->
                StoryPosterRenderer(renderJob.palette, typeface, defaultLogoBitmap)
                    .writePng(renderJob.plan, renderJob.recipe, output)
            }
            if (written) {
                file
            } else {
                runCatching { file.delete() }
                null
            }
        } catch (error: Throwable) {
            runCatching { file.delete() }
            throw error
        }
    }

    private suspend fun encode(
        renderJob: StoryRenderJob,
        maps: StoryMapSnapshots,
        output: File,
        isCancelled: () -> Boolean,
        onProgress: suspend (progress: Float) -> Unit,
        avatar: Bitmap?,
    ) {
        val codecInfo = selectEncoder()

        val codec = MediaCodec.createByCodecName(codecInfo.name)
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var trackIndex = -1
        val info = MediaCodec.BufferInfo()
        val bitmap = Bitmap.createBitmap(StoryVideoExporter.WIDTH, StoryVideoExporter.HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val renderer = StoryFrameRenderer(
            renderJob.palette,
            typeface,
            defaultLogoBitmap,
            maps.follow,
            avatar,
            maps.overview,
            renderJob.recipe,
        )
        val yuvSize = StoryVideoExporter.WIDTH * StoryVideoExporter.HEIGHT * 3 / 2
        val frameCount = (renderJob.plan.durationSec * FPS).roundToInt().coerceAtLeast(1)
        val useSurface = supportsSurface(codecInfo)
        // Scratch planes are only needed by the fallback Image path.
        var pixels: IntArray? = null
        var luma: ByteArray? = null
        var chroma: ByteArray? = null
        var inputSurface: Surface? = null
        var glEncoder: StorySurfaceEncoder? = null

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
            val format = MediaFormat.createVideoFormat(
                MIME,
                StoryVideoExporter.WIDTH,
                StoryVideoExporter.HEIGHT,
            ).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    if (useSurface) {
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    } else {
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                    },
                )
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                applyStoryEncoderSettings(
                    supportsHighProfile41 = supportsHighProfile41(codecInfo),
                    supportsVbr = supportsVbr(codecInfo),
                )
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            if (useSurface) {
                inputSurface = codec.createInputSurface()
                // Grafika order: EGL surface first, then start the codec, so the
                // producer is attached before the first buffer arrives.
                glEncoder = StorySurfaceEncoder(inputSurface)
            }
            codec.start()

            for (frame in 0 until frameCount) {
                if (isCancelled()) {
                    throw CancellationException("Geração do story cancelada.")
                }
                val timeSec = frame.toFloat() / FPS
                renderer.render(canvas, renderJob.plan, timeSec, StoryVideoExporter.WIDTH, StoryVideoExporter.HEIGHT)
                StoryFilm.drawGrain(canvas, StoryVideoExporter.WIDTH, StoryVideoExporter.HEIGHT, timeSec)
                val encoder = glEncoder
                if (encoder != null) {
                    encoder.draw(bitmap, frame * 1_000_000_000L / FPS)
                } else {
                    val inputIndex = codec.dequeueInputBuffer(10_000L)
                    check(inputIndex >= 0) { "Encoder sem buffer de entrada." }
                    val image = codec.getInputImage(inputIndex)
                        ?: error("Encoder sem entrada de imagem YUV.")
                    val pixelBuffer = pixels ?: IntArray(StoryVideoExporter.WIDTH * StoryVideoExporter.HEIGHT)
                        .also { pixels = it }
                    val lumaBuffer = luma ?: ByteArray(StoryVideoExporter.WIDTH * StoryVideoExporter.HEIGHT)
                        .also { luma = it }
                    val chromaBuffer = chroma ?: ByteArray(StoryVideoExporter.WIDTH * StoryVideoExporter.HEIGHT / 2)
                        .also { chroma = it }
                    fillImage(bitmap, image, pixelBuffer, lumaBuffer, chromaBuffer)
                    codec.queueInputBuffer(
                        inputIndex,
                        0,
                        yuvSize,
                        frame * 1_000_000L / FPS,
                        0,
                    )
                }
                drain(endOfStream = false)
                if (frame % PROGRESS_EVERY_FRAMES == 0) onProgress(frame.toFloat() / frameCount)
            }

            if (isCancelled()) {
                throw CancellationException("Geração do story cancelada.")
            }
            if (glEncoder != null) {
                codec.signalEndOfInputStream()
            } else {
                val inputIndex = codec.dequeueInputBuffer(10_000L)
                check(inputIndex >= 0) { "Encoder sem buffer para finalizar." }
                codec.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    frameCount * 1_000_000L / FPS,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
            }
            drain(endOfStream = true)
            onProgress(1f)
        } finally {
            glEncoder?.release()
            runCatching { codec.stop() }
            codec.release()
            inputSurface?.release()
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    /**
     * Prefers the Surface path: the GPU performs RGB→YUV and we skip the
     * ~110 ms/frame CPU conversion. Falls back to the Image path when the
     * device encoder has no input surface.
     */
    private fun selectEncoder(): MediaCodecInfo {
        val encoders = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(MIME, ignoreCase = true) }
        }
        return encoders.firstOrNull { supportsSurface(it) }
            ?: encoders.firstOrNull { supportsFlexibleYuv(it) }
            ?: error("Nenhum encoder H.264 compatível neste aparelho.")
    }

    private fun supportsSurface(info: MediaCodecInfo): Boolean =
        info.colorFormats().contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)

    private fun supportsFlexibleYuv(info: MediaCodecInfo): Boolean =
        info.colorFormats().contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)

    /** Some encoders list High at 4.0/4.2 but not 4.1; only declare an exact match. */
    private fun supportsHighProfile41(info: MediaCodecInfo): Boolean = runCatching {
        info.getCapabilitiesForType(MIME).profileLevels.any {
            it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh &&
                it.level == MediaCodecInfo.CodecProfileLevel.AVCLevel41
        }
    }.getOrDefault(false)

    private fun supportsVbr(info: MediaCodecInfo): Boolean = runCatching {
        info.getCapabilitiesForType(MIME).encoderCapabilities
            ?.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR) == true
    }.getOrDefault(false)

    private fun MediaCodecInfo.colorFormats(): IntArray = getCapabilitiesForType(MIME).colorFormats

    /**
     * Converts the rendered ARGB frame into the encoder's YUV420 planes.
     *
     * The planes are filled into reusable [luma]/[chroma] byte arrays and then
     * copied in bulk rows; writing one byte at a time through the direct
     * ByteBuffer crosses into native code ~3M times per frame. Layouts the
     * fast path cannot describe fall back to the original per-pixel write.
     */
    private fun fillImage(bitmap: Bitmap, image: Image, pixels: IntArray, luma: ByteArray, chroma: ByteArray) {
        bitmap.getPixels(pixels, 0, StoryVideoExporter.WIDTH, 0, 0, StoryVideoExporter.WIDTH, StoryVideoExporter.HEIGHT)
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes.getOrNull(2)
        if (yPlane.pixelStride != 1) {
            fillPerPixel(pixels, yPlane, uPlane, vPlane)
            return
        }

        for (y in 0 until StoryVideoExporter.HEIGHT) {
            val row = y * StoryVideoExporter.WIDTH
            for (x in 0 until StoryVideoExporter.WIDTH) {
                val color = pixels[row + x]
                val red = (color shr 16) and 0xff
                val green = (color shr 8) and 0xff
                val blue = color and 0xff
                luma[row + x] = (((66 * red + 129 * green + 25 * blue + 128) shr 8) + 16).toByte()
            }
        }
        writePlaneRows(yPlane, luma, StoryVideoExporter.WIDTH, StoryVideoExporter.HEIGHT)

        if (vPlane == null) {
            // Semi-planar: U and V interleaved, two bytes per sample (pixelStride 2).
            if (uPlane.pixelStride != 2) {
                fillPerPixel(pixels, yPlane, uPlane, vPlane)
                return
            }
            for (y in 0 until StoryVideoExporter.HEIGHT step 2) {
                val uvRow = (y / 2) * StoryVideoExporter.WIDTH
                val pixelRow = y * StoryVideoExporter.WIDTH
                for (x in 0 until StoryVideoExporter.WIDTH step 2) {
                    val color = pixels[pixelRow + x]
                    val red = (color shr 16) and 0xff
                    val green = (color shr 8) and 0xff
                    val blue = color and 0xff
                    chroma[uvRow + x] = (((-38 * red - 74 * green + 112 * blue + 128) shr 8) + 128).toByte()
                    chroma[uvRow + x + 1] = (((112 * red - 94 * green - 18 * blue + 128) shr 8) + 128).toByte()
                }
            }
            writePlaneRows(uPlane, chroma, StoryVideoExporter.WIDTH, StoryVideoExporter.HEIGHT / 2)
            return
        }

        if (uPlane.pixelStride != 1 || vPlane.pixelStride != 1) {
            fillPerPixel(pixels, yPlane, uPlane, vPlane)
            return
        }
        val halfWidth = StoryVideoExporter.WIDTH / 2
        val vOffset = StoryVideoExporter.WIDTH * StoryVideoExporter.HEIGHT / 4
        for (y in 0 until StoryVideoExporter.HEIGHT step 2) {
            val row = (y / 2) * halfWidth
            val pixelRow = y * StoryVideoExporter.WIDTH
            for (x in 0 until StoryVideoExporter.WIDTH step 2) {
                val color = pixels[pixelRow + x]
                val red = (color shr 16) and 0xff
                val green = (color shr 8) and 0xff
                val blue = color and 0xff
                chroma[row + x / 2] = (((-38 * red - 74 * green + 112 * blue + 128) shr 8) + 128).toByte()
                chroma[vOffset + row + x / 2] = (((112 * red - 94 * green - 18 * blue + 128) shr 8) + 128).toByte()
            }
        }
        writePlaneRows(uPlane, chroma, halfWidth, StoryVideoExporter.HEIGHT / 2)
        writePlaneRows(vPlane, chroma, halfWidth, StoryVideoExporter.HEIGHT / 2, start = vOffset)
    }

    /** Bulk copy into a plane, honouring any row padding the encoder reports. */
    private fun writePlaneRows(plane: Image.Plane, bytes: ByteArray, rowLength: Int, rows: Int, start: Int = 0) {
        val buffer = plane.buffer
        if (plane.rowStride == rowLength) {
            buffer.position(0)
            buffer.put(bytes, start, rowLength * rows)
            return
        }
        for (row in 0 until rows) {
            buffer.position(row * plane.rowStride)
            buffer.put(bytes, start + row * rowLength, rowLength)
        }
    }

    /** Encoder layouts outside the common YUV420 ones: slow but always correct. */
    private fun fillPerPixel(pixels: IntArray, yPlane: Image.Plane, uPlane: Image.Plane, vPlane: Image.Plane?) {
        for (y in 0 until StoryVideoExporter.HEIGHT) {
            for (x in 0 until StoryVideoExporter.WIDTH) {
                val color = pixels[y * StoryVideoExporter.WIDTH + x]
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

    private fun publish(file: File, cover: File?, poster: File?): StoryExportResult {
        val coverUri = cover?.let { image -> runCatching { publishImage(image) }.getOrNull() }
        val posterUri = poster?.let { image -> runCatching { publishImage(image) }.getOrNull() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return StoryExportResult(
                uri = publishVideo(file),
                savedToGallery = true,
                coverUri = coverUri,
                posterUri = posterUri,
            )
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return StoryExportResult(
            uri = uri,
            savedToGallery = false,
            coverUri = coverUri,
            posterUri = posterUri,
        )
    }

    private fun publishVideo(file: File): Uri {
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
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    /** Cover and poster share the Pictures/MeuCoach contract. */
    private fun publishImage(file: File): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/MeuCoach",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Não foi possível reservar a imagem na galeria.")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Não foi possível gravar a imagem na galeria.")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    companion object {
        private const val MIME = "video/avc"
        private const val FPS = 30

        /** Progress callback stays cheap for the encoder loop; callers throttle further. */
        private const val PROGRESS_EVERY_FRAMES = 12
    }
}
