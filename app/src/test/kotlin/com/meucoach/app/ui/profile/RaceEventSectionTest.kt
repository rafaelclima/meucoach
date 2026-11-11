package com.meucoach.app.ui.profile

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
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
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RaceEventSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 6)

    @Test
    fun `shows the planned race and its declared target time only`() {
        compose.setContent {
            McTheme(darkTheme = true) {
                ProfileContent(
                    state = state(events = listOf(plannedEvent())),
                    onEdit = {},
                    onCalibrate = {},
                    onOpenHealth = {},
                    onManageData = {},
                    onShowTutorial = {},
                )
            }
        }

        compose.onNodeWithTag("profile-list").performScrollToNode(hasTestTag("race-card"))
        compose.onNodeWithText("10 km").assertIsDisplayed()
        compose.onNodeWithText("14/03/2027").assertIsDisplayed()
        compose.onNodeWithText("Meta informada").assertIsDisplayed()
        compose.onNodeWithText("50:00", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Planejada").assertIsDisplayed()
        compose.onNodeWithTag("race-profile-complete").assertIsDisplayed()
    }

    @Test
    fun `creates a race with the four supported distances`() {
        var saved: Triple<RaceDistance, LocalDate, Int?>? = null
        compose.setContent {
            McTheme(darkTheme = true) {
                ProfileContent(
                    state = state(events = emptyList()),
                    onEdit = {},
                    onCalibrate = {},
                    onOpenHealth = {},
                    onManageData = {},
                    onShowTutorial = {},
                    onRaceSave = { distance, date, time -> saved = Triple(distance, date, time) },
                )
            }
        }

        compose.onNodeWithTag("profile-list").performScrollToNode(hasTestTag("race-card"))
        compose.onNodeWithTag("race-add").performClick()
        compose.onNodeWithText("5 km").performClick()
        compose.onNodeWithTag("race-date").performTextInput("14032027")
        compose.onNodeWithTag("race-date").assertTextContains("14/03/2027")
        compose.onNodeWithTag("race-time-minutes").performTextInput("25")
        compose.onNodeWithTag("race-time-seconds").performTextInput("00")
        compose.onNodeWithTag("race-save").performClick()

        assertThat(saved).isEqualTo(Triple(RaceDistance.FIVE_K, LocalDate.of(2027, 3, 14), 1_500))
    }

    @Test
    fun `chooses hours and applies the hours mask`() {
        var saved: Triple<RaceDistance, LocalDate, Int?>? = null
        compose.setContent {
            McTheme(darkTheme = true) {
                ProfileContent(
                    state = state(events = emptyList()),
                    onEdit = {},
                    onCalibrate = {},
                    onOpenHealth = {},
                    onManageData = {},
                    onShowTutorial = {},
                    onRaceSave = { distance, date, time -> saved = Triple(distance, date, time) },
                )
            }
        }

        compose.onNodeWithTag("profile-list").performScrollToNode(hasTestTag("race-card"))
        compose.onNodeWithTag("race-add").performClick()
        compose.onNodeWithTag("race-time-unit-hours").performClick()
        compose.onNodeWithTag("race-time-hours").performTextInput("1")
        compose.onNodeWithTag("race-time-minutes").performTextInput("02")
        compose.onNodeWithTag("race-time-seconds").performTextInput("03")
        compose.onNodeWithTag("race-date").performTextInput("14032027")
        compose.onNodeWithTag("race-save").performClick()

        assertThat(saved).isEqualTo(Triple(RaceDistance.TEN_K, LocalDate.of(2027, 3, 14), 3_723))
    }

    @Test
    fun `converts minutes to hours when the unit changes`() {
        var saved: Triple<RaceDistance, LocalDate, Int?>? = null
        compose.setContent {
            McTheme(darkTheme = true) {
                ProfileContent(
                    state = state(events = emptyList()),
                    onEdit = {},
                    onCalibrate = {},
                    onOpenHealth = {},
                    onManageData = {},
                    onShowTutorial = {},
                    onRaceSave = { distance, date, time -> saved = Triple(distance, date, time) },
                )
            }
        }

        compose.onNodeWithTag("profile-list").performScrollToNode(hasTestTag("race-card"))
        compose.onNodeWithTag("race-add").performClick()
        compose.onNodeWithTag("race-time-minutes").performTextInput("90")
        compose.onNodeWithTag("race-time-seconds").performTextInput("30")
        compose.onNodeWithTag("race-time-unit-hours").performClick()

        compose.onNodeWithTag("race-time-hours").assertTextContains("1")
        compose.onNodeWithTag("race-time-minutes").assertTextContains("30")
        compose.onNodeWithTag("race-time-seconds").assertTextContains("30")

        compose.onNodeWithTag("race-date").performTextInput("14032027")
        compose.onNodeWithTag("race-save").performClick()

        assertThat(saved).isEqualTo(Triple(RaceDistance.TEN_K, LocalDate.of(2027, 3, 14), 5_430))
    }

    @Test
    fun `edits the active race and keeps the declared time optional`() {
        var saved: Triple<RaceDistance, LocalDate, Int?>? = null
        compose.setContent {
            McTheme(darkTheme = true) {
                ProfileContent(
                    state = state(events = listOf(plannedEvent())),
                    onEdit = {},
                    onCalibrate = {},
                    onOpenHealth = {},
                    onManageData = {},
                    onShowTutorial = {},
                    onRaceSave = { distance, date, time -> saved = Triple(distance, date, time) },
                )
            }
        }

        compose.onNodeWithTag("profile-list").performScrollToNode(hasTestTag("race-card"))
        compose.onNodeWithTag("race-edit").performClick()
        compose.onNodeWithTag("race-time-minutes").performTextClearance()
        compose.onNodeWithTag("race-time-seconds").performTextClearance()
        compose.onNodeWithTag("race-save").performClick()

        assertThat(saved).isEqualTo(Triple(RaceDistance.TEN_K, LocalDate.of(2027, 3, 14), null))
    }

    @Test
    fun `completes without an activity and exposes explicit cancel and no-show`() {
        var completed: String? = "not-called"
        var cancelled = false
        var noShow = false
        compose.setContent {
            McTheme(darkTheme = true) {
                ProfileContent(
                    state = state(events = listOf(plannedEvent())),
                    onEdit = {},
                    onCalibrate = {},
                    onOpenHealth = {},
                    onManageData = {},
                    onShowTutorial = {},
                    onRaceComplete = { activityId -> completed = activityId },
                    onRaceCancel = { cancelled = true },
                    onRaceNoShow = { noShow = true },
                )
            }
        }

        compose.onNodeWithTag("profile-list").performScrollToNode(hasTestTag("race-card"))
        compose.onNodeWithTag("race-profile-complete").performClick()
        compose.onNodeWithTag("race-profile-confirm").performClick()
        assertThat(completed).isNull()

        compose.onNodeWithTag("race-profile-cancel").performClick()
        assertThat(cancelled).isTrue()

        compose.onNodeWithTag("race-profile-no-show").performClick()
        assertThat(noShow).isTrue()
    }

    @Test
    fun `keeps resolved events in history without edit actions`() {
        val completed = plannedEvent().copy(
            status = com.meucoach.core.model.RaceStatus.COMPLETED,
            resolvedAt = Instant.parse("2027-03-14T12:00:00Z"),
        )
        compose.setContent {
            McTheme(darkTheme = true) {
                ProfileContent(
                    state = state(events = listOf(completed)),
                    onEdit = {},
                    onCalibrate = {},
                    onOpenHealth = {},
                    onManageData = {},
                    onShowTutorial = {},
                )
            }
        }

        compose.onNodeWithTag("profile-list").performScrollToNode(hasTestTag("race-card"))
        compose.onNodeWithText("HISTÓRICO").assertIsDisplayed()
        compose.onNodeWithText("Concluída").assertIsDisplayed()
        compose.onAllNodesWithText("Editar").assertCountEquals(0)
    }

    private fun state(events: List<RaceEvent>) = ProfileUiState(
        isLoading = false,
        profile = profile(),
        today = today,
        raceEvents = events,
    )

    private fun plannedEvent() = RaceEvent(
        id = "race-1",
        distance = RaceDistance.TEN_K,
        targetDate = LocalDate.of(2027, 3, 14),
        targetTimeSec = 3_000,
        createdAt = Instant.parse("2026-10-01T12:00:00Z"),
        updatedAt = Instant.parse("2026-10-01T12:00:00Z"),
    )

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(
            mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 50),
        ),
    )
}
