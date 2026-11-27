package com.meucoach.app.ui.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class CycleOverviewTodayTest {
    @get:Rule
    val compose = createComposeRule()

    private val monday = LocalDate.of(2026, 11, 30)

    @Test
    fun `plan card opens the cycle sheet with the same projection`() {
        compose.setContent {
            TodayScreen(
                onDemoAction = {},
                state = demoTodayUiState(monday).copy(
                    plan = plan(),
                    cycleOverview = CycleOverviewFixtures.overview(),
                ),
            )
        }

        compose.onNodeWithTag("today-list")
            .performScrollToNode(hasTestTag("cycle-summary"))
        compose.onNodeWithTag("cycle-summary").assertIsDisplayed()
        compose.onNodeWithTag("cycle-summary-open").performClick()
        compose.onNodeWithTag("cycle-sheet").assertIsDisplayed()
        compose.onNodeWithTag("cycle-race-title").assertIsDisplayed()
        compose.onNodeWithText("5 km · 10 de janeiro").assertExists()
    }

    @Test
    fun `no race hides the cycle entry`() {
        compose.setContent {
            TodayScreen(
                onDemoAction = {},
                state = demoTodayUiState(monday).copy(
                    plan = plan(),
                    cycleOverview = null,
                ),
            )
        }

        compose.onNodeWithTag("cycle-summary").assertDoesNotExist()
        compose.onNodeWithTag("cycle-sheet").assertDoesNotExist()
    }

    private fun plan() = WeeklyPlan(
        weekStart = monday,
        sessions = listOf(
            PlannedSession(
                date = monday,
                type = WorkoutType.EASY,
                title = "Rodagem",
                durationMin = 40,
                rationale = "Base aeróbica.",
                ruleIds = listOf("R-PLAN-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
            ),
        ),
        weeklyMinutes = 40,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Semana de base.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
    )
}
