package com.meucoach.app.ui.metrics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.core.data.health.HealthConnectStatus
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.health.DailyMetricsTrendsCalculator
import com.meucoach.core.model.DailyMetrics
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
class HealthMetricsContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shows the recovery summary and all metric charts`() {
        setContent(stateWithMetrics())

        compose.onNodeWithText("Como seu corpo chegou").assertIsDisplayed()
        compose.onNodeWithTag("health-coverage").assertIsDisplayed()
        compose
            .onNodeWithContentDescription("Sono nos últimos 30 dias", substring = true)
            .assertExists()
        compose
            .onNodeWithContentDescription("FC de repouso nos últimos 30 dias", substring = true)
            .assertExists()
        compose
            .onNodeWithContentDescription("HRV nos últimos 30 dias", substring = true)
            .assertExists()
        compose
            .onNodeWithContentDescription("Oxigenação nos últimos 30 dias", substring = true)
            .assertExists()
    }

    @Test
    fun `opens the table alternative for a metric`() {
        setContent(stateWithMetrics())

        val list = compose.onNodeWithTag("metrics-import-list")
        list.performScrollToNode(hasTestTag("health-table-sleep"))
        compose.onNodeWithTag("health-table-sleep").performClick()

        list.performScrollToNode(hasText("Ocultar tabela"))
        compose.onNodeWithText("Ocultar tabela").assertIsDisplayed()
        list.performScrollToNode(hasText("03/11"))
        compose.onNodeWithText("03/11").assertExists()
    }

    @Test
    fun `changes the health window`() {
        var selected: Int? = null
        setContent(stateWithMetrics(), onWindowSelected = { selected = it })

        compose.onNodeWithTag("metrics-import-list")
            .performScrollToNode(hasTestTag("health-window-7"))
        compose.onNodeWithTag("health-window-7").performClick()

        assertThat(selected).isEqualTo(7)
    }

    @Test
    fun `explains the empty state before asking for a source`() {
        setContent(MetricsImportUiState())

        compose.onNodeWithText("Ainda sem dados de recuperação").assertIsDisplayed()
        compose.onNodeWithText("Fontes de dados").assertIsDisplayed()
    }

    @Test
    fun `shows a hint when today's data has not arrived`() {
        setContent(
            state = stateWithMetrics(),
            healthState = connectedHealth(),
            today = LocalDate.of(2026, 11, 5),
        )

        compose.onNodeWithTag("metrics-import-list")
            .performScrollToNode(hasText("Os dados de hoje ainda não chegaram"))
        compose.onNodeWithText("Os dados de hoje ainda não chegaram").assertIsDisplayed()
    }

    @Test
    fun `hides the hint when today's data is present`() {
        setContent(
            state = stateWithMetrics(),
            healthState = connectedHealth(),
            today = LocalDate.of(2026, 11, 4),
        )

        compose.onNodeWithText("Os dados de hoje ainda não chegaram").assertDoesNotExist()
    }

    private fun connectedHealth() = HealthConnectUiState(
        isLoading = false,
        status = HealthConnectStatus.AVAILABLE,
        missingPermissions = emptySet(),
    )

    private fun setContent(
        state: MetricsImportUiState,
        onWindowSelected: (Int) -> Unit = {},
        healthState: HealthConnectUiState = HealthConnectUiState(),
        today: LocalDate = LocalDate.now(),
    ) {
        compose.setContent {
            McTheme(darkTheme = true) {
                HealthContent(
                    state = state,
                    onPick = {},
                    onBack = {},
                    healthState = healthState,
                    onWindowSelected = onWindowSelected,
                    today = today,
                )
            }
        }
    }

    private fun stateWithMetrics(): MetricsImportUiState {
        val days = (0 until 30).map { offset ->
            val date = LocalDate.of(2026, 11, 4).minusDays((29 - offset).toLong())
            DailyMetrics(
                date = date,
                sleepDurationMin = 390 + (offset % 6) * 15,
                restingHeartRate = 58.0 + (offset % 4),
                hrvRmssdMs = 55.0 + (offset % 5) * 2,
                spo2Avg = 96.0 + (offset % 3) * 0.5,
            )
        }
        val trends = DailyMetricsTrendsCalculator().calculate(
            metrics = days,
            today = LocalDate.of(2026, 11, 4),
            windowDays = 30,
        )
        return MetricsImportUiState(
            storedDays = days.size,
            latestMetricDate = days.last().date,
            windowDays = 30,
            trends = trends,
        )
    }
}
