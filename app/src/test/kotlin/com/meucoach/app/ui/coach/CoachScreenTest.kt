package com.meucoach.app.ui.coach

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.meucoach.app.application.ReplanChangeKind
import com.meucoach.app.coach.CoachNextContext
import com.meucoach.app.ui.replan.LessTimeChange
import com.meucoach.app.ui.replan.LessTimeDayUi
import com.meucoach.app.ui.replan.MissedSessionUi
import com.meucoach.app.ui.replan.ReplanChangeUi
import com.meucoach.app.ui.replan.ReplanSuggestionUi
import com.meucoach.app.ui.replan.ReplanUiState
import com.meucoach.app.ui.replan.ReplanVersionChangeUi
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.replan.ReplanCause
import com.meucoach.core.domain.replan.ReplanTrigger
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

        compose.onNodeWithTag("coach-review-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("REVISÃO DA SEMANA PASSADA").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Você cumpriu a maior parte do que planejou.").performScrollTo().assertIsDisplayed()
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
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `acoes rapidas separam pedido simples, treino perdido e menos tempo`() {
        var plain = 0
        var missed = 0
        var lessTime = 0
        setContent(
            onReplanRequest = { plain++ },
            onReplanRequestMissed = { missed++ },
            onReplanRequestLessTime = { lessTime++ },
        )

        compose.onNodeWithTag("coach-replan-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-replan-request").performScrollTo().performClick()
        compose.onNodeWithTag("coach-replan-missed").performScrollTo().performClick()
        compose.onNodeWithTag("coach-replan-less-time").performScrollTo().performClick()

        assertThat(plain).isEqualTo(1)
        assertThat(missed).isEqualTo(1)
        assertThat(lessTime).isEqualTo(1)
    }

    @Test
    fun `perdi um treino abre o picker e a selecao chama o callback`() {
        val monday = LocalDate.of(2026, 11, 2)
        var selected: LocalDate? = null
        setContent(
            replan = ReplanUiState(
                missedSessions = listOf(
                    MissedSessionUi(date = monday, title = "Fácil e conversável", durationMin = 30),
                ),
            ),
            onReplanMissedSelected = { selected = it },
        )

        compose.onNodeWithTag("coach-missed-sheet").assertIsDisplayed()
        compose.onNodeWithText("Seg 02/11 · Fácil e conversável · 30 min").assertIsDisplayed()
        compose.onNodeWithTag("coach-missed-2026-11-02").performClick()

        assertThat(selected).isEqualTo(monday)
    }

    @Test
    fun `picker de treino perdido vazio explica a ausencia`() {
        setContent(replan = ReplanUiState(missedSessions = emptyList()))

        compose.onNodeWithTag("coach-missed-empty").assertIsDisplayed()
        compose.onNodeWithText("Não encontrei treinos perdidos nesta semana.").assertIsDisplayed()
    }

    @Test
    fun `menos tempo valida os minutos localmente antes de avaliar`() {
        val friday = LocalDate.of(2026, 11, 6)
        var reviewed: List<LessTimeChange>? = null
        setContent(
            replan = ReplanUiState(
                lessTimeDays = listOf(LessTimeDayUi(date = friday, currentMinutes = 40)),
            ),
            onReplanLessTimeSelected = { reviewed = it },
        )

        compose.onNodeWithTag("coach-less-time-sheet").assertIsDisplayed()
        compose.onNodeWithTag("coach-less-time-minutes").performTextInput("40")
        compose.onNodeWithTag("coach-less-time-review").performClick()

        compose.onNodeWithTag("coach-less-time-error").assertIsDisplayed()
        assertThat(reviewed).isNull()

        compose.onNodeWithTag("coach-less-time-minutes").performTextClearance()
        compose.onNodeWithTag("coach-less-time-minutes").performTextInput("25")
        compose.onNodeWithTag("coach-less-time-review").performClick()

        assertThat(reviewed).containsExactly(LessTimeChange(friday, 25))
    }

    @Test
    fun `menos tempo permite remover mais de um dia`() {
        val thursday = LocalDate.of(2026, 11, 5)
        val friday = LocalDate.of(2026, 11, 6)
        var reviewed: List<LessTimeChange>? = null
        setContent(
            replan = ReplanUiState(
                lessTimeDays = listOf(
                    LessTimeDayUi(date = thursday, currentMinutes = 25),
                    LessTimeDayUi(date = friday, currentMinutes = 30),
                ),
            ),
            onReplanLessTimeSelected = { reviewed = it },
        )

        compose.onNodeWithTag("coach-less-time-day-$friday").performClick()
        compose.onNodeWithTag("coach-less-time-minutes").performTextInput("0")
        compose.onNodeWithTag("coach-less-time-review").performClick()

        assertThat(reviewed).containsExactly(
            LessTimeChange(thursday, 0),
            LessTimeChange(friday, 0),
        )
    }

    @Test
    fun `sugestao mostra o pedido explicito do usuario`() {
        setContent(
            replan = ReplanUiState(
                planVersion = 1,
                suggestion = ReplanSuggestionUi(
                    versionFrom = 1,
                    versionTo = 2,
                    trigger = ReplanTrigger.USER_REQUEST,
                    causes = listOf(ReplanCause.TEMPORARY_AVAILABILITY),
                    headline = "Cabe no tempo que você tem?",
                    body = "Só os próximos dias mudam para caber nesse tempo.",
                    changes = emptyList(),
                    confidence = DataConfidence.MEDIUM,
                    requestNote = "Você indicou 20 min para sexta-feira (06/11).",
                ),
            ),
        )

        compose.onNodeWithText("Você indicou 20 min para sexta-feira (06/11).").assertIsDisplayed()
    }

    @Test
    fun `sugestao abre o sheet com diff e selo de versao`() {
        var accepted = false
        setContent(
            replan = ReplanUiState(
                planVersion = 1,
                suggestion = ReplanSuggestionUi(
                    versionFrom = 1,
                    versionTo = 2,
                    trigger = ReplanTrigger.USER_REQUEST,
                    causes = listOf(ReplanCause.USER_REQUEST),
                    headline = "Reorganizar o resto da semana?",
                    body = "O que já passou e o treino de hoje ficam como estão.",
                    changes = listOf(
                        ReplanChangeUi(
                            date = LocalDate.of(2026, 11, 6),
                            kind = ReplanChangeKind.ADDED,
                            kindLabel = "Novo",
                            previousLabel = null,
                            nextLabel = "Fácil e conversável · 25 min",
                        ),
                    ),
                    confidence = DataConfidence.MEDIUM,
                ),
            ),
            onReplanAccept = { accepted = true },
        )

        compose.onNodeWithTag("replan-suggestion-sheet").assertIsDisplayed()
        compose.onNodeWithText("v1 → v2").assertIsDisplayed()
        compose.onNodeWithText("Fácil e conversável · 25 min").assertIsDisplayed()
        compose.onNodeWithTag("replan-accept").performScrollTo().performClick()

        assertThat(accepted).isTrue()
    }

    @Test
    fun `desfazer aparece depois de aplicar e tambem no sheet do diff`() {
        var undone = 0
        setContent(
            replan = ReplanUiState(
                planVersion = 2,
                canUndo = true,
                message = "Semana ajustada. O que já passou e o treino de hoje não mudaram.",
                lastChange = ReplanVersionChangeUi(
                    fromVersion = 1,
                    toVersion = 2,
                    reason = "Revisão pedida por você.",
                    changes = listOf(
                        ReplanChangeUi(
                            date = LocalDate.of(2026, 11, 6),
                            kind = ReplanChangeKind.MODIFIED,
                            kindLabel = "Muda",
                            previousLabel = "Fácil e conversável · 30 min",
                            nextLabel = "Fácil e conversável · 25 min",
                        ),
                    ),
                ),
            ),
            onReplanUndo = { undone++ },
        )

        compose.onNodeWithTag("replan-undo").performScrollTo().performClick()
        assertThat(undone).isEqualTo(1)

        compose.onNodeWithTag("replan-show-changes").performScrollTo().performClick()
        compose.onNodeWithTag("replan-changes-sheet").assertIsDisplayed()
        compose.onNodeWithTag("replan-sheet-undo").performClick()
        assertThat(undone).isEqualTo(2)
    }

    @Test
    fun `sem plano mostra caminho acionavel em vez de em breve`() {
        setContent(explanation = CoachExplanationState.Unavailable)

        compose.onNodeWithTag("coach-empty-path").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Monte seu plano para começar").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "Complete perfil e disponibilidade, importe um treino ou faça a calibração.",
            substring = true,
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `fallback exibe o motivo com tag propria`() {
        setContent(
            explanation = CoachExplanationState.Fallback(
                reason = "Resposta contém pace, que só o motor pode prescrever",
                planRationale = "Plano da semana.",
            ),
        )

        compose.onNodeWithTag("coach-fallback-reason").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "Resposta contém pace, que só o motor pode prescrever",
            substring = true,
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `replan vem antes da explicacao`() {
        setContent()

        val replanY = compose.onNodeWithTag("coach-replan-card").fetchSemanticsNode().positionInRoot.y
        val explanationY =
            compose.onNodeWithTag("coach-explanation-card").fetchSemanticsNode().positionInRoot.y

        assertThat(replanY).isLessThan(explanationY)
    }

    @Test
    fun `proximo treino aparece no topo com o planejado`() {
        setContent(
            next = CoachNextUiState.Ready(
                CoachNextContext(
                    session = PlannedSession(
                        date = LocalDate.of(2026, 11, 17),
                        type = WorkoutType.EASY,
                        title = "Fácil",
                        durationMin = 30,
                        rationale = "Base.",
                        ruleIds = listOf("R-ZONE-01"),
                        evidenceIds = listOf("EV-SEILER-2010"),
                        status = SessionStatus.PLANNED,
                    ),
                    isToday = false,
                    today = LocalDate.of(2026, 11, 16),
                    prescription = null,
                    raceEvent = null,
                ),
            ),
        )

        compose.onNodeWithTag("coach-next-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("PRÓXIMO TREINO").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Amanhã · Fácil · 30 min").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `dia de prova nao mostra treino no proximo`() {
        setContent(
            next = CoachNextUiState.Ready(
                CoachNextContext(
                    session = PlannedSession(
                        date = LocalDate.of(2026, 11, 17),
                        type = WorkoutType.EASY,
                        title = "Fácil",
                        durationMin = 30,
                        rationale = "Base.",
                        ruleIds = listOf("R-ZONE-01"),
                        evidenceIds = listOf("EV-SEILER-2010"),
                        status = SessionStatus.PLANNED,
                    ),
                    isToday = false,
                    today = LocalDate.of(2026, 11, 16),
                    prescription = null,
                    raceEvent = com.meucoach.core.model.RaceEvent(
                        id = "race-1",
                        distance = com.meucoach.core.model.RaceDistance.FIVE_K,
                        targetDate = LocalDate.of(2026, 11, 17),
                        createdAt = java.time.Instant.parse("2026-11-16T12:00:00Z"),
                        updatedAt = java.time.Instant.parse("2026-11-16T12:00:00Z"),
                    ),
                ),
            ),
        )

        compose.onNodeWithTag("coach-next-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dia de prova").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `erro do proximo treino tem tentar de novo`() {
        var retried = 0
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
                    next = CoachNextUiState.Error("Sem conexão."),
                    onNextRetry = { retried++ },
                )
            }
        }

        compose.onNodeWithTag("coach-next-retry").performScrollTo().performClick()
        assertThat(retried).isEqualTo(1)
    }

    @Test
    fun `proximo com check-in pendente oferece acao`() {
        var checkedIn = 0
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
                    next = CoachNextUiState.Ready(blockedContext()),
                    onNextCheckIn = { checkedIn++ },
                )
            }
        }

        compose.onNodeWithTag("coach-next-checkin").performScrollTo().performClick()
        assertThat(checkedIn).isEqualTo(1)
    }

    @Test
    fun `proximo leva ao Hoje`() {
        var today = 0
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
                    next = CoachNextUiState.Ready(
                        CoachNextContext(
                            session = PlannedSession(
                                date = LocalDate.of(2026, 11, 17),
                                type = WorkoutType.EASY,
                                title = "Fácil",
                                durationMin = 30,
                                rationale = "Base.",
                                ruleIds = listOf("R-ZONE-01"),
                                evidenceIds = listOf("EV-SEILER-2010"),
                                status = SessionStatus.PLANNED,
                            ),
                            isToday = false,
                            today = LocalDate.of(2026, 11, 16),
                            prescription = null,
                            raceEvent = null,
                        ),
                    ),
                    onNextToday = { today++ },
                )
            }
        }

        compose.onNodeWithTag("coach-next-today").performScrollTo().performClick()
        assertThat(today).isEqualTo(1)
    }

    private fun blockedContext() = CoachNextContext(
        session = PlannedSession(
            date = LocalDate.of(2026, 11, 16),
            type = WorkoutType.QUALITY,
            title = "Ritmo forte",
            durationMin = 35,
            rationale = "Estímulo.",
            ruleIds = listOf("R-PER-03"),
            evidenceIds = listOf("EV-SEILER-2010"),
            status = SessionStatus.PLANNED,
        ),
        isToday = true,
        today = LocalDate.of(2026, 11, 16),
        prescription = com.meucoach.core.domain.prescription.DailyPrescription(
            date = LocalDate.of(2026, 11, 16),
            plannedSessionId = null,
            plannedType = WorkoutType.QUALITY,
            plannedTitle = "Ritmo forte",
            plannedDurationMin = 35,
            prescribedType = WorkoutType.EASY,
            title = "Fácil",
            prescribedDurationMin = 25,
            targets = com.meucoach.core.domain.prescription.PrescriptionTargets(
                primary = com.meucoach.core.domain.prescription.TargetKind.RPE,
            ),
            adjustment = com.meucoach.core.domain.prescription.PrescriptionAdjustment.DOWNGRADE,
            rationale = "Dia leve.",
            ruleIds = listOf("R-PRESC-02"),
            evidenceIds = listOf("EV-SAW-2016"),
            confidence = com.meucoach.core.model.DataConfidence.MEDIUM,
            engineVersion = "daily-prescription-v6",
            inputsHash = "hash",
            qualityBlockReason = com.meucoach.core.domain.prescription.QualityBlockReason.SUBJECTIVE_MISSING,
            decisionDetail = "check-in de hoje ausente",
            unlockHint = "Faça o check-in de hoje para liberar o treino forte.",
        ),
        raceEvent = null,
    )

    private fun setContent(
        explanation: CoachExplanationState = CoachExplanationState.Ready(
            title = "Semana atual",
            body = "Plano da semana.",
            bullets = emptyList(),
        ),
        next: CoachNextUiState = CoachNextUiState.Hidden,
        review: WeeklyReview? = null,
        reviewState: CoachReviewState = CoachReviewState.Unavailable,
        replan: ReplanUiState = ReplanUiState(),
        onReplanRequest: () -> Unit = {},
        onReplanRequestMissed: () -> Unit = {},
        onReplanRequestLessTime: () -> Unit = {},
        onReplanMissedSelected: (LocalDate) -> Unit = {},
        onReplanLessTimeSelected: (List<LessTimeChange>) -> Unit = {},
        onReplanAccept: () -> Unit = {},
        onReplanUndo: () -> Unit = {},
    ) {
        compose.setContent {
            McTheme(darkTheme = true) {
                CoachScreen(
                    consented = true,
                    showConsent = false,
                    explanation = explanation,
                    onGrant = {},
                    onDismiss = {},
                    onRevoke = {},
                    onRetry = {},
                    next = next,
                    review = review,
                    reviewState = reviewState,
                    replan = replan,
                    onReplanRequest = onReplanRequest,
                    onReplanRequestMissed = onReplanRequestMissed,
                    onReplanRequestLessTime = onReplanRequestLessTime,
                    onReplanMissedSelected = onReplanMissedSelected,
                    onReplanLessTimeSelected = onReplanLessTimeSelected,
                    onReplanAccept = onReplanAccept,
                    onReplanUndo = onReplanUndo,
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
