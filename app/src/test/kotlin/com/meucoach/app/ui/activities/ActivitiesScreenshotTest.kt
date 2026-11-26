package com.meucoach.app.ui.activities

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.Distance
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActivitiesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun activitiesNarrow200Dark() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                McTheme(darkTheme = true) {
                    ActivitiesContent(
                        state = ActivitiesUiState(
                            isLoading = false,
                            activities = listOf(activity()),
                        ),
                        onImportClick = {},
                        onMessageShown = {},
                        onActivityClick = {},
                    )
                }
            }
        }

        compose.onNodeWithTag("activities-list").performScrollToIndex(3)
        compose.onRoot().captureRoboImage("src/test/screenshots/activities-narrow-200-dark.png")
    }

    private fun activity() = ActivitySummary(
        id = "activity-1",
        source = ActivitySource.GPX,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-01-02T10:00:00Z"),
        durationSec = 2_100,
        distance = Distance(5_000.0),
        averageHeartRate = 145,
        maxHeartRate = 160,
        averagePace = Pace.of(minutes = 7, seconds = 0),
        averageCadenceSpm = 174,
    )
}
