package com.meucoach.app.ui.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.core.data.health.HealthConnectStatus
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.GoalType
import com.meucoach.core.model.Sex
import com.meucoach.core.model.TrainingGoal
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
class ProfileScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shows an empty state with a setup action`() {
        var edit = false
        setContent(state = ProfileUiState(isLoading = false, profile = null), onEdit = { edit = true })

        compose.onNodeWithText("Perfil ainda não configurado").assertIsDisplayed()
        compose.onNodeWithText("Configurar perfil").performClick()

        assertThat(edit).isTrue()
    }

    @Test
    fun `shows the profile summary and opens editing`() {
        var edit = false
        setContent(state = ProfileUiState(isLoading = false, profile = profile()), onEdit = { edit = true })

        compose.onNodeWithText("Seu perfil").assertIsDisplayed()
        compose.onNodeWithText("38 anos").assertIsDisplayed()
        compose.onNodeWithText("Masculino").assertIsDisplayed()

        compose.onNodeWithTag("profile-list").performScrollToNode(hasText("Editar perfil"))
        compose.onNodeWithText("Editar perfil").performClick()

        assertThat(edit).isTrue()
    }

    @Test
    fun `opens data management from the data section`() {
        var manageData = false
        compose.setContent {
            McTheme(darkTheme = true) {
                ProfileContent(
                    state = ProfileUiState(isLoading = false, profile = profile()),
                    onEdit = {},
                    onCalibrate = {},
                    onOpenHealth = {},
                    onManageData = { manageData = true },
                    onShowTutorial = {},
                )
            }
        }

        val list = compose.onNodeWithTag("profile-list")
        list.performScrollToNode(hasText("Exportar ou apagar dados"))
        compose.onNodeWithText("Exportar ou apagar dados").performClick()

        assertThat(manageData).isTrue()
    }

    @Test
    fun `shows the compact health summary and opens the health tab`() {
        var openedHealth = false
        compose.setContent {
            McTheme(darkTheme = true) {
                ProfileContent(
                    state = ProfileUiState(
                        isLoading = false,
                        profile = profile(),
                        health = ProfileHealthSummary(
                            status = HealthConnectStatus.AVAILABLE,
                            hasPermissions = true,
                            storedDays = 47,
                            lastSyncedAt = Instant.now(),
                        ),
                    ),
                    onEdit = {},
                    onCalibrate = {},
                    onOpenHealth = { openedHealth = true },
                    onManageData = {},
                    onShowTutorial = {},
                )
            }
        }

        val list = compose.onNodeWithTag("profile-list")
        list.performScrollToNode(hasText("Sono, recuperação e oxigenação"))
        compose.onNodeWithText("Sono, recuperação e oxigenação").assertIsDisplayed()
        compose.onNodeWithText("Conectado · 47 dias").assertIsDisplayed()
        compose.onNodeWithTag("profile-open-health").performClick()

        assertThat(openedHealth).isTrue()
    }

    @Test
    fun `invites the user to connect when health connect is not authorized`() {
        compose.setContent {
            McTheme(darkTheme = true) {
                ProfileContent(
                    state = ProfileUiState(
                        isLoading = false,
                        profile = profile(),
                        health = ProfileHealthSummary(
                            status = HealthConnectStatus.AVAILABLE,
                            hasPermissions = false,
                        ),
                    ),
                    onEdit = {},
                    onCalibrate = {},
                    onOpenHealth = {},
                    onManageData = {},
                    onShowTutorial = {},
                )
            }
        }

        compose.onNodeWithTag("profile-list")
            .performScrollToNode(hasText("Ainda não conectado"))
        compose.onNodeWithText("Ainda não conectado").assertIsDisplayed()
        compose.onNodeWithTag("profile-open-health").assertIsDisplayed()
    }

    @Test
    fun `offers notification priming in the help section`() {
        setContent(state = ProfileUiState(isLoading = false, profile = profile()), onEdit = {})

        val list = compose.onNodeWithTag("profile-list")
        list.performScrollToNode(hasText("Lembretes"))
        compose.onNodeWithText("Lembretes").assertIsDisplayed()
        compose.onNodeWithText("Um lembrete por dia", substring = true).assertIsDisplayed()
    }

    @Test
    fun `offers the tutorial replay and reports it`() {
        var showTutorial = false
        compose.setContent {
            McTheme(darkTheme = true) {
                ProfileContent(
                    state = ProfileUiState(isLoading = false, profile = profile()),
                    onEdit = {},
                    onCalibrate = {},
                    onOpenHealth = {},
                    onManageData = {},
                    onShowTutorial = { showTutorial = true },
                )
            }
        }

        val list = compose.onNodeWithTag("profile-list")
        list.performScrollToNode(hasText("Rever tutorial"))
        compose.onNodeWithText("Rever tutorial").performClick()

        assertThat(showTutorial).isTrue()
    }

    @Test
    fun `renders in the light theme`() {
        compose.setContent {
            McTheme(darkTheme = false) {
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

        compose.onNodeWithText("Seu perfil").assertIsDisplayed()
    }

    private fun setContent(state: ProfileUiState, onEdit: () -> Unit) {
        compose.setContent {
            McTheme(darkTheme = true) {
                ProfileContent(
                    state = state,
                    onEdit = onEdit,
                    onCalibrate = {},
                    onOpenHealth = {},
                    onManageData = {},
                    onShowTutorial = {},
                )
            }
        }
    }

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        heightCm = 174.0,
        weightKg = 97.0,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(
            mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 50, DayOfWeek.SATURDAY to 90),
        ),
        goal = TrainingGoal(
            type = GoalType.FIVE_K,
            targetDate = LocalDate.of(2027, 3, 14),
            targetDistanceM = 5_000.0,
            targetTimeSec = 1_800,
        ),
        city = "Natal",
    )
}
