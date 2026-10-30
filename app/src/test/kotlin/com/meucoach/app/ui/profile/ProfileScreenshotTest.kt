package com.meucoach.app.ui.profile

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.GoalType
import com.meucoach.core.model.Sex
import com.meucoach.core.model.TrainingGoal
import com.meucoach.core.model.WeeklyAvailability
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun profileDark() {
        capture(darkTheme = true, file = "src/test/screenshots/profile-dark.png")
    }

    @Test
    fun profileLight() {
        capture(darkTheme = false, file = "src/test/screenshots/profile-light.png")
    }

    private fun capture(darkTheme: Boolean, file: String) {
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                ProfileContent(
                    state = ProfileUiState(isLoading = false, profile = profile()),
                    onEdit = {},
                    onCalibrate = {},
                )
            }
        }

        compose.onRoot().captureRoboImage(file)
    }

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        heightCm = 174.0,
        weightKg = 97.0,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(
            mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 50, DayOfWeek.SATURDAY to 90),
        ),
        goal = TrainingGoal(
            type = GoalType.FIVE_K,
            targetDate = LocalDate.of(2027, 3, 14),
            targetDistanceM = 5_000.0,
            targetTimeSec = 1_800,
        ),
        city = "Natal",
    )
}
