package com.meucoach.app.ui.trends

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.app.screenshot.captureStableScreenshot
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.load.DailyLoad
import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadMetric
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.review.WeeklyAdherence
import com.meucoach.core.domain.review.WeeklyReadiness
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewObservation
import com.meucoach.core.domain.review.WeeklyReviewObservationKind
import com.meucoach.core.domain.review.WeeklyReviewStatus
import com.meucoach.core.domain.review.WeeklySleep
import com.meucoach.core.domain.review.WeeklyVolume
import com.meucoach.core.domain.trends.WeeklyIntensity
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
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
class WeeklyReviewScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun weeklyReviewDark() {
        capture(darkTheme = true, file = "src/test/screenshots/trends-weekly-review-dark.png")
    }

    @Test
    fun weeklyReviewLight() {
        capture(darkTheme = false, file = "src/test/screenshots/trends-weekly-review-light.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun weeklyReviewNarrowDark() {
        capture(darkTheme = true, file = "src/test/screenshots/trends-weekly-review-narrow-dark.png")
    }

    private fun capture(darkTheme: Boolean, file: String) {
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                TrendsContent(
                    state = TrendsUiState(
                        isLoading = false,
                        profile = profile(),
                        snapshot = snapshot(),
                        weeklyReview = review(),
                    ),
                )
            }
        }

        compose.onNodeWithTag("trends-list")
            .performScrollToNode(hasText("Resumo descritivo", substring = true))
        settle()
        compose.onNodeWithTag("weekly-review-card").captureStableScreenshot(compose, file)
    }

    private fun settle() {
        compose.waitForIdle()
        repeat(8) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
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

    private fun review() = WeeklyReview(
        weekStart = LocalDate.of(2026, 10, 19),
        weekEnd = LocalDate.of(2026, 10, 25),
        status = WeeklyReviewStatus.AVAILABLE,
        adherence = WeeklyAdherence(
            planned = 4,
            done = 3,
            verified = 2,
            manual = 1,
            legacy = 0,
            skipped = 1,
        ),
        volume = WeeklyVolume(plannedMin = 150, executedMin = 160, percentOfPlan = 107),
        intensity = WeeklyIntensity(LocalDate.of(2026, 10, 19), 7_200, 1_800, 1_800),
        intensitySessions = 4,
        readiness = WeeklyReadiness(measuredDays = 5, averageScore = 72, redDays = 0),
        sleep = WeeklySleep(measuredNights = 6, averageMinutes = 451, shortNights = 1),
        observations = listOf(
            observation(WeeklyReviewObservationKind.ADHERENCE_PARTIAL),
            observation(WeeklyReviewObservationKind.SHORT_SLEEP),
        ),
        ruleIds = listOf("R-REVIEW-01"),
        evidenceIds = emptyList(),
        confidence = DataConfidence.HIGH,
    )

    private fun observation(kind: WeeklyReviewObservationKind) = WeeklyReviewObservation(
        kind = kind,
        ruleId = if (kind == WeeklyReviewObservationKind.SHORT_SLEEP) "R-READY-04" else "R-REVIEW-01",
        evidenceIds = if (kind == WeeklyReviewObservationKind.SHORT_SLEEP) {
            listOf("EV-WALSH-2021", "EV-WATSON-2015")
        } else {
            emptyList()
        },
        confidence = DataConfidence.MEDIUM,
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
        return LoadSnapshot(
            daily = daily,
            ctlSeries = daily.map { it.load / 2.0 },
            atlSeries = daily.map { it.load },
            ctl = 10.0,
            atl = 20.0,
            tsb = -10.0,
            acuteAverage7d = 21.0,
            chronicAverage28d = 18.0,
            acwr = 1.2,
            monotony = 0.8,
            missingDays = 28,
            coverage = LoadCoverage(
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
