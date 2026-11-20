package com.meucoach.app.ui.coach

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.coach.CoachProgressSummary
import com.meucoach.core.domain.review.WeeklyAdherence
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewObservation
import com.meucoach.core.domain.review.WeeklyReviewObservationKind
import com.meucoach.core.domain.review.WeeklyReviewStatus
import com.meucoach.core.domain.review.WeeklyVolume
import com.meucoach.core.model.DataConfidence
import org.assertj.core.api.Assertions.assertThat
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
class CoachBubbleCardsTest {
    @get:Rule
    val compose = createComposeRule()

    private val longBody = "Reorganizei o resto da semana sem aumentar carga: o longo de 34 min já está feito " +
        "e os dois leves seguem leves. Faltam 25 min e 40 min de leve, então não consigo comparar o feito " +
        "com o planejado. Confere aqui se esses dois dias ainda cabem na sua semana?"

    @Test
    fun `explicacao vira bolhas curtas e bullets ficam em ver detalhes`() {
        setContent {
            CoachExplanationCard(
                state = CoachExplanationState.Ready(
                    title = "Semana reorganizada sem aumentar carga",
                    body = longBody,
                    bullets = listOf("Longo: 34 min, feito", "Leve: 25 min, a fazer"),
                ),
            )
        }

        val bubbles = compose.onAllNodesWithTag("coach-explanation-bubble").fetchSemanticsNodes()
        assertThat(bubbles.size).isGreaterThan(1)
        val texts = bubbles.map { node ->
            node.config[SemanticsProperties.Text].joinToString(" ") { it.text }
        }
        assertThat(texts).allMatch { it.length <= CoachBubbles.MAX_CHARS }

        compose.onNodeWithTag("coach-explanation-bullets").assertDoesNotExist()
        compose.onNodeWithTag("coach-explanation-toggle").performScrollTo().performClick()
        compose.onNodeWithText("• Longo: 34 min, feito").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `review colapsa os numeros e mantem as observacoes acessiveis`() {
        setContent {
            CoachReviewCard(
                review = review(),
                state = CoachReviewState.Ready(
                    title = "Semana passada: 3 de 4.",
                    body = "O longo saiu bem controlado, o forte ficou pra trás. A gente mantém.",
                    bullets = emptyList(),
                ),
            )
        }

        compose.onAllNodesWithTag("coach-review-bubble").onFirst().assertIsDisplayed()
        compose.onNodeWithTag("coach-review-numbers").assertDoesNotExist()

        compose.onNodeWithTag("coach-review-numbers-toggle").performScrollTo().performClick()

        compose.onNodeWithTag("coach-review-adherence").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-review-observation-0").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `progresso colapsa os agregados em ver numeros`() {
        setContent {
            CoachProgressCard(
                state = CoachProgressUiState.Ready(
                    summary = progressSummary(),
                    totalWeeks = 4,
                    profile = null,
                    today = LocalDate.of(2026, 11, 19),
                ),
                onRetry = {},
                onTrends = {},
            )
        }

        compose.onNodeWithTag("coach-progress-bubble").assertIsDisplayed()
        compose.onNodeWithTag("coach-progress-numbers").assertDoesNotExist()

        compose.onNodeWithTag("coach-progress-numbers-toggle").performScrollTo().performClick()

        compose.onNodeWithTag("coach-progress-numbers").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("3 de 4 sessões do plano (75%)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `bolhas suportam fonte 200 porcento`() {
        setContent(fontScale = 2f) {
            CoachExplanationCard(
                state = CoachExplanationState.Ready(
                    title = "Semana reorganizada",
                    body = longBody,
                    bullets = emptyList(),
                ),
            )
        }

        compose.onAllNodesWithTag("coach-explanation-bubble").fetchSemanticsNodes().forEach { node ->
            assertThat(node.config[SemanticsProperties.Text].joinToString(" ") { it.text })
                .isNotBlank()
        }
    }

    @Test
    fun `bolhas aparecem no tema claro`() {
        setContent(dark = false) {
            CoachExplanationCard(
                state = CoachExplanationState.Ready(
                    title = "Semana atual",
                    body = "Plano da semana.",
                    bullets = emptyList(),
                ),
            )
        }

        compose.onNodeWithTag("coach-explanation-card").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithTag("coach-explanation-bubble").onFirst().assertIsDisplayed()
    }

    private fun setContent(
        dark: Boolean = true,
        fontScale: Float = 1f,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale),
            ) {
                McTheme(darkTheme = dark) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                    ) {
                        content()
                    }
                }
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

    private fun progressSummary() = CoachProgressSummary(
        availableWeeks = 4,
        plannedSessions = 4,
        doneSessions = 3,
        adherencePercent = 75,
        plannedMinutes = 120,
        executedMinutes = 90,
        extraMinutes = 0,
        volumePercent = 75,
        avgReadiness = null,
        redDays = 0,
        avgSleepMinutes = null,
        shortNights = 0,
        easyPercent28d = null,
    )
}
