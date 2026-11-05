package com.meucoach.app.ui.trends

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.load.DailyLoad
import com.meucoach.core.domain.load.LoadMetric
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.PlanCompletion
import com.meucoach.core.domain.prescription.PlanCompletionWeek
import com.meucoach.core.domain.readiness.ReadinessHistoryDay
import com.meucoach.core.domain.trends.ActivityTrends
import com.meucoach.core.domain.trends.ConsistencyDay
import com.meucoach.core.domain.trends.ConsistencySummary
import com.meucoach.core.domain.trends.IntensityDistribution
import com.meucoach.core.domain.trends.PaceHeartRatePoint
import com.meucoach.core.domain.trends.WeeklyIntensity
import com.meucoach.core.domain.trends.WeeklyVolume
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Pace
import com.meucoach.core.model.ReadinessLevel
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
class TrendsFullScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun volumeDark() {
        capture(
            scrollTo = hasContentDescription("Volume semanal em quilômetros"),
            file = "src/test/screenshots/trends-volume-dark.png",
        )
    }

    @Test
    fun efficiencyDark() {
        capture(
            scrollTo = hasContentDescription(
                "Evolução da economia de corrida em 6 corridas",
                substring = true,
            ),
            file = "src/test/screenshots/trends-efficiency-dark.png",
        )
    }

    @Test
    fun readinessDark() {
        capture(
            scrollTo = hasContentDescription("Prontidão dos últimos 14 dias"),
            file = "src/test/screenshots/trends-readiness-dark.png",
        )
    }

    private fun capture(scrollTo: androidx.compose.ui.test.SemanticsMatcher, file: String) {
        compose.setContent {
            McTheme(darkTheme = true) {
                TrendsContent(
                    state = TrendsUiState(
                        isLoading = false,
                        profile = profile(),
                        snapshot = snapshot(),
                        activityTrends = trends(),
                        intensity = intensity(),
                        planCompletion = completion(),
                    ),
                    readinessHistory = readiness(),
                )
            }
        }
        compose.onNodeWithTag("trends-list").performScrollToNode(scrollTo)
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
                load = if (index >= 28) (index - 27) * 3.0 else 0.0,
                metric = if (index >= 28) LoadMetric.RTSS else null,
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
        )
    }

    private fun trends(): ActivityTrends {
        val start = LocalDate.of(2026, 10, 5)
        return ActivityTrends(
            weeks = (0 until 6).map { index ->
                WeeklyVolume(start.plusWeeks(index.toLong()), 8.0 + index * 2.5, 2 + index % 2)
            },
            consistency = ConsistencySummary(
                windowDays = 42,
                activeDays = 14,
                runs = 18,
                daysSinceLastRun = 1,
            ),
            paceHeartRate = listOf(
                PaceHeartRatePoint(LocalDate.of(2026, 9, 28), Pace.fromSecondsPerKm(372), 148),
                PaceHeartRatePoint(LocalDate.of(2026, 10, 4), Pace.fromSecondsPerKm(366), 149),
                PaceHeartRatePoint(LocalDate.of(2026, 10, 11), Pace.fromSecondsPerKm(361), 150),
                PaceHeartRatePoint(LocalDate.of(2026, 10, 18), Pace.fromSecondsPerKm(357), 149),
                PaceHeartRatePoint(LocalDate.of(2026, 10, 25), Pace.fromSecondsPerKm(351), 151),
                PaceHeartRatePoint(LocalDate.of(2026, 11, 2), Pace.fromSecondsPerKm(348), 150),
            ),
            totalKilometers = 61.5,
            averagePace = Pace.fromSecondsPerKm(360),
            averageHeartRate = 149,
            heatmap = (0 until 42).map { offset ->
                val date = start.plusDays(offset.toLong())
                val active = offset % 3 == 0
                ConsistencyDay(
                    date = date,
                    kilometers = if (active) 5.0 + offset % 4 else 0.0,
                    runs = if (active) 1 else 0,
                )
            },
        )
    }

    private fun intensity() = IntensityDistribution(
        windowDays = 28,
        easySec = 8_400,
        moderateSec = 1_200,
        hardSec = 900,
        sessions = 12,
        weeks = listOf(
            WeeklyIntensity(LocalDate.of(2026, 10, 12), 2_400, 300, 0),
            WeeklyIntensity(LocalDate.of(2026, 10, 19), 3_000, 600, 300),
            WeeklyIntensity(LocalDate.of(2026, 10, 26), 3_000, 300, 600),
        ),
        confidence = DataConfidence.HIGH,
    )

    private fun completion() = PlanCompletion(
        weeks = (0 until 8).map { index ->
            PlanCompletionWeek(
                weekStart = LocalDate.of(2026, 9, 14).plusWeeks(index.toLong()),
                planned = 3,
                done = if (index % 3 == 0) 3 else 2,
                skipped = if (index % 4 == 0) 1 else 0,
            )
        },
        windowWeeks = 8,
    )

    private fun readiness() = listOf(48, 56, 61, 43, 72, 68, 74, 81, 66, 70, 58, 76, 79, 84).mapIndexed {
            index,
            score,
        ->
        val date = LocalDate.of(2026, 10, 22).plusDays(index.toLong())
        ReadinessHistoryDay(
            date = date,
            score = score,
            level = when {
                score >= 70 -> ReadinessLevel.GREEN
                score >= 50 -> ReadinessLevel.AMBER
                else -> ReadinessLevel.RED
            },
            confidence = DataConfidence.MEDIUM,
            hasCheckIn = index % 2 == 0,
        )
    }
}
