package com.meucoach.app.story

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Typeface
import com.meucoach.app.ui.activitydetail.ActivityDetailTestData
import com.meucoach.core.domain.story.StoryPlanCalculator
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Proves the frame renderer consumes the native snapshot pixels instead of
 * reprojecting the route with the Kotlin port. Both snapshots below share the
 * same bitmap and the same plan; only [StoryMapSnapshot.routePixels] changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoryFrameRendererTest {
    @Test
    fun `frame follows the snapshot route pixels`() {
        val activity = ActivityDetailTestData.state().activity!!.imported
        val plan = checkNotNull(StoryPlanCalculator.calculate(activity, targetDurationSec = 30))
        val bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        val palette = StoryPalette(
            background = 0xFF0B0C0F.toInt(),
            surface = 0xFF16181D.toInt(),
            surfaceInset = 0xFF1F2229.toInt(),
            border = 0xFF2A2E36.toInt(),
            textPrimary = 0xFFF4F5F7.toInt(),
            textSecondary = 0xFFB7BBC4.toInt(),
            textTertiary = 0xFF8B909A.toInt(),
            accent = 0xFFD8FF3E.toInt(),
            coral = 0xFFFF7A6B.toInt(),
            onAccent = 0xFF0B0C0F.toInt(),
            mapCasing = 0xFF08090B.toInt(),
            mapBaseRoute = 0xFF4A545F.toInt(),
            zone1 = 0xFF4C7DFF.toInt(),
            zone2 = 0xFF38C6A5.toInt(),
            zone3 = 0xFFD8FF3E.toInt(),
            zone4 = 0xFFFFB020.toInt(),
            zone5 = 0xFFFF7A6B.toInt(),
        )
        val base = StoryFrameRenderer(
            palette = palette,
            typeface = Typeface.DEFAULT,
            mapBackground = StoryMapSnapshot(
                bitmap = bitmap,
                routePixels = listOf(PointF(80f, 160f), PointF(600f, 1080f)),
            ),
        ).render(plan, timeSec = 8f, width = 720, height = 1280)
        val shifted = StoryFrameRenderer(
            palette = palette,
            typeface = Typeface.DEFAULT,
            mapBackground = StoryMapSnapshot(
                bitmap = bitmap,
                routePixels = listOf(PointF(180f, 260f), PointF(700f, 1180f)),
            ),
        ).render(plan, timeSec = 8f, width = 720, height = 1280)

        assertThat(base.sameAs(shifted)).isFalse()
    }
}
