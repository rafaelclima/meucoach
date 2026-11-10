package com.meucoach.app.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.briefing.SessionBriefing
import com.meucoach.core.domain.briefing.SessionBriefingEngine
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.HeartRateTarget
import com.meucoach.core.domain.prescription.PaceTarget
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Pace
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
class SessionBriefingScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun briefingDark() {
        capture(darkTheme = true, file = "src/test/screenshots/today-briefing-dark.png")
    }

    @Test
    fun briefingLight() {
        capture(darkTheme = false, file = "src/test/screenshots/today-briefing-light.png")
    }

    private fun capture(darkTheme: Boolean, file: String) {
        compose.setContent {
            McTheme(darkTheme = darkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(McTheme.colors.surface),
                ) {
                    SessionBriefingContent(
                        state = SessionBriefingSheetState.Ready(briefing()),
                        onClose = {},
                    )
                }
            }
        }

        compose.onRoot().captureRoboImage(file)
    }

    private fun briefing(): SessionBriefing {
        val today = LocalDate.of(2026, 11, 10)
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
