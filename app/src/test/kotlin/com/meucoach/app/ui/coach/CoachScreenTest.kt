package com.meucoach.app.ui.coach

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.review.WeeklyAdherence
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewObservation
import com.meucoach.core.domain.review.WeeklyReviewObservationKind
import com.meucoach.core.domain.review.WeeklyReviewStatus
import com.meucoach.core.domain.review.WeeklyVolume
import com.meucoach.core.model.DataConfidence
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoachScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `mostra a narrativa e o resumo deterministico da semana`() {
        setContent(
            review = review(),
            reviewState = CoachReviewState.Ready(
                title = "Semana passada",
                body = "Você cumpriu a maior parte do que planejou.",
                bullets = listOf("Mantenha o ritmo leve"),
            ),
        )

        compose.onNodeWithTag("coach-review-card").assertIsDisplayed()
        compose.onNodeWithText("REVISÃO DA SEMANA PASSADA").assertIsDisplayed()
        compose.onNodeWithText("Você cumpriu a maior parte do que planejou.").assertIsDisplayed()
        compose.onNodeWithText("3 de 4 sessões concluídas").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("75%").assertExists()
        compose.onNodeWithText("Volume: 2h planejados", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `fallback mantem o resumo do motor com as observacoes`() {
        setContent(
            review = review(),
            reviewState = CoachReviewState.Fallback("O Coach não respondeu."),
        )

        compose.onNodeWithText("O que a semana mostrou").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3 de 4 sessões planejadas foram concluídas.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Você cumpriu a maior parte do que planejou.").assertDoesNotExist()
    }

    @Test
    fun `semana sem dados explica a ausencia sem inventar`() {
        setContent(
            review = WeeklyReview(
                weekStart = LocalDate.of(2026, 11, 9),
                weekEnd = LocalDate.of(2026, 11, 15),
                status = WeeklyReviewStatus.EMPTY_NO_DATA,
                adherence = null,
                volume = null,
                quality = null,
                intensity = null,
                intensitySessions = 0,
                readiness = null,
                sleep = null,
                observations = emptyList(),
                ruleIds = emptyList(),
                evidenceIds = emptyList(),
                confidence = DataConfidence.LOW,
            ),
            reviewState = CoachReviewState.Unavailable,
        )

        compose.onNodeWithText(
            "Não há plano, corridas ou check-ins registrados nessa semana.",
        ).assertIsDisplayed()
    }

    private fun setContent(review: WeeklyReview, reviewState: CoachReviewState) {
        compose.setContent {
            McTheme(darkTheme = true) {
                CoachScreen(
                    consented = true,
                    showConsent = false,
                    explanation = CoachExplanationState.Ready(
                        title = "Semana atual",
                        body = "Plano da semana.",
                        bullets = emptyList(),
                    ),
                    onGrant = {},
                    onDismiss = {},
                    onRevoke = {},
                    onRetry = {},
                    review = review,
                    reviewState = reviewState,
                )
            }
        }
    }

    private fun review() = WeeklyReview(
        weekStart = LocalDate.of(2026, 11, 9),
        weekEnd = LocalDate.of(2026, 11, 15),
        status = WeeklyReviewStatus.AVAILABLE,
        adherence = WeeklyAdherence(
            planned = 4,
            done = 3,
            verified = 2,
            manual = 1,
            legacy = 0,
            skipped = 1,
        ),
        volume = WeeklyVolume(plannedMin = 120, executedMin = 90, percentOfPlan = 75, extraMin = 10),
        quality = null,
        intensity = null,
        intensitySessions = 0,
        readiness = null,
        sleep = null,
        observations = listOf(
            WeeklyReviewObservation(
                kind = WeeklyReviewObservationKind.ADHERENCE_PARTIAL,
                ruleId = "R-REVIEW-01",
                evidenceIds = emptyList(),
                confidence = DataConfidence.HIGH,
            ),
        ),
        ruleIds = listOf("R-REVIEW-01"),
        evidenceIds = emptyList(),
        confidence = DataConfidence.HIGH,
    )
}
