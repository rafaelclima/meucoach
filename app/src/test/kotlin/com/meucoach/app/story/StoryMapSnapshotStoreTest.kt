package com.meucoach.app.story

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.domain.story.BoundingBox
import com.meucoach.core.domain.story.StoryGeoPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoryMapSnapshotStoreTest {
    private val directory = ApplicationProvider.getApplicationContext<Context>().cacheDir

    @Test
    fun `round-trips the bitmap and the exact route pixels`() {
        val file = File(directory, "round-trip.png")
        val bitmap = Bitmap.createBitmap(12, 20, Bitmap.Config.ARGB_8888)
        val snapshot = StoryMapSnapshot(
            bitmap = bitmap,
            routePixels = listOf(PointF(1.25f, 2.5f), PointF(11.75f, 19.5f)),
        )

        StoryMapSnapshotStore.write(file, snapshot)
        val loaded = StoryMapSnapshotStore.read(file)

        assertThat(loaded).isNotNull()
        assertThat(loaded!!.bitmap.width).isEqualTo(12)
        assertThat(loaded.bitmap.height).isEqualTo(20)
        assertThat(loaded.routePixels).containsExactly(PointF(1.25f, 2.5f), PointF(11.75f, 19.5f))
        assertThat(loaded.startPixel).isEqualTo(PointF(1.25f, 2.5f))
        assertThat(loaded.endPixel).isEqualTo(PointF(11.75f, 19.5f))
    }

    @Test
    fun `a staged png without the route sidecar is unusable`() {
        val file = File(directory, "no-route.png")
        file.outputStream().use { output ->
            Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        assertThat(StoryMapSnapshotStore.read(file)).isNull()
    }

    @Test
    fun `a corrupt route sidecar is rejected instead of guessed`() {
        val file = File(directory, "corrupt.png")
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        StoryMapSnapshotStore.write(file, StoryMapSnapshot(bitmap, listOf(PointF(1f, 2f))))
        StoryMapSnapshotStore.routeFile(file).writeText("not a manifest")

        assertThat(StoryMapSnapshotStore.read(file)).isNull()
    }

    @Test
    fun `delete removes both files`() {
        val file = File(directory, "delete.png")
        StoryMapSnapshotStore.write(
            file,
            StoryMapSnapshot(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888), listOf(PointF(1f, 2f))),
        )

        StoryMapSnapshotStore.delete(file)

        assertThat(file.exists()).isFalse()
        assertThat(StoryMapSnapshotStore.routeFile(file).exists()).isFalse()
    }

    @Test
    fun `the fallback projects the same pixels as the pure port`() {
        val bounds = BoundingBox(minLat = -5.80, maxLat = -5.78, minLon = -35.22, maxLon = -35.20)
        val bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        val camera = StoryMapCameraCalculator.calculateForStory(bounds, 720, 1280, pitchDegrees = 42f)
        val geoPoints = listOf(
            StoryGeoPoint(-5.79, -35.21),
            StoryGeoPoint(-5.785, -35.205),
        )
        val projection = StoryMapProjection(camera, 720, 1280)

        val snapshot = StoryMapSnapshot.fromFallback(bitmap, camera, geoPoints)

        snapshot.routePixels.forEachIndexed { index, point ->
            val expected = projection.project(geoPoints[index].latitude, geoPoints[index].longitude)
            assertThat(point.x).isCloseTo(expected[0], org.assertj.core.data.Offset.offset(0.001f))
            assertThat(point.y).isCloseTo(expected[1], org.assertj.core.data.Offset.offset(0.001f))
        }
    }
}
