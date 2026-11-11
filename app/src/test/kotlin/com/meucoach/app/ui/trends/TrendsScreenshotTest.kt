package com.meucoach.app.ui.trends

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.app.screenshot.CHART_SETTLE_MILLIS
import com.meucoach.app.screenshot.captureStableScreenshot
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.load.DailyLoad
import com.meucoach.core.domain.load.LoadMetric
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Sex
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
class TrendsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun trendsDark() {
        capture(darkTheme = true, file = "src/test/screenshots/trends-dark.png")
    }

    @Test
    fun trendsLight() {
        capture(darkTheme = false, file = "src/test/screenshots/trends-light.png")
    }

    @Test
    fun trendsChartsDark() {
        compose.setContent {
            McTheme(darkTheme = true) {
                TrendsContent(
                    state = TrendsUiState(
                        isLoading = false,
                        profile = profile(),
                        snapshot = snapshot(),
                    ),
                )
            }
        }
        compose.onNodeWithTag("trends-list")
            .performScrollToNode(
                androidx.compose.ui.test.hasContentDescription(
                    "Gráfico de carga de base e fadiga recente dos últimos 42 dias",
                ),
            )
        settle()
        compose.onRoot().captureStableScreenshot(
            compose,
            "src/test/screenshots/trends-charts-dark.png",
            initialSettleMillis = CHART_SETTLE_MILLIS,
        )
    }

    private fun capture(darkTheme: Boolean, file: String) {
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                TrendsContent(
                    state = TrendsUiState(
                        isLoading = false,
                        profile = profile(),
                        snapshot = snapshot(),
                    ),
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

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        timezone = "America/Sao_Paulo",
    )

    private fun snapshot(): LoadSnapshot {
        val start = LocalDate.of(2026, 9, 19)
        val daily = List(42) { index ->
            DailyLoad(
                date = start.plusDays(index.toLong()),
                load = if (index in 28..41) (index - 27) * 3.0 else 0.0,
                metric = if (index in 28..41) LoadMetric.RTSS else null,
            )
        }
        val ctl = daily.mapIndexed { index, day -> day.load * (index + 1) / 100.0 }
        val atl = daily.map { it.load / 2.0 }
        return LoadSnapshot(
            daily = daily,
            ctlSeries = ctl,
            atlSeries = atl,
            ctl = ctl.last(),
            atl = atl.last(),
            tsb = ctl.last() - atl.last(),
            acuteAverage7d = daily.takeLast(7).map { it.load }.average(),
            chronicAverage28d = daily.takeLast(28).map { it.load }.average(),
            acwr = 1.2,
            monotony = 0.8,
            missingDays = 28,
            coverage = com.meucoach.core.domain.load.LoadCoverage(
                daysInWindow = 28,
                daysWithLoad = 14,
                daysWithoutActivity = 14,
                daysWithoutData = 0,
                lastActivityAt = null,
                sourceFresh = true,
            ),
        )
    }
}
