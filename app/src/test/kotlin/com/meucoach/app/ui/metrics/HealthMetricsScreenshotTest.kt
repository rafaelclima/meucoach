package com.meucoach.app.ui.metrics

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.app.screenshot.CHART_SETTLE_MILLIS
import com.meucoach.app.screenshot.captureStableScreenshot
import com.meucoach.core.data.health.HealthConnectStatus
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.health.DailyMetricsTrendsCalculator
import com.meucoach.core.model.DailyMetrics
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HealthMetricsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(sdk = [35], qualifiers = "w393dp-h760dp-xxhdpi")
    fun healthDark() {
        capture(darkTheme = true, file = "src/test/screenshots/health-dark.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w393dp-h760dp-xxhdpi")
    fun healthLight() {
        capture(darkTheme = false, file = "src/test/screenshots/health-light.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h760dp-xxhdpi")
    fun healthNarrowDark() {
        capture(darkTheme = true, file = "src/test/screenshots/health-narrow-dark.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w393dp-h1500dp-xxhdpi")
    fun healthChartsDark() {
        compose.setContent {
            McTheme(darkTheme = true) {
                HealthContent(
                    state = state(),
                    onPick = {},
                )
            }
        }
        awaitChartsReady(
            "Sono nos últimos 30 dias",
            "FC de repouso nos últimos 30 dias",
        )
        settle()
        compose.onRoot().captureScreenshot("src/test/screenshots/health-charts-dark.png")
    }

    @Test
    fun healthRecoveryChartsDark() {
        compose.setContent {
            McTheme(darkTheme = true) {
                HealthContent(
                    state = state(),
                    onPick = {},
                )
            }
        }
        compose.onNodeWithTag("metrics-import-list")
            .performScrollToNode(hasTestTag("health-card-hrv"))
        awaitChart("HRV nos últimos 30 dias")
        settle()
        // Capture the root: a sub-node capture can miss Vico's canvas layer
        // under Robolectric, while the root still guards the card and chart.
        compose.onRoot().captureScreenshot("src/test/screenshots/health-recovery-dark.png")
    }

    @Test
    fun healthTodayHintDark() {
        compose.setContent {
            McTheme(darkTheme = true) {
                HealthContent(
                    state = state(),
                    healthState = connectedHealth(),
                    onPick = {},
                    today = LocalDate.of(2026, 11, 5),
                )
            }
        }

        compose.onNodeWithTag("metrics-import-list")
            .performScrollToNode(hasText("Os dados de hoje ainda não chegaram"))
        settle()
        compose.onRoot().captureScreenshot("src/test/screenshots/health-today-hint-dark.png")
    }

    @Test
    fun healthSourcesDark() {
        compose.setContent {
            McTheme(darkTheme = true) {
                HealthContent(
                    state = state(),
                    healthState = connectedHealth(),
                    onPick = {},
                )
            }
        }

        compose.onNodeWithTag("metrics-import-list")
            .performScrollToNode(hasText("Abrir Health Connect"))
        settle()
        compose.onNodeWithTag("health-connect-card")
            .captureScreenshot("src/test/screenshots/health-sources-dark.png")
    }

    private fun connectedHealth() = HealthConnectUiState(
        isLoading = false,
        status = HealthConnectStatus.AVAILABLE,
        missingPermissions = emptySet(),
    )

    private fun capture(darkTheme: Boolean, file: String) {
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                HealthContent(
                    state = state(),
                    onPick = {},
                )
            }
        }

        settle()
        compose.onRoot().captureScreenshot(file)
    }

    /**
     * The chart surfaces render a few edge pixels differently across Robolectric
     * runs. Keep a tiny tolerance so the golden guards the layout without
     * failing on anti-aliasing noise.
     */
    private fun SemanticsNodeInteraction.captureScreenshot(file: String) {
        captureStableScreenshot(compose, file, initialSettleMillis = CHART_SETTLE_MILLIS)
    }

    private fun awaitChartsReady(vararg descriptions: String) {
        compose.waitUntil(5_000) {
            descriptions.all { description ->
                compose.onAllNodes(
                    hasTestTag("mc-chart-ready") and hasContentDescription(description, substring = true),
                ).fetchSemanticsNodes().isNotEmpty()
            } &&
                compose.onAllNodesWithTag("mc-chart-loading").fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
    }

    private fun awaitChart(description: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodes(
                hasTestTag("mc-chart-ready") and hasContentDescription(description, substring = true),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun settle() {
        compose.waitForIdle()
        repeat(8) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        // Vico draws on its own dispatcher; under a loaded suite the model can be
        // ready while the canvas is still blank. Give the real thread a moment.
        Thread.sleep(300)
        compose.waitForIdle()
    }

    private fun state(): MetricsImportUiState {
        val today = LocalDate.of(2026, 11, 4)
        val days = (0 until 30).map { offset ->
            val date = today.minusDays((29 - offset).toLong())
            DailyMetrics(
                date = date,
                sleepDurationMin = 390 + (offset % 6) * 15,
                restingHeartRate = 58.0 + (offset % 4),
                hrvRmssdMs = 55.0 + (offset % 5) * 2,
                spo2Avg = 96.0 + (offset % 3) * 0.5,
            )
        }
        return MetricsImportUiState(
            storedDays = days.size,
            latestMetricDate = days.last().date,
            trends = DailyMetricsTrendsCalculator().calculate(
                metrics = days,
                today = today,
                windowDays = 30,
            ),
        )
    }
}
