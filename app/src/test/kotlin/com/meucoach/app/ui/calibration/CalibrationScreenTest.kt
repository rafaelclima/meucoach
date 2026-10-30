package com.meucoach.app.ui.calibration

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.calibration.CalibrationResult
import com.meucoach.core.domain.calibration.CalibrationTier
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Pace
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalibrationScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shows the form and calculates`() {
        var calculated = false
        setContent(state = loadedState(), onCalculate = { calculated = true })

        compose.onNodeWithText("Seu limiar").assertIsDisplayed()
        compose.onNodeWithTag("calibration-list").performScrollToNode(hasText("Calcular"))
        compose.onNodeWithText("Calcular").performClick()

        assertThat(calculated).isTrue()
    }

    @Test
    fun `shows the result and saves it`() {
        var saved = false
        setContent(
            state = loadedState().copy(result = result()),
            onSave = { saved = true },
        )

        compose.onNodeWithTag("calibration-list")
            .performScrollToNode(hasText("TIER A · PROVA/TT RECENTE"))
        compose.onNodeWithText("TIER A · PROVA/TT RECENTE").assertIsDisplayed()
        compose.onNodeWithText("29,6").assertIsDisplayed()
        compose.onAllNodesWithText("181")[0].assertIsDisplayed()
        compose.onNodeWithText("6:28").assertIsDisplayed()
        compose.onNodeWithTag("calibration-list").performScrollToNode(hasText("Salvar no perfil"))
        compose.onNodeWithTag("calibration-save").performClick()

        assertThat(saved).isTrue()
    }

    @Test
    fun `hides save for a result without metrics`() {
        setContent(
            state = loadedState().copy(
                result = result().copy(
                    tier = CalibrationTier.C,
                    vdot = null,
                    lthr = null,
                    thresholdPace = null,
                    confidence = DataConfidence.LOW,
                ),
            ),
        )

        compose.onNodeWithTag("calibration-list")
            .performScrollToNode(hasText("TIER C · BASE INICIAL"))
        compose.onNodeWithTag("calibration-save").assertDoesNotExist()
        compose.onNodeWithText("TIER C · BASE INICIAL").assertIsDisplayed()
    }

    @Test
    fun `shows form errors`() {
        setContent(
            state = loadedState().copy(
                errors = CalibrationFormErrors(time = "Use mm:ss ou h:mm:ss."),
            ),
        )

        compose.onNodeWithText("Use mm:ss ou h:mm:ss.").assertIsDisplayed()
    }

    private fun setContent(state: CalibrationUiState, onCalculate: () -> Unit = {}, onSave: () -> Unit = {}) {
        compose.setContent {
            McTheme(darkTheme = true) {
                CalibrationContent(
                    state = state,
                    onUpdate = {},
                    onCalculate = onCalculate,
                    onSave = onSave,
                    onBack = {},
                )
            }
        }
    }

    private fun loadedState() = CalibrationUiState(
        isLoading = false,
        form = CalibrationForm(
            distanceKm = "5",
            time = "30:59",
            date = "24/10/2026",
            averageHeartRate = "181",
        ),
    )

    private fun result() = CalibrationResult(
        tier = CalibrationTier.A,
        vdot = 29.6477,
        thresholdPace = Pace.fromSecondsPerKm(388),
        lthr = 181,
        confidence = DataConfidence.HIGH,
        ruleId = "R-CAL-01",
        evidenceIds = listOf("EV-DANIELS-2014", "EV-FRIEL-2018"),
        warnings = emptySet(),
        recommendation = "Use as paces E/M/T/I/R e recalibre em 6–8 semanas.",
    )
}
