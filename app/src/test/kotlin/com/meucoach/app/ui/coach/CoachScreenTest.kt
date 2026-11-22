package com.meucoach.app.ui.coach

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.meucoach.app.application.ReplanChangeKind
import com.meucoach.app.coach.CoachGuardsContext
import com.meucoach.app.coach.CoachNextContext
import com.meucoach.app.ui.replan.LessTimeChange
import com.meucoach.app.ui.replan.LessTimeDayUi
import com.meucoach.app.ui.replan.MissedSessionUi
import com.meucoach.app.ui.replan.ReplanChangeUi
import com.meucoach.app.ui.replan.ReplanSuggestionUi
import com.meucoach.app.ui.replan.ReplanUiState
import com.meucoach.app.ui.replan.ReplanVersionChangeUi
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.coach.CoachGuard
import com.meucoach.core.domain.coach.CoachGuardKind
import com.meucoach.core.domain.coach.CoachNextSubject
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.QualityBlockReason
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.domain.replan.ReplanCause
import com.meucoach.core.domain.replan.ReplanTrigger
import com.meucoach.core.domain.review.WeeklyAdherence
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewObservation
import com.meucoach.core.domain.review.WeeklyReviewObservationKind
import com.meucoach.core.domain.review.WeeklyReviewStatus
import com.meucoach.core.domain.review.WeeklyVolume
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.ReadinessLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
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

    @After
    fun settleAnimations() {
        // Quick replies animate the feed scroll on the main clock. Let it
        // finish before the rule disposes, so `Dispatchers.Main` is free for
        // the next test class (otherwise coroutines-test warns/throws).
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

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

        compose.onNodeWithTag("coach-retro-review").performScrollTo().performClick()
        compose.onNodeWithTag("coach-review-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Semana de 09/11 a 15/11").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Você cumpriu a maior parte do que planejou.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-review-numbers").assertDoesNotExist()

        compose.onNodeWithTag("coach-review-numbers-toggle").performScrollTo().performClick()

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

        compose.onNodeWithTag("coach-retro-review").performScrollTo().performClick()
        compose.onNodeWithTag("coach-review-numbers-toggle").performScrollTo().performClick()
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

        compose.onNodeWithTag("coach-retro-review").performScrollTo().performClick()
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

        compose.onNodeWithTag("coach-quick-adjust").performScrollTo().performClick()
        compose.onNodeWithTag("coach-quick-missed").performScrollTo().performClick()
        compose.onNodeWithTag("coach-quick-less-time").performScrollTo().performClick()

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
    fun `sem plano mostra um unico caminho acionavel`() {
        setContent(next = CoachNextUiState.Onboarding)

        compose.onNodeWithTag("coach-next-onboarding").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Monte seu plano para começar").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "Complete perfil e disponibilidade, importe um treino ou faça a calibração.",
            substring = true,
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-onboarding-profile").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-onboarding-calibration").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-onboarding-import").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Semana concluída.").assertDoesNotExist()
        compose.onNodeWithText("Nenhum bloqueio ativo — siga o plano.").assertDoesNotExist()
        compose.onNodeWithTag("coach-guards-card").assertDoesNotExist()
        compose.onNodeWithTag("coach-progress-card").assertDoesNotExist()
    }

    @Test
    fun `fallback exibe copy de corredor e nao o motivo tecnico`() {
        setContent(
            explanation = CoachExplanationState.Fallback(
                reason = "Resposta contém código interno: ADHERENCE_PARTIAL",
                planRationale = "Plano da semana.",
            ),
        )

        compose.onNodeWithTag("coach-fallback-reason").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("me enrolei", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ADHERENCE_PARTIAL", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Resposta contém", substring = true).assertDoesNotExist()
    }

    @Test
    fun `fallback de rede mantem copy amigavel`() {
        setContent(
            explanation = CoachExplanationState.Fallback(
                reason = "Sem conexão aqui — te mostro o plano direto do motor.",
                planRationale = "Plano da semana.",
            ),
        )

        compose.onNodeWithText(
            "Sem conexão aqui — te mostro o plano direto do motor.",
            substring = true,
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `retrovisor vem depois da explicacao`() {
        setContent(
            progress = CoachProgressUiState.Ready(
                summary = emptyProgressSummary(),
                totalWeeks = 4,
                profile = null,
                today = monday,
            ),
        )

        val explanationY =
            compose.onNodeWithTag("coach-explanation-card").fetchSemanticsNode().positionInRoot.y
        val retroY = compose.onNodeWithTag("coach-retro-prompt").fetchSemanticsNode().positionInRoot.y

        assertThat(explanationY).isLessThan(retroY)
    }

    @Test
    fun `acoes do fallback ficam abaixo do cartao sem sobrepor`() {
        setContent(
            explanation = CoachExplanationState.Fallback(
                reason = "Sem conexão aqui — te mostro o plano direto do motor.",
                planRationale = "Plano da semana.",
            ),
        )

        compose.onNodeWithTag("coach-fallback-reason").performScrollTo()
        val cardY = compose.onNodeWithTag("coach-fallback-reason")
            .fetchSemanticsNode().positionInRoot.y
        val actionY = compose.onNodeWithText("Tentar de novo")
            .fetchSemanticsNode().positionInRoot.y

        assertThat(cardY).isLessThan(actionY)
    }

    @Test
    fun `proximo treino aparece no topo com o planejado`() {
        setContent(next = CoachNextUiState.Ready(trainingContext(monday.plusDays(1))))

        compose.onNodeWithTag("coach-next-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-date-divider").assertIsDisplayed()
        compose.onNodeWithText("Amanhã · Planejado: Rodagem · 30 min").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `dia de prova sem sessao na data mostra dia de prova`() {
        setContent(next = CoachNextUiState.Ready(raceContext(monday.plusDays(1))))

        compose.onNodeWithTag("coach-next-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Amanhã · Dia de prova").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rodagem", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Semana concluída.").assertDoesNotExist()
    }

    @Test
    fun `prescricao rebaixada manda no titulo e o planejado fica secundario`() {
        setContent(
            next = CoachNextUiState.Ready(
                trainingContext(
                    date = monday,
                    prescription = prescription(
                        adjustment = PrescriptionAdjustment.DOWNGRADE,
                        reason = QualityBlockReason.SUBJECTIVE_MISSING,
                    ),
                ),
            ),
        )

        compose.onNodeWithText("Hoje · Rodagem · 25 min").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Planejado: Ritmo forte · 35 min.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-next-planned").assertExists()
    }

    @Test
    fun `prescricao de descanso nao mostra duracao de treino`() {
        setContent(
            next = CoachNextUiState.Ready(
                trainingContext(
                    date = monday,
                    prescription = prescription(
                        adjustment = PrescriptionAdjustment.REST,
                        reason = QualityBlockReason.ILLNESS_OR_RED_READINESS,
                        prescribedType = WorkoutType.REST,
                        prescribedTitle = "Descanso",
                        prescribedDuration = null,
                    ),
                ),
            ),
        )

        compose.onNodeWithText("Hoje · Descanso").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Descanso prescrito", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `unlockHint cru com ACWR nao aparece no card`() {
        setContent(
            next = CoachNextUiState.Ready(
                trainingContext(
                    date = monday,
                    prescription = prescription(
                        adjustment = PrescriptionAdjustment.DOWNGRADE,
                        reason = QualityBlockReason.ACWR_HIGH,
                        unlockHint = "ACWR 1,50 > 1,50. Reduza a carga.",
                    ),
                ),
            ),
        )

        compose.onNodeWithText("ACWR", substring = true).assertDoesNotExist()
        compose.onNodeWithText("1,50", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Carga alta: dia leve.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `hoje sem check-in oferece Fazer check-in`() {
        var checkedIn = 0
        compose.setContent {
            McTheme(darkTheme = true) {
                CoachScreen(
                    consented = true,
                    showConsent = false,
                    explanation = readyExplanation(),
                    onGrant = {},
                    onDismiss = {},
                    onRevoke = {},
                    onRetry = {},
                    next = CoachNextUiState.Ready(trainingContext(date = monday)),
                    onNextCheckIn = { checkedIn++ },
                )
            }
        }

        compose.onNodeWithTag("coach-next-checkin").performScrollTo().performClick()
        assertThat(checkedIn).isEqualTo(1)
    }

    @Test
    fun `prontidao alta aparece com rotulo e score`() {
        setContent(
            next = CoachNextUiState.Ready(
                trainingContext(
                    date = monday,
                    readiness = readiness(82, ReadinessLevel.GREEN, emptySet()),
                    hasCheckIn = true,
                ),
            ),
        )

        compose.onNodeWithTag("coach-next-readiness").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Prontidão Alta · 82/100.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `prontidao sem dados nao mostra moderada inventada`() {
        setContent(
            next = CoachNextUiState.Ready(
                trainingContext(
                    date = monday,
                    readiness = readiness(
                        score = 50,
                        level = ReadinessLevel.AMBER,
                        flags = setOf(
                            ReadinessFlag.INSUFFICIENT_DATA,
                            ReadinessFlag.SUBJECTIVE_MISSING,
                        ),
                    ),
                ),
            ),
        )

        compose.onNodeWithText("ainda não medida", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Prontidão Moderada", substring = true).assertDoesNotExist()
    }

    @Test
    fun `amanha nao mostra semaforo`() {
        setContent(
            next = CoachNextUiState.Ready(
                trainingContext(
                    date = monday.plusDays(1),
                    readiness = readiness(82, ReadinessLevel.GREEN, emptySet()),
                    hasCheckIn = true,
                ),
            ),
        )

        compose.onNodeWithTag("coach-next-readiness").assertDoesNotExist()
    }

    @Test
    fun `conteudo local aparece sem consentimento e a IA fica opcional`() {
        compose.setContent {
            McTheme(darkTheme = true) {
                CoachScreen(
                    consented = false,
                    showConsent = true,
                    explanation = CoachExplanationState.Unavailable,
                    onGrant = {},
                    onDismiss = {},
                    onRevoke = {},
                    onRetry = {},
                    next = CoachNextUiState.Ready(trainingContext(monday.plusDays(1))),
                    guards = CoachGuardsUiState.Ready(CoachGuardsContext(emptyList())),
                    progress = CoachProgressUiState.Ready(
                        summary = emptyProgressSummary(),
                        totalWeeks = 4,
                        profile = null,
                        today = monday,
                    ),
                )
            }
        }

        compose.onNodeWithTag("coach-next-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-guards-card").assertDoesNotExist()
        compose.onNodeWithTag("coach-ai-invite").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-ai-grant").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-ai-later").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-retro-progress").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithTag("coach-progress-card").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `depois mantem ativar IA persistente`() {
        compose.setContent {
            McTheme(darkTheme = true) {
                CoachScreen(
                    consented = false,
                    showConsent = false,
                    explanation = CoachExplanationState.Unavailable,
                    onGrant = {},
                    onDismiss = {},
                    onRevoke = {},
                    onRetry = {},
                    next = CoachNextUiState.Ready(trainingContext(monday.plusDays(1))),
                )
            }
        }

        compose.onNodeWithTag("coach-ai-grant").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-ai-later").assertDoesNotExist()
    }

    @Test
    fun `onboarding leva a perfil calibracao e importacao`() {
        var profile = 0
        var calibration = 0
        var import = 0
        compose.setContent {
            McTheme(darkTheme = true) {
                CoachScreen(
                    consented = false,
                    showConsent = true,
                    explanation = CoachExplanationState.Unavailable,
                    onGrant = {},
                    onDismiss = {},
                    onRevoke = {},
                    onRetry = {},
                    next = CoachNextUiState.Onboarding,
                    onOnboardingProfile = { profile++ },
                    onOnboardingCalibration = { calibration++ },
                    onOnboardingImport = { import++ },
                )
            }
        }

        compose.onNodeWithTag("coach-onboarding-profile").performScrollTo().performClick()
        compose.onNodeWithTag("coach-onboarding-calibration").performScrollTo().performClick()
        compose.onNodeWithTag("coach-onboarding-import").performScrollTo().performClick()

        assertThat(profile).isEqualTo(1)
        assertThat(calibration).isEqualTo(1)
        assertThat(import).isEqualTo(1)
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
                    next = CoachNextUiState.Ready(trainingContext(monday.plusDays(1))),
                    onNextToday = { today++ },
                )
            }
        }

        compose.onNodeWithTag("coach-next-today").performScrollTo().performClick()
        assertThat(today).isEqualTo(1)
    }

    private val monday = LocalDate.of(2026, 11, 16)

    private fun readyExplanation() = CoachExplanationState.Ready(
        title = "Semana atual",
        body = "Plano da semana.",
        bullets = emptyList(),
    )

    private fun session(
        date: LocalDate = monday.plusDays(1),
        type: WorkoutType = WorkoutType.EASY,
        title: String = "Rodagem",
        durationMin: Int = 30,
    ) = PlannedSession(
        date = date,
        type = type,
        title = title,
        durationMin = durationMin,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = SessionStatus.PLANNED,
    )

    private fun doneSession() = PlannedSession(
        date = monday,
        type = WorkoutType.EASY,
        title = "Rodagem",
        durationMin = 30,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = SessionStatus.DONE,
        completionEvidence = CompletionEvidence.MANUAL,
    )

    private fun trainingContext(
        date: LocalDate = monday.plusDays(1),
        prescription: DailyPrescription? = null,
        readiness: ReadinessResult? = null,
        hasCheckIn: Boolean = false,
        sleepMinutes: Int? = null,
        todaySession: PlannedSession? = null,
        importedToday: Boolean = false,
    ) = CoachNextContext(
        subject = CoachNextSubject.Training(session(date)),
        today = monday,
        hasPlan = true,
        hasProfile = true,
        prescription = prescription,
        readiness = readiness,
        hasCheckIn = hasCheckIn,
        sleepMinutes = sleepMinutes,
        todaySession = todaySession,
        importedToday = importedToday,
    )

    private fun raceContext(date: LocalDate) = CoachNextContext(
        subject = CoachNextSubject.Race(
            RaceEvent(
                id = "race-1",
                distance = RaceDistance.FIVE_K,
                targetDate = date,
                createdAt = java.time.Instant.parse("2026-11-16T12:00:00Z"),
                updatedAt = java.time.Instant.parse("2026-11-16T12:00:00Z"),
            ),
        ),
        today = monday,
        hasPlan = true,
        hasProfile = true,
    )

    private fun prescription(
        adjustment: PrescriptionAdjustment,
        reason: QualityBlockReason? = null,
        prescribedType: WorkoutType = WorkoutType.EASY,
        prescribedTitle: String = "Rodagem",
        prescribedDuration: Int? = 25,
        unlockHint: String = "Faça o check-in de hoje para liberar o treino forte.",
    ) = DailyPrescription(
        date = monday,
        plannedSessionId = null,
        plannedType = WorkoutType.QUALITY,
        plannedTitle = "Ritmo forte",
        plannedDurationMin = 35,
        prescribedType = prescribedType,
        title = prescribedTitle,
        prescribedDurationMin = prescribedDuration,
        targets = PrescriptionTargets(primary = TargetKind.RPE),
        adjustment = adjustment,
        rationale = "Dia leve.",
        ruleIds = listOf("R-PRESC-02"),
        evidenceIds = listOf("EV-SAW-2016"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = "daily-prescription-v6",
        inputsHash = "hash",
        qualityBlockReason = reason,
        decisionDetail = if (reason == null) null else "check-in de hoje ausente",
        unlockHint = if (reason == null) null else unlockHint,
    )

    private fun readiness(score: Int, level: ReadinessLevel, flags: Set<ReadinessFlag>) = ReadinessResult(
        score = score,
        level = level,
        confidence = DataConfidence.MEDIUM,
        components = if (ReadinessFlag.INSUFFICIENT_DATA in flags) {
            emptyMap()
        } else {
            mapOf(ReadinessComponent.SUBJECTIVE to score)
        },
        missing = emptySet(),
        flags = flags,
    )

    private fun emptyProgressSummary() = com.meucoach.core.domain.coach.CoachProgressSummary(
        availableWeeks = 0,
        plannedSessions = 0,
        doneSessions = 0,
        adherencePercent = null,
        plannedMinutes = 0,
        executedMinutes = 0,
        extraMinutes = 0,
        volumePercent = null,
        avgReadiness = null,
        redDays = 0,
        avgSleepMinutes = null,
        shortNights = 0,
        easyPercent28d = null,
    )

    @Test
    fun `travas aparecem com titulo e detalhe`() {
        setContent(
            guards = CoachGuardsUiState.Ready(
                CoachGuardsContext(
                    listOf(
                        CoachGuard(
                            CoachGuardKind.RECOVERY,
                            "Recuperação até 18/11.",
                            "Teto de 30 min, sem qualidade nem longão.",
                        ),
                        CoachGuard(
                            CoachGuardKind.CALIBRATION,
                            "Base de carga em calibração.",
                            "5 de 28 dias com carga objetiva.",
                        ),
                    ),
                ),
            ),
        )

        compose.onNodeWithTag("coach-guards-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Recuperação até 18/11.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Base de card em calibração.").assertDoesNotExist()
        compose.onNodeWithText("Base de carga em calibração.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `mais de quatro travas expandem sob demanda`() {
        val five = (1..5).map {
            CoachGuard(
                CoachGuardKind.NO_AVAILABILITY,
                "Trava $it",
                "Detalhe $it.",
            )
        }
        setContent(guards = CoachGuardsUiState.Ready(CoachGuardsContext(five)))

        compose.onNodeWithText("Trava 2", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Trava 3", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("coach-guards-more").performScrollTo().performClick()
        compose.onNodeWithText("Trava 5", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `sem travas nao ocupa o feed`() {
        setContent(guards = CoachGuardsUiState.Ready(CoachGuardsContext(emptyList())))

        compose.onNodeWithTag("coach-guards-card").assertDoesNotExist()
        compose.onNodeWithText("Nenhum bloqueio ativo — siga o plano.").assertDoesNotExist()
    }

    @Test
    fun `erro das travas tem tentar de novo`() {
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
                    guards = CoachGuardsUiState.Error("Sem conexão."),
                    onGuardsRetry = { retried++ },
                )
            }
        }

        compose.onNodeWithTag("coach-guards-retry").performScrollTo().performClick()
        assertThat(retried).isEqualTo(1)
    }

    @Test
    fun `evolucao mostra os agregados das semanas`() {
        setContent(
            progress = CoachProgressUiState.Ready(
                summary = com.meucoach.core.domain.coach.CoachProgressSummary(
                    availableWeeks = 4,
                    plannedSessions = 9,
                    doneSessions = 6,
                    adherencePercent = 67,
                    plannedMinutes = 214,
                    executedMinutes = 219,
                    extraMinutes = 28,
                    volumePercent = 102,
                    avgReadiness = 62,
                    redDays = 1,
                    avgSleepMinutes = 360,
                    shortNights = 2,
                    easyPercent28d = 52,
                ),
                totalWeeks = 4,
                profile = null,
                today = LocalDate.of(2026, 11, 19),
            ),
        )

        compose.onNodeWithTag("coach-retro-progress").performScrollTo().performClick()
        compose.onNodeWithTag("coach-progress-card").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("6 de 9 sessões do plano (67%)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-progress-numbers").assertDoesNotExist()

        compose.onNodeWithTag("coach-progress-numbers-toggle").performScrollTo().performClick()

        compose.onNodeWithText("Planejado 3h34 · feito 3h39 (102%)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("52% fácil nos últimos 28 dias (alvo ~80%).").performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Sem VDOT recente — correndo por RPE.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `evolucao vazia mostra caminho sem numero`() {
        setContent(
            progress = CoachProgressUiState.Ready(
                summary = com.meucoach.core.domain.coach.CoachProgressSummary(
                    availableWeeks = 0,
                    plannedSessions = 0,
                    doneSessions = 0,
                    adherencePercent = null,
                    plannedMinutes = 0,
                    executedMinutes = 0,
                    extraMinutes = 0,
                    volumePercent = null,
                    avgReadiness = null,
                    redDays = 0,
                    avgSleepMinutes = null,
                    shortNights = 0,
                    easyPercent28d = null,
                ),
                totalWeeks = 4,
                profile = null,
                today = LocalDate.of(2026, 11, 19),
            ),
        )

        compose.onNodeWithTag("coach-retro-progress").performScrollTo().performClick()
        compose.onNodeWithText("Pouco histórico ainda.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("6 de 9 sessões do plano (67%)").assertDoesNotExist()
    }

    @Test
    fun `erro da evolucao tem tentar de novo`() {
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
                    progress = CoachProgressUiState.Error("Sem conexão."),
                    onProgressRetry = { retried++ },
                    onProgressTrends = {},
                )
            }
        }

        compose.onNodeWithTag("coach-retro-progress").performScrollTo().performClick()
        compose.onNodeWithTag("coach-progress-retry").performScrollTo().performClick()
        assertThat(retried).isEqualTo(1)
    }

    @Test
    fun `evolucao leva a Tendencias`() {
        var trends = 0
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
                    progress = CoachProgressUiState.Ready(
                        summary = com.meucoach.core.domain.coach.CoachProgressSummary(
                            availableWeeks = 0,
                            plannedSessions = 0,
                            doneSessions = 0,
                            adherencePercent = null,
                            plannedMinutes = 0,
                            executedMinutes = 0,
                            extraMinutes = 0,
                            volumePercent = null,
                            avgReadiness = null,
                            redDays = 0,
                            avgSleepMinutes = null,
                            shortNights = 0,
                            easyPercent28d = null,
                        ),
                        totalWeeks = 4,
                        profile = null,
                        today = LocalDate.of(2026, 11, 19),
                    ),
                    onProgressTrends = { trends++ },
                )
            }
        }

        compose.onNodeWithTag("coach-retro-progress").performScrollTo().performClick()
        compose.onNodeWithTag("coach-progress-trends").performScrollTo().performClick()
        assertThat(trends).isEqualTo(1)
    }

    @Test
    fun `chat header abre como funciona sem tecnices no corpo`() {
        setContent()

        compose.onNodeWithTag("coach-chat-header").assertIsDisplayed()
        compose.onNodeWithText("motor no comando").assertIsDisplayed()
        compose.onNodeWithText("IA ativa").assertDoesNotExist()
        compose.onNodeWithTag("coach-chat-info").performClick()
        compose.onNodeWithTag("coach-how-it-works-sheet").assertIsDisplayed()
        compose.onNodeWithText("O motor decide o treino", substring = true).assertIsDisplayed()
        compose.onNodeWithText("IA ativa neste aparelho.", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("coach-how-it-works-close").performClick()
        compose.onNodeWithTag("coach-how-it-works-sheet").assertDoesNotExist()
    }

    @Test
    fun `desativar IA mora no como funciona`() {
        var revoked = 0
        compose.setContent {
            McTheme(darkTheme = true) {
                CoachScreen(
                    consented = true,
                    showConsent = false,
                    explanation = readyExplanation(),
                    onGrant = {},
                    onDismiss = {},
                    onRevoke = { revoked++ },
                    onRetry = {},
                    next = CoachNextUiState.Hidden,
                )
            }
        }

        compose.onNodeWithTag("coach-disable").assertDoesNotExist()
        compose.onNodeWithTag("coach-chat-info").performClick()
        compose.onNodeWithTag("coach-disable").performClick()
        assertThat(revoked).isEqualTo(1)
        compose.onNodeWithTag("coach-how-it-works-sheet").assertDoesNotExist()
    }

    @Test
    fun `abertura conversa aparece antes do proximo treino`() {
        setContent(next = CoachNextUiState.Ready(trainingContext(monday.plusDays(1))))

        compose.onAllNodesWithTag("coach-chat-opening").onFirst().performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Amanhã tem rodagem, 30 min", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `quick reply perdi treino emite pergunta e chama o fluxo`() {
        var missed = 0
        val echoes = mutableListOf<String>()
        setContent(onReplanRequestMissed = { missed++ }, onEchoQuestion = { echoes += it })

        compose.onNodeWithTag("coach-quick-missed").performScrollTo().performClick()
        assertThat(echoes).containsExactly("Perdi o treino de ontem")
        assertThat(missed).isEqualTo(1)
    }

    @Test
    fun `quick reply menos tempo emite pergunta e chama o fluxo`() {
        var lessTime = 0
        val echoes = mutableListOf<String>()
        setContent(onReplanRequestLessTime = { lessTime++ }, onEchoQuestion = { echoes += it })

        compose.onNodeWithTag("coach-quick-less-time").performScrollTo().performClick()
        assertThat(echoes).containsExactly("Só tenho 30 min na quinta")
        assertThat(lessTime).isEqualTo(1)
    }

    @Test
    fun `quick reply explica semana emite pergunta e pede retry`() {
        var retried = 0
        val echoes = mutableListOf<String>()
        setContent(onRetry = { retried++ }, onEchoQuestion = { echoes += it })

        compose.onNodeWithTag("coach-quick-explain").performScrollTo().performClick()
        assertThat(echoes).containsExactly("Pode explicar minha semana?")
        assertThat(retried).isEqualTo(1)
    }

    @Test
    fun `toque duplo repassa as duas perguntas e a VM deduplica`() {
        val echoes = mutableListOf<String>()
        setContent(onEchoQuestion = { echoes += it })

        compose.onNodeWithTag("coach-quick-explain").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("coach-quick-explain").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(echoes).containsExactly(
            "Pode explicar minha semana?",
            "Pode explicar minha semana?",
        )
    }

    @Test
    fun `retrovisor no rodape rola ate o prompt`() {
        setContent(review = review())

        compose.onNodeWithTag("coach-quick-retro").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("coach-retro-prompt").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `importacao de hoje mostra system turn de carga`() {
        setContent(next = CoachNextUiState.Ready(trainingContext(importedToday = true)))

        compose.onNodeWithTag("coach-system-import").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-import-ack").performScrollTo().assertTextContains(
            "Já entrou na carga",
            substring = true,
        )
    }

    @Test
    fun `treino feito mostra system turn comemorando`() {
        setContent(next = CoachNextUiState.Ready(trainingContext(todaySession = doneSession())))

        compose.onNodeWithTag("coach-system-done").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-done-cheer").performScrollTo().assertTextContains(
            "Rodagem",
            substring = true,
        )
    }

    @Test
    fun `treino pendente nao mostra system turn de conclusao`() {
        setContent(next = CoachNextUiState.Ready(trainingContext()))

        compose.onNodeWithTag("coach-system-done").assertDoesNotExist()
        compose.onNodeWithTag("coach-done-cheer").assertDoesNotExist()
    }

    @Test
    fun `check-in feito mostra system turn com resposta curta`() {
        setContent(
            next = CoachNextUiState.Ready(
                trainingContext(
                    date = monday,
                    prescription = prescription(
                        adjustment = PrescriptionAdjustment.KEEP,
                    ),
                    hasCheckIn = true,
                ),
            ),
        )

        compose.onNodeWithTag("coach-system-checkin").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Check-in feito", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-checkin-ack").performScrollTo().assertTextContains(
            "como planejado",
            substring = true,
        )
    }

    @Test
    fun `sem check-in nao mostra system turn`() {
        setContent(next = CoachNextUiState.Ready(trainingContext(date = monday)))

        compose.onNodeWithTag("coach-system-checkin").assertDoesNotExist()
        compose.onNodeWithTag("coach-checkin-ack").assertDoesNotExist()
    }

    @Test
    fun `abertura de prova fala boa prova`() {
        setContent(next = CoachNextUiState.Ready(raceContext(monday.plusDays(1))))

        compose.onNodeWithTag("coach-chat-opening").assertTextContains("dia de prova", substring = true)
    }

    @Test
    fun `abertura sem check-in pede check-in`() {
        setContent(next = CoachNextUiState.Ready(trainingContext(monday)))

        compose.onNodeWithTag("coach-chat-opening").assertTextContains("check-in", substring = true)
    }

    @Test
    fun `digitando aparece enquanto a IA carrega`() {
        setContent(explanation = CoachExplanationState.Loading)

        compose.onNodeWithTag("coach-typing").assertIsDisplayed()
    }

    @Test
    fun `composer habilitado com opt-in mostra o campo`() {
        setContent()

        compose.onNodeWithTag("coach-composer-input").assertIsDisplayed()
        compose.onNodeWithTag("coach-composer-send").assertHasClickAction()
        compose.onNodeWithText("Em breve você poderá digitar", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun `barra de input envolve o composer para ficar acima do teclado`() {
        setContent()

        // Regressão: sem o imePadding na barra, o teclado cobre o campo
        // e o usuário não vê o que digita.
        compose.onNodeWithTag("coach-input-bar").assertIsDisplayed()
        compose.onNodeWithTag("coach-composer-input").assertIsDisplayed()
        compose.onNodeWithTag("coach-composer-send").assertHasClickAction()
    }

    @Test
    fun `composer envia a mensagem digitada e limpa o campo`() {
        var sent: String? = null
        setContent(onSendFreeText = { sent = it })

        compose.onNodeWithTag("coach-composer-input").performTextInput("só tenho 30 min")
        compose.onNodeWithTag("coach-composer-send").performClick()

        assertThat(sent).isEqualTo("só tenho 30 min")
        compose.onNodeWithText("só tenho 30 min", substring = true).assertDoesNotExist()
    }

    @Test
    fun `composer trava o envio enquanto ha resposta carregando`() {
        setContent(
            exchanges = listOf(
                FreeExchange("e1", "o que tem hoje?", FreeAnswerUiState.Loading),
            ),
        )

        compose.onNodeWithTag("coach-composer-send").assertIsNotEnabled()
    }

    @Test
    fun `composer expoe descricoes de acessibilidade`() {
        setContent()

        compose.onNodeWithContentDescription("Digite sua mensagem para o Coach").assertIsDisplayed()
        compose.onNodeWithContentDescription("Enviar mensagem").assertHasClickAction()
    }

    @Test
    fun `composer some sem opt-in e mostra a dica`() {
        setContent(consented = false)

        compose.onNodeWithTag("coach-composer-input").assertDoesNotExist()
        compose.onNodeWithTag("coach-composer-send").assertDoesNotExist()
        compose.onNodeWithTag("coach-composer").assertIsDisplayed()
        compose.onNodeWithText("Em breve você poderá digitar", substring = true).assertIsDisplayed()
    }

    @Test
    fun `troca pergunta e resposta aparece adjacente no fluxo`() {
        setContent(
            exchanges = listOf(
                FreeExchange("e1", "o que tem hoje?", FreeAnswerUiState.Ready(listOf("Fechado.", "Bora?"))),
            ),
        )

        compose.onNodeWithText("o que tem hoje?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fechado.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Bora?").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `troca carregando mostra digitando junto a pergunta`() {
        setContent(
            exchanges = listOf(
                FreeExchange("e1", "o que tem hoje?", FreeAnswerUiState.Loading),
            ),
        )

        compose.onNodeWithText("o que tem hoje?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-free-typing").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `troca sem resposta mostra so a pergunta`() {
        setContent(
            exchanges = listOf(
                FreeExchange("e1", "Perdi o treino de ontem", FreeAnswerUiState.Idle),
            ),
        )

        compose.onNodeWithText("Perdi o treino de ontem").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `fallback livre mostra a fala com tentar de novo`() {
        var retried = 0
        setContent(
            exchanges = listOf(
                FreeExchange("e1", "o que tem hoje?", FreeAnswerUiState.Fallback("Sem conexão com o Coach.")),
            ),
            onRetryFreeAnswer = { retried++ },
        )

        compose.onNodeWithTag("coach-free-fallback").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("coach-free-retry").performScrollTo().performClick()

        assertThat(retried).isEqualTo(1)
    }

    private fun setContent(
        explanation: CoachExplanationState = CoachExplanationState.Ready(
            title = "Semana atual",
            body = "Plano da semana.",
            bullets = emptyList(),
        ),
        next: CoachNextUiState = CoachNextUiState.Hidden,
        guards: CoachGuardsUiState = CoachGuardsUiState.Hidden,
        progress: CoachProgressUiState = CoachProgressUiState.Hidden,
        review: WeeklyReview? = null,
        reviewState: CoachReviewState = CoachReviewState.Unavailable,
        replan: ReplanUiState = ReplanUiState(),
        consented: Boolean = true,
        exchanges: List<FreeExchange> = emptyList(),
        onSendFreeText: (String) -> Unit = {},
        onEchoQuestion: (String) -> Unit = {},
        onRetryFreeAnswer: () -> Unit = {},
        onReplanRequest: () -> Unit = {},
        onReplanRequestMissed: () -> Unit = {},
        onReplanRequestLessTime: () -> Unit = {},
        onReplanMissedSelected: (LocalDate) -> Unit = {},
        onReplanLessTimeSelected: (List<LessTimeChange>) -> Unit = {},
        onReplanAccept: () -> Unit = {},
        onReplanUndo: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        compose.setContent {
            McTheme(darkTheme = true) {
                CoachScreen(
                    consented = consented,
                    showConsent = false,
                    explanation = explanation,
                    onGrant = {},
                    onDismiss = {},
                    onRevoke = {},
                    onRetry = onRetry,
                    next = next,
                    onNextCheckIn = {},
                    onNextRetry = {},
                    onNextToday = {},
                    guards = guards,
                    onGuardsRetry = {},
                    progress = progress,
                    review = review,
                    reviewState = reviewState,
                    replan = replan,
                    exchanges = exchanges,
                    onSendFreeText = onSendFreeText,
                    onEchoQuestion = onEchoQuestion,
                    onRetryFreeAnswer = onRetryFreeAnswer,
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
