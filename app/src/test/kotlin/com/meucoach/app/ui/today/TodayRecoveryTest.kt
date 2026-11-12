package com.meucoach.app.ui.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.prescription.CompletedRace
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.RecoveryWindow
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.RaceDistance
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
import java.time.LocalDate

/** ADR 0037: the Hoje state exposes and respects the post-race recovery window. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TodayRecoveryTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 2) // Monday

    @Test
    fun `exposes the recovery day and downgrades today's quality`() {
        val recovery = RecoveryWindow(
            CompletedRace("race-1", RaceDistance.TEN_K, today.minusDays(1)),
        )

        val state = todayState(
            checkIn = null,
            profile = profile(),
            today = today,
            plan = qualityPlan(today),
            recovery = recovery,
        )

        assertThat(state.recovery).isNotNull()
        assertThat(state.recovery?.dayOfWindow).isEqualTo(1)
        assertThat(state.recovery?.totalDays).isEqualTo(3)
        assertThat(state.recovery?.until).isEqualTo(today.plusDays(2))
        assertThat(state.prescription?.prescribedType).isEqualTo(WorkoutType.EASY)
        assertThat(state.prescription?.ruleIds).contains("R-RACE-04")
    }

    @Test
    fun `does not expose recovery after the window ends`() {
        val recovery = RecoveryWindow(
            CompletedRace("race-1", RaceDistance.FIVE_K, today.minusDays(5)),
        )

        val state = todayState(
            checkIn = null,
            profile = profile(),
            today = today,
            plan = qualityPlan(today),
            recovery = recovery,
        )

        assertThat(state.recovery).isNull()
        assertThat(state.prescription?.ruleIds).doesNotContain("R-RACE-04")
    }

    @Test
    fun `shows the recovery line in the today card`() {
        val recovery = RecoveryWindow(
            CompletedRace("race-1", RaceDistance.TEN_K, today.minusDays(1)),
        )
        val state = todayState(
            checkIn = null,
            profile = profile(),
            today = today,
            plan = qualityPlan(today),
            recovery = recovery,
        )

        compose.setContent {
            McTheme(darkTheme = true) {
                TodayScreen(onDemoAction = {}, state = state)
            }
        }

        compose.onNodeWithTag("today-list").performScrollToNode(hasTestTag("today-recovery"))
        compose.onNodeWithText("Recuperação após a prova · dia 1 de 3 · até 04/11").assertIsDisplayed()
    }

    private fun qualityPlan(date: LocalDate) = WeeklyPlan(
        weekStart = date,
        sessions = listOf(
            PlannedSession(
                date = date,
                type = WorkoutType.QUALITY,
                title = "Tempo",
                durationMin = 40,
                rationale = "20 min em esforço moderado.",
                ruleIds = listOf("R-PLAN-02"),
                evidenceIds = listOf("EV-SEILER-2010"),
            ),
        ),
        weeklyMinutes = 40,
        longRunMinutes = null,
        qualityCount = 1,
        rationale = "Plano da semana.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
    )

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        experience = Experience.INTERMEDIATE,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(mapOf(DayOfWeek.MONDAY to 40)),
    )
}
