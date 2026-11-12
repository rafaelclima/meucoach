package com.meucoach.app.ui.calibration

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.app.ui.common.TargetTimeParts
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.calibration.CalibrationPerformance
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

    @Test
    fun calibrationTierBDark() {
        capture(
            darkTheme = true,
            file = "src/test/screenshots/calibration-tier-b-dark.png",
            result = tierBResult(),
        )
    }

    @Test
    fun calibrationTierBLight() {
        capture(
            darkTheme = false,
            file = "src/test/screenshots/calibration-tier-b-light.png",
            result = tierBResult(),
        )
    }

    private fun capture(darkTheme: Boolean, file: String, result: CalibrationResult = result()) {
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
                        trainingEfforts = List(4) { index ->
                            CalibrationPerformance(
                                distanceM = 6_000.0,
                                durationSec = 1_800,
                                date = java.time.LocalDate.parse("2026-10-24"),
                                averageHeartRate = 170 + index,
                            )
                        },
                        result = result,
                    ),
                    onUpdate = {},
                    onCalculate = {},
                    onSave = {},
                    onEstimate = {},
                    onBack = {},
                )
            }
        }

        if (result.tier == CalibrationTier.B) {
            compose.onNodeWithTag("calibration-list").performScrollToNode(hasText("Salvar no perfil"))
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

    private fun tierBResult() = result().copy(
        tier = CalibrationTier.B,
        vdot = 40.9223,
        thresholdPace = Pace.fromSecondsPerKm(300),
        lthr = 174,
        confidence = DataConfidence.MEDIUM,
        ruleId = "R-CAL-02",
        recommendation = "Estimativa pelo seu melhor esforço sustentado de 20–35 min. " +
            "Confirme com um teste de 30 min quando possível; até lá, ajuste pelo esforço percebido.",
    )
}
