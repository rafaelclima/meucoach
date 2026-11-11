package com.meucoach.app.ui.profile

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.core.data.health.HealthConnectStatus
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.GoalType
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
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
import java.time.Instant
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

    @Test
    fun profileHealthDark() {
        captureHealth(darkTheme = true, file = "src/test/screenshots/profile-health-dark.png")
    }

    @Test
    fun profileHealthLight() {
        captureHealth(darkTheme = false, file = "src/test/screenshots/profile-health-light.png")
    }

    @Test
    fun profileRaceDark() {
        captureRace(darkTheme = true, file = "src/test/screenshots/profile-race-dark.png")
    }

    @Test
    fun profileRaceLight() {
        captureRace(darkTheme = false, file = "src/test/screenshots/profile-race-light.png")
    }

    private fun captureRace(darkTheme: Boolean, file: String) {
        val event = raceEvent()
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                ProfileContent(
                    state = state().copy(
                        raceEvents = listOf(event),
                        raceProjection = com.meucoach.core.domain.race.RaceTargetProjector.project(
                            event.distance,
                            event.targetTimeSec,
                        ),
                    ),
                    onEdit = {},
                    onCalibrate = {},
                    onOpenHealth = {},
                    onManageData = {},
                    onShowTutorial = {},
                )
            }
        }

        compose.onNodeWithTag("profile-list").performScrollToNode(hasTestTag("race-card"))
        compose.onRoot().captureRoboImage(file)
    }

    private fun raceEvent() = RaceEvent(
        id = "race-1",
        distance = RaceDistance.TEN_K,
        targetDate = LocalDate.of(2027, 3, 14),
        targetTimeSec = 3_000,
        createdAt = Instant.parse("2026-10-01T12:00:00Z"),
        updatedAt = Instant.parse("2026-10-01T12:00:00Z"),
    )

    private fun captureHealth(darkTheme: Boolean, file: String) {
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                ProfileContent(
                    state = state(),
                    onEdit = {},
                    onCalibrate = {},
                    onOpenHealth = {},
                    onManageData = {},
                    onShowTutorial = {},
                )
            }
        }

        compose.onNodeWithTag("profile-list")
            .performScrollToNode(hasText("Sono, recuperação e oxigenação"))
        compose.onRoot().captureRoboImage(file)
    }

    private fun capture(darkTheme: Boolean, file: String) {
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                ProfileContent(
                    state = state(),
                    onEdit = {},
                    onCalibrate = {},
                    onOpenHealth = {},
                    onManageData = {},
                    onShowTutorial = {},
                )
            }
        }

        compose.onRoot().captureRoboImage(file)
    }

    private fun state() = ProfileUiState(
        isLoading = false,
        profile = profile(),
        today = LocalDate.of(2026, 11, 6),
        now = Instant.now(),
        health = ProfileHealthSummary(
            status = HealthConnectStatus.AVAILABLE,
            hasPermissions = true,
            storedDays = 47,
            lastSyncedAt = Instant.now(),
        ),
    )

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
