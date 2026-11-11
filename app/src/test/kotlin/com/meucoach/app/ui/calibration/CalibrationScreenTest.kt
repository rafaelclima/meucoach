package com.meucoach.app.ui.calibration

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.meucoach.app.ui.common.TargetTimeParts
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.calibration.CalibrationCandidate
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

        compose.onNodeWithText("Seu limite de esforço").assertIsDisplayed()
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
            .performScrollToNode(hasText("CALIBRAÇÃO POR PROVA RECENTE"))
        compose.onNodeWithText("CALIBRAÇÃO POR PROVA RECENTE").assertIsDisplayed()
        compose.onNodeWithText("R-CAL-01", substring = true).assertDoesNotExist()
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
            .performScrollToNode(hasText("BASE INICIAL"))
        compose.onNodeWithTag("calibration-save").assertDoesNotExist()
        compose.onNodeWithText("BASE INICIAL").assertIsDisplayed()
    }

    @Test
    fun `shows the prefill confirmation when the form came from a run`() {
        setContent(
            state = loadedState().copy(
                prefilledFrom = CalibrationCandidate(
                    distanceM = 7_000.0,
                    durationSec = 1_800,
                    date = java.time.LocalDate.parse("2026-10-24"),
                    averageHeartRate = 176,
                    heartRateCoverage = 1.0,
                ),
            ),
        )

        compose.onNodeWithTag("calibration-list")
            .performScrollToNode(hasText("Confirme se foi um esforço máximo", substring = true))
        compose.onNodeWithText("Dados da corrida").assertIsDisplayed()
        compose.onNodeWithText("Confirme se foi um esforço máximo", substring = true).assertIsDisplayed()
    }

    @Test
    fun `shows form errors`() {
        setContent(
            state = loadedState().copy(
                errors = CalibrationFormErrors(time = "Informe minutos e segundos (0 a 59)."),
            ),
        )

        compose.onNodeWithTag("calibration-list")
            .performScrollToNode(hasText("Informe minutos e segundos (0 a 59)."))
        compose.onNodeWithText("Informe minutos e segundos (0 a 59).").assertIsDisplayed()
    }

    @Test
    fun `masks the date and keeps time segments explicit`() {
        var state by mutableStateOf(loadedState().copy(form = CalibrationForm()))
        compose.setContent {
            McTheme(darkTheme = true) {
                CalibrationContent(
                    state = state,
                    onUpdate = { transform -> state = state.copy(form = transform(state.form)) },
                    onCalculate = {},
                    onSave = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("calibration-list").performScrollToNode(hasTestTag("calibration-time-minutes"))
        compose.onNodeWithTag("calibration-time-minutes").performTextInput("30")
        compose.onNodeWithTag("calibration-time-seconds").performTextInput("59")
        compose.onNodeWithTag("calibration-list").performScrollToNode(hasTestTag("calibration-date"))
        compose.onNodeWithTag("calibration-date").performTextInput("24102026")

        assertThat(state.form.timeParts).isEqualTo(TargetTimeParts(minutes = "30", seconds = "59"))
        assertThat(state.form.date).isEqualTo("24102026")
        compose.onNodeWithTag("calibration-date").assertTextContains("24/10/2026")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun `keeps the time and date fields usable at 200 percent font`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                McTheme(darkTheme = false) {
                    CalibrationContent(
                        state = loadedState(),
                        onUpdate = {},
                        onCalculate = {},
                        onSave = {},
                        onBack = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("calibration-list").performScrollToNode(hasTestTag("calibration-time-minutes"))
        compose.onNodeWithTag("calibration-time-mask").assertIsDisplayed()
        compose.onNodeWithTag("calibration-time-minutes").assertIsDisplayed()
        compose.onNodeWithTag("calibration-list").performScrollToNode(hasTestTag("calibration-date"))
        compose.onNodeWithTag("calibration-date").assertIsDisplayed()
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
            timeParts = TargetTimeParts(minutes = "30", seconds = "59"),
            date = "24102026",
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
