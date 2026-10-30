package com.meucoach.app.ui.activitydetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.meucoach.core.data.repository.StoredActivity
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.insights.ActivityInsightsCalculator
import com.meucoach.core.model.ActivityLap
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActivityDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `shows loading`() {
        setContent { ActivityDetailContent(state = ActivityDetailUiState(), onBack = {}) }

        compose.onNodeWithTag("activity-detail-loading").assertIsDisplayed()
    }

    @Test
    fun `shows not found with a back action`() {
        var back = false
        setContent {
            ActivityDetailContent(
                state = ActivityDetailUiState(isLoading = false, notFound = true),
                onBack = { back = true },
            )
        }

        compose.onNodeWithText("Atividade não encontrada").assertIsDisplayed()
        compose.onNodeWithText("Voltar").performClick()
        assertThat(back).isTrue()
    }

    @Test
    fun `shows hero metrics and explainable sections`() {
        setContent {
            ActivityDetailContent(state = loadedState(), onBack = {})
        }

        compose.onNodeWithText("Corrida").assertIsDisplayed()
        compose.onNodeWithText("5,00").assertIsDisplayed()
        compose.onNodeWithContentDescription("Voltar").assertIsDisplayed()

        val list = compose.onNodeWithTag("activity-detail-list")
        list.performScrollToNode(hasText("Pace × FC × tempo"))
        compose.onNodeWithText("Pace × FC × tempo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Gráfico de pace e frequência cardíaca ao longo do tempo")
            .assertIsDisplayed()

        list.performScrollToNode(hasText("Splits"))
        compose.onNodeWithText("Splits").assertIsDisplayed()

        list.performScrollToNode(hasText("O que aprendemos"))
        compose.onNodeWithText("O que aprendemos").assertIsDisplayed()
        compose.onNodeWithText("R-ACT-01", substring = true).assertIsDisplayed()
    }

    @Test
    fun `renders in the light theme`() {
        compose.setContent {
            McTheme(darkTheme = false) {
                ActivityDetailContent(state = loadedState(), onBack = {})
            }
        }

        compose.onNodeWithText("Corrida").assertIsDisplayed()
    }

    private fun setContent(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            McTheme(darkTheme = true) {
                content()
            }
        }
    }

    private fun loadedState(): ActivityDetailUiState {
        val activity = storedActivity()
        return ActivityDetailUiState(
            isLoading = false,
            activity = activity,
            insights = ActivityInsightsCalculator().calculate(activity.imported.laps),
        )
    }

    private fun storedActivity(): StoredActivity {
        val start = Instant.parse("2026-10-24T08:17:10Z")
        val imported = ImportedActivity(
            source = ActivitySource.FIT,
            sport = Sport.RUNNING,
            startTime = start,
            durationSec = 1_800,
            distanceM = 5_000.0,
            averageHeartRate = 150,
            maxHeartRate = 170,
            averagePace = Pace.fromSecondsPerKm(360),
            averageCadenceSpm = 170,
            averagePowerW = 250,
            elevationGainM = 40.0,
            laps = listOf(
                ActivityLap(index = 0, durationSec = 900, distanceM = 2_500.0, averageHeartRate = 148),
                ActivityLap(index = 1, durationSec = 900, distanceM = 2_500.0, averageHeartRate = 152),
            ),
            samples = listOf(
                ActivitySample(offsetSec = 0, speedMps = 2.7, heartRate = 140, latitude = -5.79, longitude = -35.21),
                ActivitySample(offsetSec = 900, speedMps = 2.8, heartRate = 150, latitude = -5.78, longitude = -35.20),
                ActivitySample(
                    offsetSec = 1_800,
                    speedMps = 2.9,
                    heartRate = 160,
                    latitude = -5.77,
                    longitude = -35.19,
                ),
            ),
            parserVersion = "1.0.0",
        )
        return StoredActivity(
            id = "activity-1",
            imported = imported,
            importedAt = Instant.parse("2026-10-30T12:00:00Z"),
            rawFilePath = null,
            sourceFileSha256 = null,
            zoneId = "America/Sao_Paulo",
            localDate = LocalDate.parse("2026-10-24"),
        )
    }
}
