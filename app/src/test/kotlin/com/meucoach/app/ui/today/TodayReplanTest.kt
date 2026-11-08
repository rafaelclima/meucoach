package com.meucoach.app.ui.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.app.application.ReplanChangeKind
import com.meucoach.app.ui.replan.ReplanChangeUi
import com.meucoach.app.ui.replan.ReplanSuggestionUi
import com.meucoach.app.ui.replan.ReplanUiState
import com.meucoach.app.ui.replan.ReplanVersionChangeUi
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.replan.ReplanTrigger
import com.meucoach.core.model.DataConfidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodayReplanTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 4)
    private val future = today.plusDays(2)

    @Test
    fun `shows the version badge and asks to replan the week explicitly`() {
        var requested = false
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    state = demoTodayUiState(today).copy(plan = planWithFuture()),
                    replan = ReplanUiState(planVersion = 2),
                    onReplanRequest = { requested = true },
                )
            }
        }

        val list = compose.onNodeWithTag("today-list")
        list.performScrollToNode(hasText("PLANO DA SEMANA"))
        compose.onNodeWithText("v2").assertIsDisplayed()
        list.performScrollToNode(hasText("Replanejar minha semana"))
        compose.onNodeWithTag("plan-replan").performClick()

        assertThat(requested).isTrue()
    }

    @Test
    fun `shows what changed in the latest revision`() {
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    state = demoTodayUiState(today).copy(plan = planWithFuture()),
                    replan = ReplanUiState(
                        planVersion = 2,
                        lastChange = ReplanVersionChangeUi(
                            fromVersion = 1,
                            toVersion = 2,
                            reason = "Revisão pedida por você.",
                            changes = listOf(modifiedChange()),
                        ),
                    ),
                    onReplanRequest = {},
                    onReplanAccept = {},
                    onReplanDecline = {},
                    onReplanSuppress = {},
                )
            }
        }

        val list = compose.onNodeWithTag("today-list")
        list.performScrollToNode(hasText("Ver o que mudou"))
        compose.onNodeWithTag("plan-changes").performClick()

        compose.onNodeWithTag("replan-changes-sheet").assertIsDisplayed()
        compose.onNodeWithText("Plano v1 → v2").assertIsDisplayed()
        compose.onNodeWithText("Tempo · 30 min → Fácil e conversável · 30 min").assertIsDisplayed()
    }

    @Test
    fun `offers accept decline and suppress for a suggestion`() {
        var accepted = false
        var declined = false
        var suppressed = false
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    state = demoTodayUiState(today).copy(plan = planWithFuture()),
                    replan = ReplanUiState(
                        planVersion = 1,
                        suggestion = ReplanSuggestionUi(
                            versionFrom = 1,
                            versionTo = 2,
                            trigger = ReplanTrigger.MISSED_SESSIONS,
                            headline = "Dá para ajustar o resto da semana",
                            body = "2 treinos desta semana não aconteceram.",
                            changes = listOf(modifiedChange()),
                            confidence = DataConfidence.MEDIUM,
                        ),
                    ),
                    onReplanAccept = { accepted = true },
                    onReplanDecline = { declined = true },
                    onReplanSuppress = { suppressed = true },
                )
            }
        }

        val list = compose.onNodeWithTag("today-list")
        list.performScrollToNode(hasText("AJUSTE DA SEMANA"))
        compose.onNodeWithTag("replan-card").assertIsDisplayed()
        compose.onNodeWithText("Não sugerir hoje").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("replan-accept").performScrollTo().performClick()
        compose.onNodeWithTag("replan-decline").performScrollTo().performClick()
        compose.onNodeWithTag("replan-suppress").performScrollTo().performClick()

        assertThat(accepted).isTrue()
        assertThat(declined).isTrue()
        assertThat(suppressed).isTrue()
    }

    private fun modifiedChange() = ReplanChangeUi(
        date = future,
        kind = ReplanChangeKind.MODIFIED,
        kindLabel = "Muda",
        previousLabel = "Tempo · 30 min",
        nextLabel = "Fácil e conversável · 30 min",
    )

    private fun planWithFuture() = WeeklyPlan(
        weekStart = today.minusDays(today.dayOfWeek.value - 1L),
        sessions = listOf(
            PlannedSession(
                date = today,
                type = WorkoutType.EASY,
                title = "Fácil e conversável",
                durationMin = 40,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
            ),
            PlannedSession(
                date = future,
                type = WorkoutType.QUALITY,
                title = "Tempo",
                durationMin = 30,
                rationale = "Qualidade.",
                ruleIds = listOf("R-PLAN-02"),
                evidenceIds = listOf("EV-DANIELS-2014"),
            ),
        ),
        weeklyMinutes = 70,
        longRunMinutes = null,
        qualityCount = 1,
        rationale = "Semana original.",
        confidence = DataConfidence.LOW,
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-DANIELS-2014"),
    )
}
