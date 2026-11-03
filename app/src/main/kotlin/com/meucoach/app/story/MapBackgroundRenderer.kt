package com.meucoach.app.story

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
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
fun interface MapBackgroundRenderer {
    suspend fun render(boundingBox: BoundingBox, width: Int, height: Int): Bitmap
}

/**
 * MapLibre snapshotter backed by OpenFreeMap vector tiles (OSM data, ADR 0015).
 * Vector labels stay sharp at 1080×1920; the snapshot is generated once per
 * story, before the video is encoded, and reused for every frame. If the style
 * download fails, it falls back to the inline OSM raster style.
 */
class MapLibreBackgroundRenderer @Inject constructor(@ApplicationContext private val context: Context) :
    MapBackgroundRenderer {
    @Volatile
    private var cachedStyle: String? = null

    override suspend fun render(boundingBox: BoundingBox, width: Int, height: Int): Bitmap {
        if (!hasNetwork()) {
            throw IllegalStateException("Sem conexão para baixar o mapa.")
        }
        val style = styleJson()
        return withContext(Dispatchers.Main.immediate) {
            MapLibre.getInstance(context)
            suspendCancellableCoroutine { continuation ->
                val camera = StoryMapCameraCalculator.calculate(boundingBox, width, height)
                val options = MapSnapshotter
                    .Options(width, height)
                    .withStyleBuilder(Style.Builder().fromJson(style))
                    .withCameraPosition(
                        CameraPosition.Builder()
                            .target(LatLng(camera.latitude, camera.longitude))
                            .zoom(camera.zoom)
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
                        if (continuation.isActive) continuation.resume(snapshot.bitmap)
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

    private suspend fun styleJson(): String {
        cachedStyle?.let { return it }
        val style = runCatching {
            withContext(Dispatchers.IO) {
                val connection = (URL(OpenFreeMapStyle.STYLE_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "MeuCoach/0.1 (Android)")
                    instanceFollowRedirects = true
                }
                val json = connection.getInputStream().bufferedReader().use { it.readText() }
                OpenFreeMapStyle.customize(json)
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            return OSM_RASTER_STYLE
        }
        cachedStyle = style
        return style
    }

    private fun hasNetwork(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
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
