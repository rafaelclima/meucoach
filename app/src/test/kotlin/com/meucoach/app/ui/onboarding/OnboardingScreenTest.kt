package com.meucoach.app.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.GoalType
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
class OnboardingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shows the first step and continues`() {
        var continued = false
        setContent(state = step(0), onNext = { continued = true })

        compose.onNodeWithText("Vamos nos conhecer").assertIsDisplayed()
        compose.onNodeWithText("Data de nascimento").assertIsDisplayed()
        compose.onNodeWithText("Continuar").performClick()

        assertThat(continued).isTrue()
    }

    @Test
    fun `shows goal and availability on the second step`() {
        setContent(state = step(1).copy(form = form(goalType = GoalType.FIVE_K)))

        compose.onNodeWithText("Qual é o seu objetivo?").assertIsDisplayed()
        compose.onNodeWithText("5 km").assertIsDisplayed()
        compose.onNodeWithTag("onboarding-list").performScrollToNode(hasText("Disponibilidade"))
        compose.onNodeWithText("Disponibilidade").assertIsDisplayed()
    }

    @Test
    fun `shows the save action on the last step`() {
        var saved = false
        setContent(state = step(2), onSave = { saved = true })

        compose.onNodeWithText("Sua rotina").assertIsDisplayed()
        compose.onNodeWithText("Salvar perfil").performClick()

        assertThat(saved).isTrue()
    }

    @Test
    fun `shows validation errors`() {
        setContent(
            state = step(0).copy(errors = mapOf(OnboardingField.BIRTH_DATE to "Use dd/mm/aaaa.")),
        )

        compose.onNodeWithText("Use dd/mm/aaaa.").assertIsDisplayed()
    }

    @Test
    fun `shows and triggers cancel while editing`() {
        var cancelled = false
        setContent(state = step(0).copy(existingProfile = existingProfile()), onCancel = { cancelled = true })

        compose.onNodeWithText("EDITAR PERFIL").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()

        assertThat(cancelled).isTrue()
    }

    @Test
    fun `shows loading without the form`() {
        setContent(state = OnboardingUiState(), onNext = {})

        compose.onNodeWithText("Vamos nos conhecer").assertDoesNotExist()
    }

    @Test
    fun `formats the birth date while typing`() {
        var state by mutableStateOf(step(0))
        compose.setContent {
            McTheme(darkTheme = true) {
                OnboardingContent(
                    state = state,
                    onUpdate = { transform -> state = state.copy(form = transform(state.form)) },
                    onNext = {},
                    onBack = {},
                    onSave = {},
                    onToggleDay = {},
                    onDayMinutes = { _, _ -> },
                )
            }
        }

        compose.onNodeWithTag("birth-date-field").performTextInput("01111988")

        compose.onNodeWithTag("birth-date-field").assertTextContains("01/11/1988")
        assertThat(state.form.birthDate).isEqualTo("01111988")
    }

    @Test
    fun `picks a timezone from a searchable list`() {
        var state by mutableStateOf(step(2))
        compose.setContent {
            McTheme(darkTheme = true) {
                OnboardingContent(
                    state = state,
                    onUpdate = { transform -> state = state.copy(form = transform(state.form)) },
                    onNext = {},
                    onBack = {},
                    onSave = {},
                    onToggleDay = {},
                    onDayMinutes = { _, _ -> },
                )
            }
        }

        compose.onNodeWithTag("onboarding-list").performScrollToNode(hasTestTag("timezone-field"))
        compose.onNodeWithTag("timezone-field").performClick()
        compose.onNodeWithTag("selection-search").performTextInput("noronha")
        compose.onNodeWithText("Fernando de Noronha").assertIsDisplayed()
        compose.onNodeWithText("Fernando de Noronha").performClick()

        assertThat(state.form.timezone).isEqualTo("America/Noronha")
    }

    private fun setContent(
        state: OnboardingUiState,
        onNext: () -> Unit = {},
        onBack: () -> Unit = {},
        onSave: () -> Unit = {},
        onCancel: (() -> Unit)? = null,
    ) {
        compose.setContent {
            McTheme(darkTheme = true) {
                OnboardingContent(
                    state = state,
                    onUpdate = {},
                    onNext = onNext,
                    onBack = onBack,
                    onSave = onSave,
                    onToggleDay = {},
                    onDayMinutes = { _, _ -> },
                    onCancel = onCancel,
                )
            }
        }
    }

    private fun step(index: Int) = OnboardingUiState(
        isLoading = false,
        step = index,
        form = form(),
    )

    private fun form(goalType: GoalType = GoalType.FIVE_K) = OnboardingForm(
        birthDate = "01011988",
        goalType = goalType,
        targetDate = "14032027",
        timezone = "America/Sao_Paulo",
    )

    private fun existingProfile() = com.meucoach.core.model.AthleteProfile(
        id = "athlete-1",
        birthDate = java.time.LocalDate.of(1988, 1, 1),
        sex = com.meucoach.core.model.Sex.MALE,
    )
}
