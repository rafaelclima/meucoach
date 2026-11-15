package com.meucoach.app.story

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.meucoach.core.domain.story.BoundingBox
import com.meucoach.core.domain.story.StoryGeoPoint
import com.meucoach.core.domain.story.StoryMapStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Provides the static map bitmap that sits behind the story route (ADR 0015).
 * Suspending and injectable so previews/tests can run without network or the
 * MapLibre native library.
 */
interface MapBackgroundRenderer {
    /**
     * Low-RAM devices (`ActivityManager.isLowRamDevice`) render the story at
     * 1,25× with two keyframes (MAP-07). The default is the full budget; only
     * the real MapLibre renderer overrides this from the device.
     */
    val lowRam: Boolean get() = false

    suspend fun render(
        boundingBox: BoundingBox,
        route: List<StoryGeoPoint>,
        width: Int,
        height: Int,
        style: StoryMapStyle = StoryMapStyle.MEUCOACH_DARK,
        pitchDegrees: Float = StoryMapScript.FOLLOW_PITCH_DEGREES,
        bearingDegrees: Float = 0f,
    ): StoryMapSnapshot
}

/**
 * MapLibre snapshotter backed by OpenFreeMap vector tiles (OSM data, ADR 0015).
 * Vector labels stay sharp at 1080×1920; the snapshot is generated once per
 * story, before the video is encoded, and reused for every frame. If the style
 * download fails, it falls back to the inline OSM raster style.
 */
class MapLibreBackgroundRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tileSource: StoryTileSource,
) : MapBackgroundRenderer {
    private val cachedStyles = java.util.concurrent.ConcurrentHashMap<StoryMapStyle, String>()

    override val lowRam: Boolean by lazy {
        context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
    }

    override suspend fun render(
        boundingBox: BoundingBox,
        route: List<StoryGeoPoint>,
        width: Int,
        height: Int,
        style: StoryMapStyle,
        pitchDegrees: Float,
        bearingDegrees: Float,
    ): StoryMapSnapshot {
        if (!hasNetwork()) {
            throw IllegalStateException("Sem conexão para baixar o mapa.")
        }
        // Backstop for the low-RAM budget (MAP-07): callers already request
        // 1,25× dims on constrained devices, but a full-size request is clamped
        // here so the snapshot never exceeds the budget transiently either.
        val pixels = width.toLong() * height.toLong()
        val (renderWidth, renderHeight) = if (lowRam && pixels > LOW_RAM_MAX_PIXELS) {
            val scale = kotlin.math.sqrt(LOW_RAM_MAX_PIXELS.toDouble() / pixels).toFloat()
            maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
        } else {
            width to height
        }
        val json = styleJson(style)
        return withContext(Dispatchers.Main.immediate) {
            MapLibre.getInstance(context)
            suspendCancellableCoroutine { continuation ->
                val camera = StoryMapCameraCalculator.calculateForStory(
                    boundingBox,
                    renderWidth,
                    renderHeight,
                    pitchDegrees = pitchDegrees,
                    bearingDegrees = bearingDegrees,
                )
                val options = MapSnapshotter
                    .Options(renderWidth, renderHeight)
                    .withStyleBuilder(Style.Builder().fromJson(json))
                    .withCameraPosition(
                        CameraPosition.Builder()
                            .target(LatLng(camera.latitude, camera.longitude))
                            .zoom(camera.zoom)
                            .tilt(camera.pitchDegrees.toDouble())
                            .bearing(camera.bearingDegrees.toDouble())
                            .build(),
                    )
                    .withLogo(false)
                    .withAttribution(false)
                val snapshotter = MapSnapshotter(context, options)
                continuation.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post {
                        runCatching { snapshotter.cancel() }
                    }
                }
                snapshotter.start(
                    { snapshot ->
                        val projected = runCatching {
                            val bitmap = checkNotNull(snapshot.bitmap) {
                                "O mapa não retornou um bitmap."
                            }
                            val pixels = route.map { point ->
                                checkNotNull(
                                    snapshot.pixelForLatLng(LatLng(point.latitude, point.longitude)),
                                ) { "O mapa não projetou um ponto da rota." }
                            }
                            StoryMapSnapshot(bitmap = bitmap, routePixels = pixels)
                        }
                        if (!continuation.isActive) return@start
                        projected.fold(
                            { snapshotWithRoute -> continuation.resume(snapshotWithRoute) },
                            { error ->
                                continuation.resumeWithException(
                                    IllegalStateException("Falha ao projetar a rota no mapa: $error", error),
                                )
                            },
                        )
                    },
                    { error ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Falha ao baixar o mapa: $error"),
                            )
                        }
                    },
                )
            }
        }
    }

    private fun cachedStyle(style: StoryMapStyle): String? {
        cachedStyles[style]?.let { return it }
        val disk = StoryMapDiskCache.readStyle(context.cacheDir, style) ?: return null
        cachedStyles[style] = disk
        return disk
    }

    private suspend fun styleJson(style: StoryMapStyle): String {
        cachedStyle(style)?.let { return it }
        val json = runCatching {
            withContext(Dispatchers.IO) {
                val connection = (URL(tileSource.styleUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "MeuCoach/0.1 (Android)")
                    instanceFollowRedirects = true
                }
                val json = connection.getInputStream().bufferedReader().use { it.readText() }
                tileSource.customize(json, style)
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            if (style == StoryMapStyle.TERRAIN) {
                // No relief overlay without the style download; keep the story usable.
                return styleJson(StoryMapStyle.CLASSIC)
            }
            return OSM_RASTER_STYLE
        }
        cachedStyles[style] = json
        if (json != OSM_RASTER_STYLE) {
            StoryMapDiskCache.writeStyle(context.cacheDir, style, json)
        }
        return json
    }

    private fun hasNetwork(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        /** 1,25× follow frame: the low-RAM ceiling for any story snapshot (MAP-07). */
        const val LOW_RAM_MAX_PIXELS = 1_350 * 2_400

        // Fallback when OpenFreeMap is unreachable: inline OSM raster tiles, no key.
        val OSM_RASTER_STYLE = """
            {
              "version": 8,
              "sources": {
                "osm": {
                  "type": "raster",
                  "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                  "tileSize": 256,
                  "maxzoom": 19,
                  "attribution": "© OpenStreetMap contributors"
                }
              },
              "layers": [
                { "id": "osm", "type": "raster", "source": "osm" }
              ]
            }
        """.trimIndent()
    }
}
