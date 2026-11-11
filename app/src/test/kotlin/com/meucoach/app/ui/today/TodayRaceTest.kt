package com.meucoach.app.ui.today

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.race.RaceTargetProjector
import com.meucoach.core.domain.today.TodayDecision
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.RaceCompletionSource
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.RaceStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodayRaceTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 2)

    @Test
    fun `competition replaces the training card and hides the briefing`() {
        val event = raceEvent(today)
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    state = competitionState(event),
                )
            }
        }

        compose.onNodeWithTag("today-list").performScrollToNode(hasTestTag("race-day-card"))
        compose.onNodeWithTag("race-day-card").assertIsDisplayed()
        compose.onNodeWithText("PROVA DE HOJE").assertIsDisplayed()
        compose.onNodeWithText("10 km").assertIsDisplayed()
        compose.onNodeWithText("Concluir prova").assertIsDisplayed()

        compose.onAllNodesWithText("TREINO DE HOJE").assertCountEquals(0)
        compose.onAllNodesWithText("Como correr hoje").assertCountEquals(0)
        compose.onAllNodesWithText("Trocar").assertCountEquals(0)
        compose.onAllNodesWithText("Concluir treino").assertCountEquals(0)
    }

    @Test
    fun `profile and race actions are explicit`() {
        var completed: String? = "not-called"
        var cancelled = false
        var noShow = false
        val event = raceEvent(today)
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    state = competitionState(event),
                    onCompleteRace = { activityId -> completed = activityId },
                    onCancelRace = { cancelled = true },
                    onNoShowRace = { noShow = true },
                )
            }
        }

        val list = compose.onNodeWithTag("today-list")
        list.performScrollToNode(hasTestTag("race-complete"))
        compose.onNodeWithTag("race-complete").performClick()
        compose.onNodeWithTag("race-confirm").performClick()
        assertThat(completed).isNull()

        list.performScrollToNode(hasTestTag("race-cancel"))
        compose.onNodeWithTag("race-cancel").performClick()
        assertThat(cancelled).isTrue()

        list.performScrollToNode(hasTestTag("race-no-show"))
        compose.onNodeWithTag("race-no-show").performClick()
        assertThat(noShow).isTrue()
    }

    @Test
    fun `race card remains readable at 200 percent font`() {
        val event = raceEvent(today)
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f),
            ) {
                McTheme(darkTheme = true) {
                    TodayScreen(
                        onDemoAction = {},
                        state = competitionState(event),
                    )
                }
            }
        }

        compose.onNodeWithTag("today-list").performScrollToNode(hasTestTag("race-complete"))
        compose.onNodeWithTag("race-complete").assertIsDisplayed()
        compose.onNodeWithTag("race-no-show").assertExists()
    }

    @Test
    fun `plan marks the race day and hides a legacy session on it`() {
        val raceDate = today.plusDays(4)
        val plan = WeeklyPlan(
            weekStart = today,
            sessions = listOf(
                PlannedSession(
                    date = raceDate,
                    type = WorkoutType.QUALITY,
                    title = "Sessão legada",
                    durationMin = 30,
                    rationale = "Legado.",
                    ruleIds = listOf("R-PLAN-02"),
                    evidenceIds = listOf("EV-SEILER-2010"),
                ),
            ),
            weeklyMinutes = 30,
            longRunMinutes = null,
            qualityCount = 1,
            rationale = "Plano.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-PLAN-02"),
            evidenceIds = listOf("EV-SEILER-2010"),
        )
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    state = demoTodayUiState(today).copy(
                        plan = plan,
                        eventDates = setOf(raceDate),
                    ),
                )
            }
        }

        val list = compose.onNodeWithTag("today-list")
        list.performScrollToNode(hasText("PLANO DA SEMANA"))
        list.performScrollToNode(hasText("Sem treino neste dia"))
        compose.onNodeWithText("Sem treino neste dia").assertIsDisplayed()
        compose.onAllNodesWithText("Sessão legada").assertCountEquals(0)
    }

    @Test
    fun `race day shows the target reference and its splits`() {
        val event = raceEvent(today)
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    state = competitionState(event),
                )
            }
        }

        compose.onNodeWithTag("today-list").performScrollToNode(hasTestTag("race-projection"))
        compose.onNodeWithText("REFERÊNCIA DA META").assertIsDisplayed()
        compose.onNodeWithText("5:00/km").assertIsDisplayed()
        compose.onNodeWithText("4:51–5:09/km").assertIsDisplayed()
        compose.onNodeWithTag("today-list").performScrollToNode(hasTestTag("race-splits-open"))
        compose.onNodeWithTag("race-splits-open").performClick()
        compose.onNodeWithTag("race-splits-list").performScrollToNode(hasText("10 km · 50:00"))
        compose.onNodeWithText("10 km · 50:00").assertIsDisplayed()
        compose.onNodeWithTag("race-splits-close").performClick()
    }

    @Test
    fun `without a target time the race day does not invent a pace`() {
        val event = raceEvent(today).copy(targetTimeSec = null)
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    state = competitionState(event),
                )
            }
        }

        compose.onNodeWithTag("today-list").performScrollToNode(hasTestTag("race-day-card"))
        compose.onAllNodesWithText("REFERÊNCIA DA META").assertCountEquals(0)
        compose.onAllNodesWithText("Ver passagens por km").assertCountEquals(0)
    }

    @Test
    fun `an inferred completion can be undone from the race day card`() {
        var undone: String? = null
        val event = raceEvent(today).copy(
            status = RaceStatus.COMPLETED,
            resolvedAt = Instant.parse("2026-11-02T12:00:00Z"),
            activityId = "activity-1",
            completionSource = RaceCompletionSource.AUTO_IMPORT,
        )
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    state = competitionState(event),
                    onUndoAutoRaceCompletion = { undone = it },
                )
            }
        }

        compose.onNodeWithTag("today-list").performScrollToNode(hasTestTag("race-undo-auto"))
        compose.onNodeWithText(
            "Prova concluída com a corrida importada. Se não foi a prova, você pode desfazer.",
        ).assertIsDisplayed()
        compose.onNodeWithTag("race-undo-auto").performClick()

        assertThat(undone).isEqualTo("race-1")
    }

    @Test
    fun `a completed race without an available run hides the run link`() {
        val event = raceEvent(today).copy(
            status = RaceStatus.COMPLETED,
            resolvedAt = Instant.parse("2026-11-02T12:00:00Z"),
            activityId = "activity-1",
            completionSource = RaceCompletionSource.USER,
        )
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    state = competitionState(event),
                )
            }
        }

        compose.onNodeWithTag("today-list").performScrollToNode(hasTestTag("race-day-card"))
        compose.onAllNodesWithText("Ver corrida").assertCountEquals(0)
    }

    private fun competitionState(event: RaceEvent) = demoTodayUiState(today).copy(
        decision = TodayDecision.Competition(event),
        prescription = null,
        briefing = null,
        raceProjection = RaceTargetProjector.project(event.distance, event.targetTimeSec),
        eventDates = setOf(event.targetDate),
    )

    private fun raceEvent(date: LocalDate) = RaceEvent(
        id = "race-1",
        distance = RaceDistance.TEN_K,
        targetDate = date,
        targetTimeSec = 3_000,
        createdAt = Instant.parse("2026-10-01T12:00:00Z"),
        updatedAt = Instant.parse("2026-10-01T12:00:00Z"),
    )
}
