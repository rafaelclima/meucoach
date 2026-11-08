package com.meucoach.app.ui.replan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.app.application.ReplanChangeKind
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.replan.ReplanTrigger
import com.meucoach.core.model.DataConfidence
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
class ReplanScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun replanDark() {
        capture(darkTheme = true, file = "src/test/screenshots/replan-dark.png")
    }

    @Test
    fun replanLight() {
        capture(darkTheme = false, file = "src/test/screenshots/replan-light.png")
    }

    private fun capture(darkTheme: Boolean, file: String) {
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(McTheme.colors.background)
                        .padding(20.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ReplanSuggestionCard(
                            state = ReplanUiState(
                                planVersion = 1,
                                suggestion = ReplanSuggestionUi(
                                    versionFrom = 1,
                                    versionTo = 2,
                                    trigger = ReplanTrigger.MISSED_SESSIONS,
                                    headline = "Dá para ajustar o resto da semana",
                                    body = "2 treinos desta semana não aconteceram. Posso reorganizar só " +
                                        "os próximos dias, sem repor carga e sem mexer no que já passou.",
                                    changes = listOf(
                                        ReplanChangeUi(
                                            date = LocalDate.of(2026, 11, 6),
                                            kind = ReplanChangeKind.MODIFIED,
                                            kindLabel = "Muda",
                                            previousLabel = "Tempo · 30 min",
                                            nextLabel = "Fácil e conversável · 30 min",
                                        ),
                                        ReplanChangeUi(
                                            date = LocalDate.of(2026, 11, 8),
                                            kind = ReplanChangeKind.REMOVED,
                                            kindLabel = "Sai",
                                            previousLabel = "Longão · 60 min",
                                            nextLabel = null,
                                        ),
                                    ),
                                    confidence = DataConfidence.MEDIUM,
                                ),
                            ),
                            onAccept = {},
                            onDecline = {},
                            onSuppress = {},
                        )
                    }
                }
            }
        }

        compose.onRoot().captureRoboImage(file)
    }
}
