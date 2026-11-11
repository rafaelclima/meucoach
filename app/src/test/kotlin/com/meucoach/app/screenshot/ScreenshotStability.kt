package com.meucoach.app.screenshot

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage

/**
 * Vico paints on its own dispatcher. Under a loaded suite the model can be ready
 * while the canvas is still mid-frame, so the golden catches anti-aliasing noise
 * from a partially drawn chart.
 *
 * Waits (bounded) for two consecutive identical captures before writing the
 * golden. A genuinely different screen still differs and fails the comparison;
 * only the race between capture and paint is removed.
 */
fun SemanticsNodeInteraction.captureStableScreenshot(compose: ComposeTestRule, file: String, attempts: Int = 40) {
    var previous: IntArray? = null
    var stableFrames = 0
    var attempt = 0
    while (attempt < attempts && stableFrames < 1) {
        compose.waitForIdle()
        val pixels = captureToImage().toPixelMap().buffer
        if (previous != null && pixels.contentEquals(previous)) {
            stableFrames++
        } else {
            stableFrames = 0
        }
        previous = pixels
        attempt++
        if (stableFrames < 1) Thread.sleep(100)
    }
    captureRoboImage(
        filePath = file,
        roborazziOptions = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.00001f),
        ),
    )
}
