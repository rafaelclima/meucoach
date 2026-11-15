package com.meucoach.app.ui.level

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.app.ui.profile.ProfileContent
import com.meucoach.app.ui.profile.ProfileUiState
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LevelGraduationProfileTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shows the level card on the profile and accepts it`() {
        var accepted = false
        compose.setContent {
            McTheme(darkTheme = true) {
                ProfileContent(
                    state = ProfileUiState(isLoading = false, profile = profile()),
                    onEdit = {},
                    onCalibrate = {},
                    onOpenHealth = {},
                    onManageData = {},
                    onShowTutorial = {},
                    levelGraduation = LevelGraduationUiState(suggestion = suggestion()),
                    onLevelAccept = { accepted = true },
                )
            }
        }

        compose.onNodeWithTag("profile-list")
            .performScrollToNode(hasText("PRONTO PARA SUBIR DE NÍVEL?", substring = false))
        compose.onNodeWithText("De Iniciante para Intermediário").assertIsDisplayed()
        compose.onNodeWithTag("level-graduation-accept").performClick()

        assertThat(accepted).isTrue()
    }

    @Test
    fun `hides the level section when there is no suggestion or message`() {
        compose.setContent {
            McTheme(darkTheme = true) {
                ProfileContent(
                    state = ProfileUiState(isLoading = false, profile = profile()),
                    onEdit = {},
                    onCalibrate = {},
                    onOpenHealth = {},
                    onManageData = {},
                    onShowTutorial = {},
                )
            }
        }

        compose.onNodeWithTag("level-graduation-card").assertDoesNotExist()
        compose.onNodeWithTag("level-graduation-message-card").assertDoesNotExist()
    }

    @Test
    fun `renders the level card in the light theme`() {
        compose.setContent {
            McTheme(darkTheme = false) {
                ProfileContent(
                    state = ProfileUiState(isLoading = false, profile = profile()),
                    onEdit = {},
                    onCalibrate = {},
                    onOpenHealth = {},
                    onManageData = {},
                    onShowTutorial = {},
                    levelGraduation = LevelGraduationUiState(suggestion = suggestion()),
                )
            }
        }

        compose.onNodeWithTag("profile-list")
            .performScrollToNode(hasText("PRONTO PARA SUBIR DE NÍVEL?", substring = false))
        compose.onNodeWithTag("level-graduation-card").assertIsDisplayed()
    }

    private fun suggestion() = LevelGraduationSuggestionUi(
        from = Experience.BEGINNER,
        to = Experience.INTERMEDIATE,
        reasons = listOf("4 de 6 semanas com pelo menos 80% dos treinos concluídos com evidência forte"),
        ruleIds = listOf("R-LVL-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        confidence = DataConfidence.MEDIUM,
        inputsHash = "hash-a",
    )

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(
            mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 50, DayOfWeek.SATURDAY to 90),
        ),
    )
}
