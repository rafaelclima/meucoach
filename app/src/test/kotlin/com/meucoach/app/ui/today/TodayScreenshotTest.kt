package com.meucoach.app.ui.today

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.app.firstrun.FirstRunTipStore
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DailyMetricsSource
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodayScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun clearTips() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences(FirstRunTipStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun todayDark() {
        capture(darkTheme = true, file = "src/test/screenshots/today-dark.png")
    }

    @Test
    fun todayLight() {
        capture(darkTheme = false, file = "src/test/screenshots/today-light.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun todayNarrowDark() {
        capture(darkTheme = true, file = "src/test/screenshots/today-narrow-dark.png")
    }

    @Test
    fun todayChecklistDark() {
        capture(
            darkTheme = true,
            file = "src/test/screenshots/today-checklist-dark.png",
            checklistState = FirstRunChecklistState(
                isLoading = false,
                hasCheckedIn = true,
                hasActivities = false,
                healthConnected = false,
            ),
        )
    }

    private fun capture(
        darkTheme: Boolean,
        file: String,
        checklistState: FirstRunChecklistState = FirstRunChecklistState(
            isLoading = false,
            dismissed = true,
        ),
    ) {
        val today = LocalDate.of(2026, 10, 30)
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                TodayScreen(
                    onDemoAction = {},
                    state = todayState(
                        checkIn = checkIn(today),
                        metrics = metrics(today),
                        profile = profile(today),
                        today = today,
                        // Fixed time so the greeting does not change the golden during the day.
                        now = LocalTime.of(8, 0),
                        plan = plan(today),
                        load = loadSnapshot(),
                        kmByDay = mapOf(today.minusDays(2) to 5.11, today to 6.2),
                        weeklyLoadKm = 11.31,
                    ),
                    checklistState = checklistState,
                )
            }
        }

        compose.onRoot().captureRoboImage(file)
    }

    private fun loadSnapshot() = LoadSnapshot(
        daily = emptyList(),
        ctlSeries = emptyList(),
        atlSeries = emptyList(),
        ctl = 24.0,
        atl = 31.0,
        tsb = -7.0,
        acuteAverage7d = 31.0,
        chronicAverage28d = 24.0,
        acwr = 1.29,
        monotony = 1.4,
        missingDays = 2,
        coverage = coverage(),
    )

    private fun coverage() = com.meucoach.core.domain.load.LoadCoverage(
        daysInWindow = 28,
        daysWithLoad = 12,
        daysWithoutActivity = 16,
        daysWithoutData = 0,
        lastActivityAt = java.time.Instant.parse("2026-10-29T10:00:00Z"),
        sourceFresh = true,
    )

    private fun plan(today: LocalDate): WeeklyPlan {
        val monday = today.minusDays(4)
        return WeeklyPlan(
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
                    date = monday.plusDays(2),
                    type = WorkoutType.QUALITY,
                    title = "Qualidade",
                    durationMin = 35,
                    rationale = "Qualidade.",
                    ruleIds = listOf("R-PLAN-02"),
                    evidenceIds = listOf("EV-DANIELS-2014"),
                ),
                PlannedSession(
                    date = monday.plusDays(5),
                    type = WorkoutType.LONG,
                    title = "Longão",
                    durationMin = 60,
                    rationale = "Longo.",
                    ruleIds = listOf("R-PLAN-03"),
                    evidenceIds = listOf("EV-PFITZINGER-2008"),
                ),
            ),
            weeklyMinutes = 135,
            longRunMinutes = 60,
            qualityCount = 1,
            rationale = "Sem progressão até existir histórico de conclusão.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-PLAN-03"),
            evidenceIds = listOf("EV-PFITZINGER-2008"),
        )
    }

    private fun checkIn(today: LocalDate) = DailyCheckIn(
        date = today,
        sleepQuality = 4,
        fatigue = 2,
        soreness = 1,
        stress = 2,
    )

    /** Enough nights to show sleep, HRV and resting heart rate in the readiness card. */
    private fun metrics(today: LocalDate) = (0..30).map { daysAgo ->
        DailyMetrics(
            date = today.minusDays(daysAgo.toLong()),
            sleepDurationMin = if (daysAgo == 0) 553 else 450,
            restingHeartRate = 52.0,
            hrvRmssdMs = 60.0,
            source = DailyMetricsSource.HEALTH_CONNECT,
        )
    }

    private fun profile(today: LocalDate) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(360),
        experience = Experience.INTERMEDIATE,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(mapOf(today.dayOfWeek to 45)),
    )
}
