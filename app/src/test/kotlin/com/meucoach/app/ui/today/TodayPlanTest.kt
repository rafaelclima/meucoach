package com.meucoach.app.ui.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
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
class TodayPlanTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shows the weekly plan sessions`() {
        val monday = LocalDate.of(2026, 11, 2)
        val plan = WeeklyPlan(
            weekStart = monday,
            sessions = listOf(
                PlannedSession(
                    date = monday,
                    type = WorkoutType.EASY,
                    title = "Fácil em Z2",
                    durationMin = 40,
                    rationale = "Base.",
                    ruleIds = listOf("R-ZONE-01"),
                    evidenceIds = listOf("EV-SEILER-2010"),
                ),
                PlannedSession(
                    date = monday.plusDays(5),
                    type = WorkoutType.LONG,
                    title = "Longão",
                    durationMin = 51,
                    rationale = "Longo.",
                    ruleIds = listOf("R-PLAN-03"),
                    evidenceIds = listOf("EV-PFITZINGER-2008"),
                ),
            ),
            weeklyMinutes = 91,
            longRunMinutes = 51,
            qualityCount = 0,
            rationale = "Sem progressão até existir histórico.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-PLAN-03"),
            evidenceIds = listOf("EV-PFITZINGER-2008"),
        )

        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    state = demoTodayUiState(monday).copy(plan = plan),
                )
            }
        }

        val list = compose.onNodeWithTag("today-list")
        list.performScrollToNode(hasText("PLANO DA SEMANA"))
        compose.onNodeWithText("PLANO DA SEMANA").assertIsDisplayed()
        list.performScrollToNode(hasText("Fácil em Z2"))
        compose.onNodeWithText("Fácil em Z2").assertIsDisplayed()
        list.performScrollToNode(hasText("Longão"))
        compose.onAllNodesWithText("Longão")[0].assertIsDisplayed()
        list.performScrollToNode(hasText("Sem progressão até existir histórico."))
        compose.onNodeWithText("Sem progressão até existir histórico.").assertIsDisplayed()
    }
}
