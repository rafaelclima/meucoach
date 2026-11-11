package com.meucoach.app.ui.calibration

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.app.ui.common.TargetTimeParts
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.calibration.CalibrationResult
import com.meucoach.core.domain.calibration.CalibrationTier
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Pace
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalibrationScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun calibrationDark() {
        capture(darkTheme = true, file = "src/test/screenshots/calibration-dark.png")
    }

    @Test
    fun calibrationLight() {
        capture(darkTheme = false, file = "src/test/screenshots/calibration-light.png")
    }

    private fun capture(darkTheme: Boolean, file: String) {
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                CalibrationContent(
                    state = CalibrationUiState(
                        isLoading = false,
                        form = CalibrationForm(
                            distanceKm = "5",
                            timeParts = TargetTimeParts(minutes = "30", seconds = "59"),
                            date = "24102026",
                            averageHeartRate = "181",
                        ),
                        recentEfforts = 4,
                        result = result(),
                    ),
                    onUpdate = {},
                    onCalculate = {},
                    onSave = {},
                    onBack = {},
                )
            }
        }

        compose.onRoot().captureRoboImage(file)
    }

    private fun result() = CalibrationResult(
        tier = CalibrationTier.A,
        vdot = 29.6477,
        thresholdPace = Pace.fromSecondsPerKm(388),
        lthr = 181,
        confidence = DataConfidence.HIGH,
        ruleId = "R-CAL-01",
        evidenceIds = listOf("EV-DANIELS-2014", "EV-FRIEL-2018"),
        warnings = emptySet(),
        recommendation = "Use os ritmos de treino e as zonas de FC pelo seu limite de esforço. " +
            "Recalibre em 6–8 semanas.",
    )
}
