package com.meucoach.app.ui.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.core.designsystem.theme.McTheme
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

    private fun capture(darkTheme: Boolean, file: String) {
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                OnboardingContent(
                    state = OnboardingUiState(
                        isLoading = false,
                        form = OnboardingForm(
                            birthDate = "01/01/1988",
                            heightCm = "174",
                            weightKg = "97",
                            timezone = "America/Sao_Paulo",
                        ),
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
}
