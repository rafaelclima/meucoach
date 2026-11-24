package com.meucoach.app.story

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryPlanCalculator
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import java.time.Instant
import java.time.LocalDate

/** Deterministic five-kilometer plan shared by the art renderer tests. */
internal fun fiveKStoryPlan(): StoryPlan {
    val samples = (0..5).map { km ->
        ActivitySample(
            offsetSec = km * 360,
            latitude = -5.790 + km * 0.009,
            longitude = -35.210 + if (km % 2 == 0) 0.0 else 0.002,
            heartRate = 150,
            cadenceSpm = 170,
        )
    }
    val activity = ImportedActivity(
        source = ActivitySource.GPX,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-10-24T12:00:00Z"),
        durationSec = 1_800,
        distanceM = 5_000.0,
        calories = 310,
        averageHeartRate = 150,
        maxHeartRate = 170,
        averagePace = Pace.fromSecondsPerKm(360),
        averageCadenceSpm = 170,
        elevationGainM = 24.0,
        samples = samples,
    )
    return checkNotNull(StoryPlanCalculator.calculate(activity))
        .copy(localDate = LocalDate.parse("2026-10-24"))
}

/**
 * Synthetic landscape for the photo goldens: sky, horizon, ground and a sun.
 * No real photo or health data is used.
 */
internal object SyntheticArtPhoto {
    fun landscape(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val sky = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat(),
                intArrayOf(0xFFB7D6F2.toInt(), 0xFFEAF4FC.toInt(), 0xFF9CCB8B.toInt(), 0xFF426B3C.toInt()),
                floatArrayOf(0f, 0.46f, 0.47f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), sky)
        val sun = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFE08A.toInt() }
        canvas.drawCircle(width * 0.78f, height * 0.17f, width * 0.075f, sun)
        val cloud = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
        canvas.drawCircle(width * 0.22f, height * 0.13f, width * 0.055f, cloud)
        canvas.drawCircle(width * 0.29f, height * 0.12f, width * 0.045f, cloud)
        canvas.drawCircle(width * 0.16f, height * 0.12f, width * 0.04f, cloud)
        return bitmap
    }

    fun solid(width: Int, height: Int, color: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }
}
