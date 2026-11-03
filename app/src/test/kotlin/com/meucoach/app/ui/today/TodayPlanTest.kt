package com.meucoach.app.ui.today

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
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
                    title = "Fácil e conversável",
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
        list.performScrollToNode(hasText("Fácil e conversável"))
        compose.onNodeWithText("Fácil e conversável").assertIsDisplayed()
        list.performScrollToNode(hasText("Longão"))
        compose.onAllNodesWithText("Longão").assertCountEquals(1)
        compose.onAllNodesWithText("Longão")[0].assertIsDisplayed()
        list.performScrollToNode(hasText("Sem progressão até existir histórico."))
        compose.onNodeWithText("Sem progressão até existir histórico.").assertIsDisplayed()
    }

    @Test
    fun `completes today's session`() {
        val today = LocalDate.now()
        var completed: LocalDate? = null
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    state = demoTodayUiState(today).copy(plan = planWith(today)),
                    onCompleteSession = { completed = it },
                )
            }
        }

        val list = compose.onNodeWithTag("today-list")
        list.performScrollToNode(hasText("Concluir"))
        compose.onNodeWithText("Concluir").performClick()

        assertThat(completed).isEqualTo(today)
    }

    @Test
    fun `opens the workout explanation`() {
        val today = LocalDate.of(2026, 11, 2)
        val explanation = TodayExplanationUi(
            rationale = "Base aeróbica em ritmo fácil, conversável do início ao fim.",
            confidence = DataConfidence.MEDIUM,
            usedData = listOf("Check-in de hoje", "Ritmo no limite calibrado"),
            missingData = listOf("Sono", "Variabilidade cardíaca"),
            rules = listOf(ExplanationRuleUi("R-ZONE-01", "Zonas pelo limite de esforço")),
            evidence = listOf(
                ExplanationEvidenceUi(
                    id = "EV-SEILER-2010",
                    title = "Distribuição de intensidade",
                    source = "Seiler, IJSPP 2010",
                ),
            ),
        )
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    today = today,
                    state = demoTodayUiState(today).copy(explanation = explanation),
                )
            }
        }

        val list = compose.onNodeWithTag("today-list")
        list.performScrollToNode(hasText("Ver explicação"))
        compose.onNodeWithText("Ver explicação").performClick()

        compose.onNodeWithText("Por que este treino?").assertIsDisplayed()
        compose.onNodeWithText("O QUE USEI").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("COMO DECIDI").performScrollTo()
        compose
            .onNodeWithText("Zonas pelo limite de esforço", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("R-ZONE-01", substring = true).assertDoesNotExist()

        compose.onNodeWithText("Ver fontes e detalhes técnicos").performScrollTo().performClick()
        compose.onNodeWithText("Seiler, IJSPP 2010", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("R-ZONE-01", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `opens the readiness explanation with its own title`() {
        val today = LocalDate.of(2026, 11, 2)
        val explanation = TodayExplanationUi(
            rationale = "Sua prontidão está verde (74/100): sono de 7h30 e variabilidade acima da média.",
            confidence = DataConfidence.HIGH,
            usedData = listOf("Check-in de hoje", "Sono recente"),
            missingData = listOf("FC de repouso"),
            rules = listOf(ExplanationRuleUi("R-READY-01", "Prontidão combina os sinais")),
            evidence = listOf(
                ExplanationEvidenceUi(
                    id = "EV-SAW-2016",
                    title = "Check-in subjetivo",
                    source = "Saw et al., BJSM 2016",
                ),
            ),
        )
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    today = today,
                    state = demoTodayUiState(today).copy(readinessExplanation = explanation),
                )
            }
        }

        compose.onNodeWithText("Por que esta prontidão?").performClick()

        compose.onNodeWithTag("explanation-title").assertTextEquals("Por que esta prontidão?")
        compose
            .onNodeWithText("Sua prontidão está verde", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("O QUE USEI").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `finishes the guided session`() {
        val today = LocalDate.of(2026, 11, 2)
        var completed: LocalDate? = null
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    today = today,
                    state = demoTodayUiState(today).copy(plan = planWith(today)),
                    onCompleteSession = { completed = it },
                )
            }
        }

        val list = compose.onNodeWithTag("today-list")
        list.performScrollToNode(hasText("Iniciar"))
        compose.onNodeWithText("Iniciar").performClick()
        compose.onNodeWithText("Sessão guiada").assertIsDisplayed()
        compose.onNodeWithText("Concluir treino").performClick()

        assertThat(completed).isEqualTo(today)
    }

    @Test
    fun `swaps today's session with another planned day`() {
        val today = LocalDate.of(2026, 11, 2)
        val tomorrow = today.plusDays(1)
        var swapped: Pair<LocalDate, LocalDate>? = null
        val plan = planWith(today).let { current ->
            current.copy(
                sessions = current.sessions + PlannedSession(
                    date = tomorrow,
                    type = WorkoutType.QUALITY,
                    title = "Tempo",
                    durationMin = 30,
                    rationale = "Qualidade.",
                    ruleIds = listOf("R-PLAN-02"),
                    evidenceIds = listOf("EV-SEILER-2010"),
                ),
                qualityCount = 1,
            )
        }
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    today = today,
                    state = demoTodayUiState(today).copy(plan = plan),
                    onSwapSession = { first, second -> swapped = first to second },
                )
            }
        }

        val list = compose.onNodeWithTag("today-list")
        list.performScrollToNode(hasText("Trocar"))
        compose.onNodeWithText("Trocar").performClick()
        compose.onNodeWithText("Trocar treino").assertIsDisplayed()
        compose.onNodeWithTag("swap-candidate-$tomorrow").performClick()

        assertThat(swapped).isEqualTo(today to tomorrow)
    }

    @Test
    fun `shows a completed session`() {
        val today = LocalDate.now()
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    state = demoTodayUiState(today).copy(
                        plan = planWith(today).let { plan ->
                            plan.copy(
                                sessions = plan.sessions.map {
                                    it.copy(status = SessionStatus.DONE)
                                },
                            )
                        },
                    ),
                )
            }
        }

        val list = compose.onNodeWithTag("today-list")
        list.performScrollToNode(hasText("Feito"))
        compose.onNodeWithText("Feito").assertIsDisplayed()
    }

    @Test
    fun `shows the real load ratio`() {
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    state = demoTodayUiState().copy(acwr = 1.42),
                )
            }
        }

        val list = compose.onNodeWithTag("today-list")
        list.performScrollToNode(hasText("Aumento 1,42× vs. mês"))
        compose.onNodeWithText("Aumento 1,42× vs. mês").assertIsDisplayed()
    }

    @Test
    fun `shows an empty load card without any data`() {
        val state = todayState(null, today = LocalDate.now())

        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(onDemoAction = {}, state = state)
            }
        }

        val list = compose.onNodeWithTag("today-list")
        list.performScrollToNode(hasText("CARGA DA SEMANA"))
        compose.onNodeWithText("Sem carga ainda").assertIsDisplayed()
        compose.onNodeWithText("Acumulada", ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("Balanço", ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun `does not invent a load ratio without history`() {
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(onDemoAction = {}, state = demoTodayUiState())
            }
        }

        compose.onAllNodesWithText("Aumento", substring = true).assertCountEquals(0)
    }

    @Test
    fun `navigates to trends and to the recent activity`() {
        var trends = false
        var openedActivity: String? = null
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(
                    onDemoAction = {},
                    state = demoTodayUiState(),
                    onTrendsClick = { trends = true },
                    onActivityClick = { openedActivity = it },
                )
            }
        }

        val list = compose.onNodeWithTag("today-list")
        list.performScrollToNode(hasText("Ver tendências"))
        compose.onNodeWithText("Ver tendências").performClick()
        assertThat(trends).isTrue()

        list.performScrollToNode(hasText("ÚLTIMA CORRIDA"))
        compose.onNodeWithText("Corrida de rua").performClick()
        assertThat(openedActivity).isEqualTo("demo-activity")
    }

    private fun planWith(date: LocalDate) = WeeklyPlan(
        weekStart = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)),
        sessions = listOf(
            PlannedSession(
                date = date,
                type = WorkoutType.EASY,
                title = "Fácil e conversável",
                durationMin = 40,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
            ),
        ),
        weeklyMinutes = 40,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Sem progressão.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
    )
}
