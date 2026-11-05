package com.meucoach.app.ui.metrics

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureRoboImage
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
    fun healthDark() {
        capture(darkTheme = true, file = "src/test/screenshots/health-dark.png")
    }

    @Test
    fun healthLight() {
        capture(darkTheme = false, file = "src/test/screenshots/health-light.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h852dp-xxhdpi")
    fun healthNarrowDark() {
        capture(darkTheme = true, file = "src/test/screenshots/health-narrow-dark.png")
    }

    @Test
    fun healthChartsDark() {
        compose.setContent {
            McTheme(darkTheme = true) {
                MetricsImportContent(
                    state = state(),
                    onPick = {},
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag("metrics-import-list")
            .performScrollToNode(hasTestTag("health-table-sleep"))
        settle()
        compose.onRoot().captureRoboImage("src/test/screenshots/health-charts-dark.png")
    }

    @Test
    fun healthRecoveryChartsDark() {
        compose.setContent {
            McTheme(darkTheme = true) {
                MetricsImportContent(
                    state = state(),
                    onPick = {},
                    onBack = {},
                )
            }
        }
        compose.onNodeWithTag("metrics-import-list")
            .performScrollToNode(hasTestTag("health-table-hrv"))
        settle()
        compose.onRoot().captureRoboImage("src/test/screenshots/health-recovery-dark.png")
    }

    private fun capture(darkTheme: Boolean, file: String) {
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                MetricsImportContent(
                    state = state(),
                    onPick = {},
                    onBack = {},
                )
            }
        }

        settle()
        compose.onRoot().captureRoboImage(file)
    }

    /** Vico builds the model off the composition; give it a moment before capturing. */
    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
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
