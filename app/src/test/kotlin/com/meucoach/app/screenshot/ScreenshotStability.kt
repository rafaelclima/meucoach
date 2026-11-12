package com.meucoach.app.screenshot

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage

/**
 * A screen can paint in stages (fonts, images, async content). Waits (bounded)
 * for several consecutive identical captures before writing the golden, which
 * covers a frame that briefly looks stable between two paints. A genuinely
 * different screen still differs and fails the comparison; only the race
 * between capture and paint is removed.
 *
 * Charts no longer need this race: `McLineChart`/`McBarChart` build their model
 * synchronously, so the series is present from the first composition.
 */
fun SemanticsNodeInteraction.captureStableScreenshot(
    compose: ComposeTestRule,
    file: String,
    attempts: Int = 100,
    initialSettleMillis: Long = 0L,
) {
    if (!roborazziEnabled()) return
    // Defensive real-time window before sampling; charts are synchronous now,
    // but the rest of the screen can still settle asynchronously.
    if (initialSettleMillis > 0) {
        compose.waitForIdle()
        Thread.sleep(initialSettleMillis)
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }
    var previous: IntArray? = null
    var stableFrames = 0
    var attempt = 0
    while (attempt < attempts && stableFrames < STABLE_FRAMES) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val pixels = captureToImage().toPixelMap().buffer
        if (previous != null && pixels.contentEquals(previous)) {
            stableFrames++
        } else {
            stableFrames = 0
        }
        previous = pixels
        attempt++
        if (stableFrames < STABLE_FRAMES) Thread.sleep(STABLE_FRAME_DELAY_MILLIS)
    }
    captureRoboImage(
        filePath = file,
        roborazziOptions = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.00001f),
        ),
    )
}

/**
 * Real-time window before sampling frames for screens with charts.
 *
 * Charts are synchronous, so this is a defensive settle for the rest of the
 * screen (fonts and async content). One second keeps the gate deterministic
 * without changing any expected image.
 */
const val CHART_SETTLE_MILLIS = 1_000L

/** ~700 ms of an unchanged frame: enough for font and content layout to settle. */
private const val STABLE_FRAMES = 8
private const val STABLE_FRAME_DELAY_MILLIS = 100L

/** Roborazzi no-ops when no mode is enabled; skip the sampling cost too. */
private fun roborazziEnabled(): Boolean =
    listOf("record", "compare", "verify").any { System.getProperty("roborazzi.test.$it") == "true" }
