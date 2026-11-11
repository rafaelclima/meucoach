package com.meucoach.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.Density
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.briefing.SessionBriefing
import com.meucoach.core.domain.briefing.SessionBriefingEngine
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.HeartRateTarget
import com.meucoach.core.domain.prescription.PaceTarget
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionClosureReason
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodayBriefingTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun `briefing copies the persisted prescription and opens from the planned workout card`() {
        val state = todayState(
            checkIn = checkIn(),
            profile = profile(),
            plan = plan(status = SessionStatus.PLANNED),
            load = null,
            today = today,
        )

        val prescription = state.prescription!!
        val briefing = state.briefing!!
        assertThat(briefing.date).isEqualTo(prescription.date)
        assertThat(briefing.title).isEqualTo(prescription.title)
        assertThat(briefing.durationMin).isEqualTo(prescription.prescribedDurationMin)
        assertThat(briefing.targets).isEqualTo(prescription.targets)
        assertThat(briefing.rationale).isEqualTo(prescription.rationale)
        assertThat(briefing.confidence).isEqualTo(prescription.confidence)

        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(onDemoAction = {}, state = state)
            }
        }

        compose.onNodeWithTag("today-list").performScrollToNode(hasText("Como correr hoje"))
        compose.onNodeWithTag("today-briefing-cta").performClick()

        compose.onNodeWithTag("briefing-sheet").assertIsDisplayed()
        compose.onNodeWithTag("briefing-title").assertTextEquals(prescription.title)
        compose.onNodeWithTag("briefing-focus").assertExists()
        compose.onNodeWithTag("briefing-target").assertExists()
        compose.onNodeWithTag("briefing-why").assertExists()
        compose.onNodeWithTag("briefing-how").assertExists()
        compose.onNodeWithTag("briefing-if-hard").assertExists()

        val tree = compose.onNodeWithTag("briefing-content").printToString()
        val focus = tree.indexOf("briefing-focus")
        val target = tree.indexOf("briefing-target")
        val why = tree.indexOf("briefing-why")
        val how = tree.indexOf("briefing-how")
        val hard = tree.indexOf("briefing-if-hard")
        assertThat(focus).isGreaterThanOrEqualTo(0)
        assertThat(focus).isLessThan(target)
        assertThat(target).isLessThan(why)
        assertThat(why).isLessThan(how)
        assertThat(how).isLessThan(hard)

        compose.onNodeWithTag("briefing-if-hard").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("R-", substring = true).assertDoesNotExist()
        compose.onNodeWithText("EV-", substring = true).assertDoesNotExist()

        compose.onNodeWithTag("briefing-close").performScrollTo().performClick()
        compose.onNodeWithTag("briefing-sheet").assertDoesNotExist()
    }

    @Test
    fun `hydration and nutrition sections show the approved copy without numbers`() {
        val state = todayState(
            checkIn = checkIn(),
            profile = profile(),
            plan = plan(status = SessionStatus.PLANNED),
            today = today,
        )

        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(onDemoAction = {}, state = state)
            }
        }

        compose.onNodeWithTag("today-list").performScrollToNode(hasText("Como correr hoje"))
        compose.onNodeWithTag("today-briefing-cta").performClick()

        compose.onNodeWithTag("briefing-hydration").assertExists()
        compose.onNodeWithTag("briefing-nutrition").assertExists()

        val tree = compose.onNodeWithTag("briefing-content").printToString()
        val hard = tree.indexOf("briefing-if-hard")
        val hydration = tree.indexOf("briefing-hydration")
        val nutrition = tree.indexOf("briefing-nutrition")
        val close = tree.indexOf("briefing-close")
        assertThat(hard).isGreaterThanOrEqualTo(0)
        assertThat(hard).isLessThan(hydration)
        assertThat(hydration).isLessThan(nutrition)
        assertThat(nutrition).isLessThan(close)

        compose.onNodeWithText("Comece o treino já hidratado. Nos minutos antes, beba água com calma.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag("briefing-hydration-note")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText(BriefingFuelContent.NUTRITION_REFERRAL)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `a done session has no briefing and keeps the persisted execution`() {
        val execution = TodayExecutionUi(
            duration = "45 min",
            distance = null,
            pace = null,
            rpe = 5,
            activityId = null,
            withoutRunData = true,
        )
        val state = todayState(
            checkIn = checkIn(),
            profile = profile(),
            plan = plan(status = SessionStatus.DONE, evidence = CompletionEvidence.MANUAL),
            today = today,
            todayExecution = execution,
        )

        assertThat(state.briefing).isNull()

        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(onDemoAction = {}, state = state)
            }
        }

        compose.onNodeWithTag("today-briefing-cta").assertDoesNotExist()
        compose.onNodeWithTag("today-list").performScrollToNode(hasTestTag("today-execution-summary"))
        compose.onNodeWithTag("today-execution-summary").assertIsDisplayed()
        compose.onNodeWithTag("today-done-badge").assertIsDisplayed()
    }

    @Test
    fun `a closed session has no briefing`() {
        val state = todayState(
            checkIn = checkIn(),
            profile = profile(),
            plan = plan(status = SessionStatus.CLOSED, closure = SessionClosureReason.USER_SKIPPED),
            today = today,
        )

        assertThat(state.briefing).isNull()

        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(onDemoAction = {}, state = state)
            }
        }

        compose.onNodeWithTag("today-briefing-cta").assertDoesNotExist()
        compose.onNodeWithTag("today-closed-badge").assertIsDisplayed()
    }

    @Test
    fun `hides the CTA when there is no prescription`() {
        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(onDemoAction = {}, state = demoTodayUiState(today).copy(briefing = null))
            }
        }

        compose.onNodeWithTag("today-briefing-cta").assertDoesNotExist()
    }

    @Test
    fun `without calibration shows only the existing RPE target`() {
        val state = todayState(
            checkIn = null,
            profile = profile(calibrated = false),
            today = today,
        )

        assertThat(state.briefing).isNotNull
        val briefing = state.briefing!!
        assertThat(briefing.targets.rpe).isEqualTo(4)
        assertThat(briefing.targets.pace).isNull()
        assertThat(briefing.targets.heartRate).isNull()

        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(onDemoAction = {}, state = state)
            }
        }

        compose.onNodeWithTag("today-list").performScrollToNode(hasText("Como correr hoje"))
        compose.onNodeWithTag("today-briefing-cta").performClick()

        compose.onNodeWithTag("briefing-target-rpe").assertIsDisplayed()
        compose.onNodeWithText("4/10").assertIsDisplayed()
        compose.onNodeWithTag("briefing-target-pace").assertDoesNotExist()
        compose.onNodeWithTag("briefing-target-heart-rate").assertDoesNotExist()
        compose
            .onNodeWithText("Use o esforço percebido 4/10 como guia.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `sheet shows a loading state`() {
        compose.setContent {
            McTheme(darkTheme = true) {
                SessionBriefingContent(
                    state = SessionBriefingSheetState.Loading,
                    onClose = {},
                )
            }
        }

        compose.onNodeWithTag("briefing-loading").assertIsDisplayed()
        compose.onNodeWithText("Preparando o briefing…").assertIsDisplayed()
    }

    @Test
    fun `sheet shows an empty state without a prescription`() {
        compose.setContent {
            McTheme(darkTheme = true) {
                SessionBriefingContent(
                    state = SessionBriefingSheetState.Empty,
                    onClose = {},
                )
            }
        }

        compose.onNodeWithTag("briefing-empty").assertIsDisplayed()
        compose.onNodeWithText("Ainda não há prescrição para hoje").assertIsDisplayed()
    }

    @Test
    fun `sheet shows an error state with a recoverable message`() {
        compose.setContent {
            McTheme(darkTheme = true) {
                SessionBriefingContent(
                    state = SessionBriefingSheetState.Failed("A prescrição de hoje não pôde ser lida."),
                    onClose = {},
                )
            }
        }

        compose.onNodeWithTag("briefing-error").assertIsDisplayed()
        compose.onNodeWithText("A prescrição de hoje não pôde ser lida.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun `content remains reachable at a 200 percent font scale on a narrow screen`() {
        val briefing = briefing()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 2f),
            ) {
                McTheme(darkTheme = true) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(McTheme.colors.background),
                    ) {
                        SessionBriefingContent(
                            state = SessionBriefingSheetState.Ready(briefing),
                            onClose = {},
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("briefing-how").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("briefing-if-hard").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("briefing-nutrition").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("briefing-close").performScrollTo().assertIsDisplayed()
    }

    private fun checkIn() = DailyCheckIn(
        date = today,
        sleepQuality = 4,
        fatigue = 2,
        soreness = 1,
        stress = 2,
    )

    private fun plan(
        status: SessionStatus,
        evidence: CompletionEvidence? = null,
        closure: SessionClosureReason? = null,
    ) = WeeklyPlan(
        weekStart = today.minusDays(today.dayOfWeek.value - 1L),
        sessions = listOf(
            PlannedSession(
                date = today,
                type = WorkoutType.EASY,
                title = "Fácil e conversável",
                durationMin = 45,
                rationale = "Base aeróbica em ritmo fácil.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = status,
                completionEvidence = evidence,
                closureReason = closure,
                closedAt = if (status == SessionStatus.CLOSED) Instant.parse("2026-11-10T10:00:00Z") else null,
            ),
        ),
        weeklyMinutes = 45,
        longRunMinutes = null,
        qualityCount = 0,
        rationale = "Semana conservadora.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-03"),
        evidenceIds = listOf("EV-SEILER-2010"),
        createdOn = today,
    )

    private fun profile(calibrated: Boolean = true) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = if (calibrated) 180 else null,
        thresholdPace = if (calibrated) Pace.fromSecondsPerKm(360) else null,
        experience = Experience.INTERMEDIATE,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(mapOf(DayOfWeek.TUESDAY to 45)),
    )

    private fun briefing(): SessionBriefing {
        val prescription = DailyPrescription(
            date = today,
            plannedSessionId = "plan-$today",
            plannedType = WorkoutType.EASY,
            plannedTitle = "Fácil e conversável",
            plannedDurationMin = 45,
            prescribedType = WorkoutType.EASY,
            title = "Fácil e conversável",
            prescribedDurationMin = 45,
            targets = PrescriptionTargets(
                pace = PaceTarget(Pace.of(7, 30), Pace.of(8, 24)),
                heartRate = HeartRateTarget(minBpm = 146, maxBpm = 161),
                zone = "Z2",
                distanceKm = 6.0,
            ),
            adjustment = PrescriptionAdjustment.KEEP,
            rationale = "Plano mantido: fácil, 45 min, em ritmo fácil e conversável.",
            ruleIds = listOf("R-PRESC-01", "R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
            confidence = DataConfidence.MEDIUM,
            engineVersion = "daily-prescription-v2",
            inputsHash = "inputs-hash",
        )
        return SessionBriefingEngine().brief(prescription, SessionStatus.PLANNED)!!
    }
}
