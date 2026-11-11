package com.meucoach.app.ui.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.app.ui.common.TargetTimeParts
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.GoalType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun onboardingDark() {
        capture(darkTheme = true, file = "src/test/screenshots/onboarding-dark.png")
    }

    @Test
    fun onboardingLight() {
        capture(darkTheme = false, file = "src/test/screenshots/onboarding-light.png")
    }

    @Test
    fun onboardingRoutineDark() {
        capture(darkTheme = true, step = 2, file = "src/test/screenshots/onboarding-routine-dark.png")
    }

    @Test
    fun onboardingGoalDark() {
        capture(
            darkTheme = true,
            step = 1,
            file = "src/test/screenshots/onboarding-goal-dark.png",
            form = defaultForm().copy(
                goalType = GoalType.FIVE_K,
                targetDate = "14032027",
                targetTimeParts = TargetTimeParts(minutes = "30", seconds = "00"),
            ),
        )
    }

    private fun capture(darkTheme: Boolean, step: Int = 0, file: String, form: OnboardingForm = defaultForm()) {
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                OnboardingContent(
                    state = OnboardingUiState(
                        isLoading = false,
                        step = step,
                        form = form,
                    ),
                    onUpdate = {},
                    onNext = {},
                    onBack = {},
                    onSave = {},
                    onToggleDay = {},
                    onDayMinutes = { _, _ -> },
                )
            }
        }

        compose.onRoot().captureRoboImage(file)
    }

    private fun defaultForm() = OnboardingForm(
        birthDate = "01011988",
        heightCm = "174",
        weightKg = "97",
        timezone = "America/Sao_Paulo",
        city = "Natal",
    )
}
