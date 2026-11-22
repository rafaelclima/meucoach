package com.meucoach.app.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.coach.CoachConsentStore
import com.meucoach.app.coach.CoachGuardsContext
import com.meucoach.app.coach.CoachNextContext
import com.meucoach.app.coach.CoachPlanContext
import com.meucoach.app.coach.CoachProgressContext
import com.meucoach.app.coach.CoachProgressSource
import com.meucoach.app.coach.CoachSources
import com.meucoach.app.di.ApplicationScope
import com.meucoach.app.replan.ReplanControllerFactory
import com.meucoach.app.ui.replan.LessTimeChange
import com.meucoach.app.ui.replan.ReplanUiState
import com.meucoach.core.data.coach.CoachApi
import com.meucoach.core.data.coach.CoachKind
import com.meucoach.core.data.coach.CoachMemoryRepository
import com.meucoach.core.domain.coach.ChatTurn
import com.meucoach.core.domain.coach.ChatTurns
import com.meucoach.core.domain.coach.CoachFreeAnswerPrompt
import com.meucoach.core.domain.coach.CoachMessageKind
import com.meucoach.core.domain.coach.CoachNextSubject
import com.meucoach.core.domain.coach.CoachProgress
import com.meucoach.core.domain.coach.CoachProgressAnswer
import com.meucoach.core.domain.coach.CoachProgressSummary
import com.meucoach.core.domain.coach.CoachPromptBuilder
import com.meucoach.core.domain.coach.CoachResponseValidator
import com.meucoach.core.domain.coach.CoachReviewPrompt
import com.meucoach.core.domain.coach.CoachReviewPromptBuilder
import com.meucoach.core.domain.coach.CoachSessionAnswer
import com.meucoach.core.domain.coach.CoachSessionReference
import com.meucoach.core.domain.coach.CoachValidationResult
import com.meucoach.core.domain.coach.FreeInputIntent
import com.meucoach.core.domain.coach.FreeInputParse
import com.meucoach.core.domain.coach.FreeInputParser
import com.meucoach.core.domain.coach.SessionTask
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.model.AthleteProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

sealed interface CoachExplanationState {
    data object Unavailable : CoachExplanationState

    data object Loading : CoachExplanationState

    data class Ready(val title: String, val body: String, val bullets: List<String>) : CoachExplanationState

    data class Fallback(val reason: String, val planRationale: String) : CoachExplanationState
}

/**
 * Narrative of the closed-week review. `Fallback` keeps the deterministic review
 * on screen: the AI only rewrites text, so an unavailable model is not an error
 * state for the athlete.
 */
sealed interface CoachReviewState {
    data object Unavailable : CoachReviewState

    data object Loading : CoachReviewState

    data class Ready(val title: String, val body: String, val bullets: List<String>) : CoachReviewState

    data class Fallback(val reason: String) : CoachReviewState
}

/**
 * Next relevant event for the Coach tab (PRD evolução, F1).
 *
 * Local and always rendered, even without AI consent. `Ready` carries the
 * read-only pick of [CoachNextSource] and its readiness when the day is today;
 * `Onboarding` is the single path when there is no profile or plan. The tab
 * never triggers a new prescription.
 */
sealed interface CoachNextUiState {
    data object Hidden : CoachNextUiState

    data object Loading : CoachNextUiState

    /** No profile or plan: the screen shows one onboarding card with CTAs. */
    data object Onboarding : CoachNextUiState

    /** A plan exists and has no pending future session (and no relevant race). */
    data object Empty : CoachNextUiState

    data class Ready(val context: CoachNextContext) : CoachNextUiState

    data class Error(val reason: String) : CoachNextUiState
}

/**
 * Structural guards for the Coach tab (PRD evolução, F2).
 *
 * Local and always rendered, even without AI consent. `Ready` carries the
 * read-only guards resolved from the shared signals; an empty list means a
 * green week and the UI shows a single quiet line instead of an empty card.
 */
sealed interface CoachGuardsUiState {
    data object Hidden : CoachGuardsUiState

    data object Loading : CoachGuardsUiState

    data class Ready(val context: CoachGuardsContext) : CoachGuardsUiState

    data class Error(val reason: String) : CoachGuardsUiState
}

/**
 * Four-week rollup for the Coach tab (PRD evolução, F4).
 *
 * Local and always rendered, even without AI consent. `Ready` carries the pure
 * rollup of the last closed weeks plus the profile for the VDOT line; weeks
 * without data surface as a quiet empty card instead of an error.
 */
sealed interface CoachProgressUiState {
    data object Hidden : CoachProgressUiState

    data object Loading : CoachProgressUiState

    data class Ready(
        val summary: CoachProgressSummary,
        val totalWeeks: Int,
        val profile: AthleteProfile?,
        val today: LocalDate,
    ) : CoachProgressUiState

    data class Error(val reason: String) : CoachProgressUiState
}

/**
 * Read-only list of what the Coach already persisted (PRD decisions 17/25).
 *
 * `Hidden` is the no-consent state: without opt-in the tab never shows nor
 * reads the memory. `Ready` carries validated copy only; it is never a source
 * of load, pace or planning.
 */
sealed interface CoachHistoryState {
    data object Hidden : CoachHistoryState

    data object Loading : CoachHistoryState

    data object Empty : CoachHistoryState

    data class Ready(val turns: List<ChatTurn>) : CoachHistoryState

    data class Error(val reason: String) : CoachHistoryState
}

/**
 * Answer to one typed message (ADR 0056).
 *
 * `Ready` carries validated or deterministic copy split into chat bubbles;
 * `Fallback` carries honest coach-voiced copy, never a technical reason.
 * The question itself is persisted as a `YOU` turn; every `Ready` answer —
 * model-validated or deterministic — is persisted as an `ANSWER` turn, so
 * the archive never holds a question without its answer (ADR 0057 item 3).
 * Only `Fallback` stays ephemeral (transient refusal or failure).
 */
sealed interface FreeAnswerUiState {
    data object Idle : FreeAnswerUiState

    data object Loading : FreeAnswerUiState

    data class Ready(val texts: List<String>, val reference: List<String> = emptyList()) : FreeAnswerUiState

    data class Fallback(val copy: String) : FreeAnswerUiState
}

/**
 * One free-text round trip in the session feed (ADR 0057 item 2).
 *
 * The screen will render each exchange as an adjacent Q&A pair in flow
 * order; the legacy single-answer mirror (`freeAnswer`) stays until the
 * screen migrates. `answer == Idle` means question only (no reply follows).
 */
data class FreeExchange(val id: String, val question: String, val answer: FreeAnswerUiState)

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val consent: CoachConsentStore,
    private val sources: CoachSources,
    private val api: CoachApi,
    private val memory: CoachMemoryRepository,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope,
    replanFactory: ReplanControllerFactory,
) : ViewModel() {
    private val _consented = MutableStateFlow(consent.isConsented())
    val consented: StateFlow<Boolean> = _consented

    private val _dismissed = MutableStateFlow(false)
    val dismissed: StateFlow<Boolean> = _dismissed

    private val replanController = replanFactory.create(viewModelScope)

    /** Same acceptance machine as Today/Perfil; the Coach tab never duplicates it. */
    val replan: StateFlow<ReplanUiState> = replanController.uiState

    private val _explanation =
        MutableStateFlow<CoachExplanationState>(CoachExplanationState.Unavailable)
    val explanation: StateFlow<CoachExplanationState> = _explanation

    private val _review = MutableStateFlow<WeeklyReview?>(null)
    val review: StateFlow<WeeklyReview?> = _review

    private val _reviewState = MutableStateFlow<CoachReviewState>(CoachReviewState.Unavailable)
    val reviewState: StateFlow<CoachReviewState> = _reviewState

    private val _history = MutableStateFlow<CoachHistoryState>(CoachHistoryState.Hidden)
    val history: StateFlow<CoachHistoryState> = _history

    private val _freeAnswer = MutableStateFlow<FreeAnswerUiState>(FreeAnswerUiState.Idle)

    /** Legacy single-answer mirror of the latest exchange; the screen migrates to [exchanges]. */
    val freeAnswer: StateFlow<FreeAnswerUiState> = _freeAnswer

    private val _exchanges = MutableStateFlow<List<FreeExchange>>(emptyList())

    /** Free-text round trips in send order; answers settle in place, never overwrite each other. */
    val exchanges: StateFlow<List<FreeExchange>> = _exchanges
    private var lastFreeText: String? = null

    private val _next = MutableStateFlow<CoachNextUiState>(CoachNextUiState.Loading)
    val next: StateFlow<CoachNextUiState> = _next

    private val _guards = MutableStateFlow<CoachGuardsUiState>(CoachGuardsUiState.Loading)
    val guards: StateFlow<CoachGuardsUiState> = _guards

    private val _progress = MutableStateFlow<CoachProgressUiState>(CoachProgressUiState.Loading)
    val progress: StateFlow<CoachProgressUiState> = _progress

    /** Hash of the explanation currently rendered; excluded from the archive. */
    private val currentExplainHash = MutableStateFlow<String?>(null)

    private val retryTick = MutableStateFlow(0)
    private val historyRetryTick = MutableStateFlow(0)
    private var lastExplainContext: CoachPlanContext? = null
    private var lastReviewHash: String? = null

    init {
        viewModelScope.launch {
            runCatching { memory.pruneExpired() }
                .onFailure { android.util.Log.d("Coach", "memory prune failed: ${it.message}") }
        }
        observeNext()
        observeGuards()
        observeProgress()
        viewModelScope.launch {
            combine(_consented, historyRetryTick) { isConsented, tick -> isConsented to tick }
                .collectLatest { (isConsented, _) ->
                    if (!isConsented) {
                        _history.value = CoachHistoryState.Hidden
                        return@collectLatest
                    }
                    combine(
                        memory.observeTurns(),
                        currentExplainHash,
                        _review,
                    ) { turns, explainHash, review ->
                        val visible = ChatTurns.visibleTurns(
                            turns = turns,
                            currentExplainHash = explainHash,
                            currentReviewWeek = review?.weekStart,
                        )
                        if (visible.isEmpty()) {
                            CoachHistoryState.Empty
                        } else {
                            CoachHistoryState.Ready(turns = visible)
                        }
                    }
                        .onStart { emit(CoachHistoryState.Loading) }
                        .catch { error ->
                            android.util.Log.d("Coach", "history failed: ${error.message}")
                            emit(CoachHistoryState.Error("Não foi possível carregar o histórico."))
                        }
                        .collect { _history.value = it }
                }
        }
        viewModelScope.launch {
            combine(_consented, sources.plans.observeCurrent(), retryTick) { isConsented, context, tick ->
                Triple(isConsented, context, tick)
            }.collect { (isConsented, context, _) ->
                val plan = context?.plan
                if (!isConsented || plan == null) {
                    lastExplainContext = null
                    currentExplainHash.value = null
                    _explanation.value = CoachExplanationState.Unavailable
                    return@collect
                }
                if (context == lastExplainContext && _explanation.value is CoachExplanationState.Ready) {
                    return@collect
                }
                currentExplainHash.value = context.inputsHash
                _explanation.value = CoachExplanationState.Loading
                try {
                    val prompt = CoachPromptBuilder.build(plan, context.inputsHash)
                    val raw = api.explain(prompt, CoachKind.EXPLAIN)
                    if (!_consented.value) return@collect
                    val allowed = prompt.sessions.mapNotNull { it.durationMin }.toSet() +
                        prompt.weeklyMinutes +
                        listOfNotNull(prompt.executedMinutes)
                    val validated = CoachResponseValidator.validate(
                        raw,
                        allowed,
                        forbiddenCodes = prompt.ruleIds.toSet(),
                    )
                    when (validated) {
                        is CoachValidationResult.Valid -> {
                            val response = validated.response
                            recordSafely {
                                memory.recordMessage(
                                    kind = CoachMessageKind.EXPLAIN,
                                    inputsHash = context.inputsHash,
                                    title = response.title,
                                    body = response.body,
                                    bullets = response.bullets,
                                )
                            }
                            lastExplainContext = context
                            currentExplainHash.value = context.inputsHash
                            android.util.Log.d("Coach", "explain ok hash=${context.inputsHash}")
                            _explanation.value = CoachExplanationState.Ready(
                                title = response.title,
                                body = response.body,
                                bullets = response.bullets,
                            )
                        }

                        is CoachValidationResult.Invalid -> {
                            android.util.Log.d("Coach", "explain rejected: ${validated.reason}")
                            _explanation.value = CoachExplanationState.Fallback(
                                reason = validated.reason,
                                planRationale = plan.rationale,
                            )
                        }
                    }
                } catch (e: IOException) {
                    if (!_consented.value) return@collect
                    android.util.Log.d("Coach", "explain io: ${e.message}")
                    _explanation.value = CoachExplanationState.Fallback(
                        reason = "Sem conexão com o Coach. Mostrando o resumo do motor.",
                        planRationale = plan.rationale,
                    )
                } catch (e: Exception) {
                    if (!_consented.value) return@collect
                    android.util.Log.d("Coach", "explain failed: ${e.message}")
                    _explanation.value = CoachExplanationState.Fallback(
                        reason = "O Coach não respondeu. Mostrando o resumo do motor.",
                        planRationale = plan.rationale,
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(_consented, sources.reviews.observeLastClosedWeek(), retryTick) { isConsented, review, tick ->
                Triple(isConsented, review, tick)
            }.collect { (isConsented, review, _) ->
                _review.value = review
                if (!isConsented || review == null) {
                    lastReviewHash = null
                    _reviewState.value = CoachReviewState.Unavailable
                    return@collect
                }
                val prompt = CoachReviewPromptBuilder.build(review)
                if (prompt.inputsHash == lastReviewHash && _reviewState.value is CoachReviewState.Ready) {
                    return@collect
                }
                _reviewState.value = CoachReviewState.Loading
                try {
                    val raw = api.review(prompt)
                    if (!_consented.value) return@collect
                    val validated = CoachResponseValidator.validate(
                        raw = raw,
                        allowedMinutes = CoachReviewPromptBuilder.allowedMinutes(prompt),
                        allowedPercents = CoachReviewPromptBuilder.allowedPercents(prompt),
                        forbiddenCodes = (prompt.observations + prompt.ruleIds).toSet(),
                    )
                    when (validated) {
                        is CoachValidationResult.Valid -> {
                            val response = validated.response
                            recordSafely {
                                memory.recordMessage(
                                    kind = CoachMessageKind.REVIEW,
                                    inputsHash = prompt.inputsHash,
                                    title = response.title,
                                    body = response.body,
                                    bullets = response.bullets,
                                )
                                memory.recordSummary(
                                    weekStart = review.weekStart,
                                    inputsHash = prompt.inputsHash,
                                    title = response.title,
                                    body = response.body,
                                    bullets = response.bullets,
                                )
                            }
                            lastReviewHash = prompt.inputsHash
                            android.util.Log.d("Coach", "review ok hash=${prompt.inputsHash}")
                            _reviewState.value = CoachReviewState.Ready(
                                title = response.title,
                                body = response.body,
                                bullets = response.bullets,
                            )
                        }

                        is CoachValidationResult.Invalid -> {
                            android.util.Log.d("Coach", "review rejected: ${validated.reason}")
                            _reviewState.value = CoachReviewState.Fallback(validated.reason)
                        }
                    }
                } catch (e: IOException) {
                    if (!_consented.value) return@collect
                    android.util.Log.d("Coach", "review io: ${e.message}")
                    _reviewState.value = CoachReviewState.Fallback("Sem conexão com o Coach.")
                } catch (e: Exception) {
                    if (!_consented.value) return@collect
                    android.util.Log.d("Coach", "review failed: ${e.message}")
                    _reviewState.value = CoachReviewState.Fallback("O Coach não respondeu.")
                }
            }
        }
    }

    /**
     * Next relevant event, read-only and local. A future day without a
     * persisted prescription surfaces with `prescription == null`; this never
     * computes or appends a prescription for a day that has not arrived
     * (ADR 0023). Consent only gates the AI explanation, never this card.
     * [retry] restarts the collection, so the error state can recover.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeNext() {
        viewModelScope.launch {
            retryTick.flatMapLatest {
                sources.next.observeNext()
                    .map { context -> toNextReady(context) }
                    .onStart { emit(CoachNextUiState.Loading) }
                    .catch { error ->
                        android.util.Log.d("Coach", "next failed: ${error.message}")
                        emit(CoachNextUiState.Error("Não foi possível carregar o próximo treino."))
                    }
            }.collect { _next.value = it }
        }
    }

    private fun toNextReady(context: CoachNextContext): CoachNextUiState = when {
        context.subject != null -> CoachNextUiState.Ready(context)
        !context.hasProfile || !context.hasPlan -> CoachNextUiState.Onboarding
        else -> CoachNextUiState.Empty
    }

    /**
     * Structural guards, read-only and local. Consent only gates the AI
     * explanation, never this card. [retry] restarts the collection, so the
     * error state can recover.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeGuards() {
        viewModelScope.launch {
            retryTick.flatMapLatest {
                sources.guards.observeGuards()
                    .map { context -> toGuardsReady(context) }
                    .onStart { emit(CoachGuardsUiState.Loading) }
                    .catch { error ->
                        android.util.Log.d("Coach", "guards failed: ${error.message}")
                        emit(CoachGuardsUiState.Error("Não foi possível carregar as travas da semana."))
                    }
            }.collect { _guards.value = it }
        }
    }

    private fun toGuardsReady(context: CoachGuardsContext): CoachGuardsUiState = CoachGuardsUiState.Ready(context)

    /**
     * Four-week rollup, read-only and local. Consent only gates the AI
     * explanation, never this card. [retry] restarts the collection, so the
     * error state can recover.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeProgress() {
        viewModelScope.launch {
            retryTick.flatMapLatest {
                sources.progress.observeProgress()
                    .map { context -> toProgressReady(context) }
                    .onStart { emit(CoachProgressUiState.Loading) }
                    .catch { error ->
                        android.util.Log.d("Coach", "progress failed: ${error.message}")
                        emit(CoachProgressUiState.Error("Não foi possível carregar sua evolução."))
                    }
            }.collect { _progress.value = it }
        }
    }

    private fun toProgressReady(context: CoachProgressContext): CoachProgressUiState = CoachProgressUiState.Ready(
        summary = CoachProgress.summarize(context.weeks, context.easyPercent28d),
        totalWeeks = context.weeks.size,
        profile = context.profile,
        today = context.today,
    )

    fun grant() {
        consent.grant()
        _consented.value = true
    }

    fun revoke() {
        consent.revoke()
        _consented.value = false
        _dismissed.value = false
        // App scope: the deletion must finish even if the tab/ViewModel goes away.
        appScope.launch {
            runCatching { memory.clearAll() }
                .onFailure { android.util.Log.d("Coach", "memory clear failed: ${it.message}") }
        }
    }

    fun dismiss() {
        _dismissed.value = true
    }

    /** Erases messages and summaries only; training data and consent are untouched. */
    fun clearHistory() {
        if (!_consented.value) return
        appScope.launch {
            runCatching { memory.clearAll() }
                .onFailure {
                    android.util.Log.d("Coach", "history clear failed: ${it.message}")
                    _history.value = CoachHistoryState.Error("Não foi possível apagar o histórico.")
                }
        }
    }

    /** Removes one unified timeline turn; training data is untouched. */
    fun deleteHistoryTurn(turn: ChatTurn) = deleteHistoryEntry {
        memory.deleteTurn(turn.id)
    }

    /**
     * Typed message from the composer (ADR 0056).
     *
     * The parser decides locally; the question is always persisted as a
     * `YOU` turn first. Deterministic intents answer without any network;
     * week/review questions call `free-answer` and only validated copy is
     * shown and persisted. Adjustments route to the existing replan machine
     * and never apply anything alone. One send at a time: while an exchange
     * is loading, new sends are ignored so answers can never overwrite each
     * other (ADR 0057 item 2).
     */
    fun sendFreeText(text: String, recordQuestion: Boolean = true) {
        if (!_consented.value) return
        if (_exchanges.value.any { it.answer is FreeAnswerUiState.Loading }) return
        val parsed = FreeInputParser.parse(text, LocalDate.now(clock).dayOfWeek)
        lastFreeText = text
        val exchangeId = UUID.randomUUID().toString()
        _exchanges.value =
            (_exchanges.value + FreeExchange(exchangeId, parsed.sanitized, FreeAnswerUiState.Loading))
                .takeLast(MAX_EXCHANGES)
        _freeAnswer.value = FreeAnswerUiState.Loading
        viewModelScope.launch {
            if (recordQuestion) {
                recordSafely { memory.recordYouTurn(parsed.sanitized) }
            }
            route(parsed, exchangeId)
        }
    }

    /** Ephemeral question echo for quick replies (item 2): in-flow, never persisted. */
    fun echoQuestion(text: String) {
        val clean = text.trim().take(FreeInputParser.MAX_CHARS)
        if (clean.isEmpty()) return
        if (_exchanges.value.lastOrNull()?.question == clean) return
        _exchanges.value =
            (_exchanges.value + FreeExchange(UUID.randomUUID().toString(), clean, FreeAnswerUiState.Idle))
                .takeLast(MAX_EXCHANGES)
    }

    /** Retries the latest failed exchange in place, without persisting the question twice. */
    fun retryFreeAnswer() {
        if (!_consented.value) return
        val text = lastFreeText ?: return
        val pending = _exchanges.value.lastOrNull { it.answer is FreeAnswerUiState.Fallback } ?: return
        if (_exchanges.value.any { it.answer is FreeAnswerUiState.Loading }) return
        viewModelScope.launch {
            val parsed = FreeInputParser.parse(text, LocalDate.now(clock).dayOfWeek)
            settle(pending.id, FreeAnswerUiState.Loading)
            route(parsed, pending.id)
        }
    }

    private suspend fun route(parsed: FreeInputParse, exchangeId: String) {
        when (parsed.intent) {
            FreeInputIntent.OUT_OF_SCOPE ->
                settle(exchangeId, FreeAnswerUiState.Fallback(FreeAnswerCopy.OUT_OF_SCOPE))

            FreeInputIntent.ASK_CLARIFY ->
                answerLocal(exchangeId, FreeAnswerCopy.CLARIFY, localHash("STATIC", parsed))

            FreeInputIntent.ASK_CAPABILITIES ->
                answerLocal(exchangeId, FreeAnswerCopy.CAPABILITIES, localHash("STATIC", parsed))

            FreeInputIntent.REPORT_DONE ->
                answerLocal(exchangeId, FreeAnswerCopy.REPORT_ACK, localHash("STATIC", parsed))

            FreeInputIntent.REQUEST_ADJUST -> {
                if (parsed.slots.minutes != null) requestLessTime() else requestReplan()
                answerLocal(exchangeId, FreeAnswerCopy.ADJUST_ACK, localHash("STATIC", parsed))
            }

            FreeInputIntent.ASK_WEEK -> answerWeek(parsed, exchangeId)

            FreeInputIntent.ASK_SESSION -> answerSession(parsed, SessionTask.WHAT, exchangeId)

            FreeInputIntent.ASK_HOW_TO_RUN -> answerSession(parsed, SessionTask.HOW_TO, exchangeId)

            FreeInputIntent.ASK_REVIEW -> answerReview(parsed, exchangeId)

            FreeInputIntent.ASK_PROGRESS -> answerProgress(parsed, exchangeId)
        }
    }

    /** Terminal state of one exchange, mirrored to the legacy single-answer state. */
    private fun settle(exchangeId: String, state: FreeAnswerUiState) {
        _exchanges.value = _exchanges.value.map { exchange ->
            if (exchange.id == exchangeId) exchange.copy(answer = state) else exchange
        }
        _freeAnswer.value = state
    }

    /**
     * Deterministic `Ready` answer (ADR 0057 item 3): shown and persisted as
     * an `ANSWER` turn with a stable local hash, so the archive never holds
     * a question without its answer. `Fallback` stays ephemeral.
     */
    private suspend fun answerLocal(exchangeId: String, copy: String, inputsHash: String) {
        recordLocalAnswer(inputsHash, copy)
        settle(exchangeId, FreeAnswerUiState.Ready(CoachBubbles.split(copy)))
    }

    private suspend fun recordLocalAnswer(inputsHash: String, copy: String) {
        val bubbles = CoachBubbles.split(copy)
        val title = bubbles.firstOrNull() ?: return
        val body = bubbles.drop(1).joinToString(" ").takeIf { it.isNotBlank() } ?: title
        recordSafely {
            memory.recordMessage(
                kind = CoachMessageKind.ANSWER,
                inputsHash = inputsHash,
                title = title,
                body = body,
                bullets = emptyList(),
            )
        }
    }

    private fun localHash(scope: String, parsed: FreeInputParse): String {
        val today = LocalDate.now(clock)
        return listOf("coach-free-v1", "local", scope, today.toString(), parsed.intent.name).joinToString("|")
    }

    private suspend fun answerWeek(parsed: FreeInputParse, exchangeId: String) {
        val context = sources.plans.observeCurrent().first()
        if (context == null) {
            settle(exchangeId, FreeAnswerUiState.Fallback(FreeAnswerCopy.NO_PLAN))
            return
        }
        val prompt = CoachPromptBuilder.build(context.plan, context.inputsHash)
        val allowed = prompt.sessions.mapNotNull { it.durationMin }.toSet() +
            prompt.weeklyMinutes +
            listOfNotNull(prompt.executedMinutes)
        freeAnswer(
            FreeAnswerRequest(
                parsed = parsed,
                exchangeId = exchangeId,
                contextHash = context.inputsHash,
                allowedMinutes = allowed,
                allowedPercents = emptySet(),
                allowedDistancesKm = emptySet(),
                forbiddenCodes = prompt.ruleIds.toSet(),
                engineContext = prompt.rationale,
            ),
        )
    }

    /**
     * Session-specific answer (ASK_SESSION / ASK_HOW_TO_RUN).
     *
     * Past, rest and out-of-week days answer locally without a model call:
     * instant, honest and free. Only a present/future `PLANNED` session goes
     * to `free-answer` with a session-scoped `engineContext`, so the reply
     * can only cite that session and the week total.
     */
    private suspend fun answerSession(parsed: FreeInputParse, task: SessionTask, exchangeId: String) {
        val planContext = sources.plans.observeCurrent().first()
        if (planContext == null) {
            settle(exchangeId, FreeAnswerUiState.Fallback(FreeAnswerCopy.NO_PLAN))
            return
        }
        val plan = planContext.plan
        val today = LocalDate.now(clock)
        val nextContext = sources.next.observeNext().first()
        val slotDay = parsed.slots.dayOfWeek
        val dayOffset = parsed.slots.dayOffset
        val target = if (dayOffset != null) {
            today.plusDays(dayOffset.toLong())
        } else if (slotDay != null) {
            CoachSessionAnswer.resolveDayInWeek(today, slotDay)
        } else {
            (nextContext.subject as? CoachNextSubject.Training)?.session?.date ?: today
        }
        val localCopy = localSessionCopy(plan, today, target, task)
        if (localCopy != null) {
            answerLocal(exchangeId, localCopy, localHash("SESSION", parsed) + "|$target")
            return
        }
        val session = plan.sessions.find { it.date == target } ?: return
        val prescribed = if (target == today && nextContext.isToday) nextContext.prescription else null
        val shortSleep = target == today &&
            ReadinessFlag.SHORT_SLEEP in (nextContext.readiness?.flags ?: emptySet())
        val answer = CoachSessionAnswer.build(
            today = today,
            target = target,
            sessions = plan.sessions,
            weeklyMinutes = plan.weeklyMinutes,
            task = task,
            prescription = prescribed,
            shortSleep = shortSleep,
            hasCheckIn = target == today && nextContext.hasCheckIn,
        )
        freeAnswer(
            FreeAnswerRequest(
                parsed = parsed,
                exchangeId = exchangeId,
                contextHash = "${planContext.inputsHash}|$target",
                allowedMinutes = answer.allowedMinutes,
                allowedPercents = emptySet(),
                allowedDistancesKm = answer.allowedDistancesKm,
                forbiddenCodes = (plan.ruleIds + plan.evidenceIds + session.ruleIds + session.evidenceIds).toSet(),
                engineContext = answer.engineContext,
                reference = CoachSessionReference.lines(session, prescribed),
            ),
        )
    }

    private suspend fun answerReview(parsed: FreeInputParse, exchangeId: String) {
        val review = sources.reviews.observeLastClosedWeek().first()
        if (review == null) {
            settle(exchangeId, FreeAnswerUiState.Fallback(FreeAnswerCopy.NO_REVIEW))
            return
        }
        val prompt = CoachReviewPromptBuilder.build(review)
        freeAnswer(
            FreeAnswerRequest(
                parsed = parsed,
                exchangeId = exchangeId,
                contextHash = prompt.inputsHash,
                allowedMinutes = CoachReviewPromptBuilder.allowedMinutes(prompt),
                allowedPercents = CoachReviewPromptBuilder.allowedPercents(prompt),
                allowedDistancesKm = emptySet(),
                forbiddenCodes = (prompt.observations + prompt.ruleIds).toSet(),
                engineContext = reviewLine(prompt),
            ),
        )
    }

    private suspend fun freeAnswer(request: FreeAnswerRequest) {
        val parsed = request.parsed
        val exchangeId = request.exchangeId
        val freeHash =
            "coach-free-v1|${request.contextHash}|${parsed.intent}|${parsed.slots.dayOfWeek}|${parsed.slots.minutes}"
        val allowedMinutes = request.allowedMinutes
        val allowedPercents = request.allowedPercents
        val allowedDistancesKm = request.allowedDistancesKm
        val forbiddenCodes = request.forbiddenCodes
        val engineContext = request.engineContext
        try {
            val prompt = CoachFreeAnswerPrompt(
                inputsHash = freeHash,
                intent = parsed.intent,
                dayOfWeek = parsed.slots.dayOfWeek,
                minutes = parsed.slots.minutes,
                allowedMinutes = allowedMinutes,
                allowedPercents = allowedPercents,
                allowedDistancesKm = allowedDistancesKm,
                engineContext = engineContext,
            )
            val raw = api.freeAnswer(prompt)
            if (!_consented.value) return
            val validated = CoachResponseValidator.validateFreeAnswer(
                raw = raw,
                allowedMinutes = allowedMinutes,
                allowedPercents = allowedPercents,
                allowedDistancesKm = allowedDistancesKm,
                forbiddenCodes = forbiddenCodes,
            )
            when (validated) {
                is CoachValidationResult.Valid -> {
                    val response = validated.response
                    val historyBullets = (response.bullets + request.reference).take(5)
                    recordSafely {
                        memory.recordMessage(
                            kind = CoachMessageKind.ANSWER,
                            inputsHash = freeHash,
                            title = response.title,
                            body = response.body,
                            bullets = historyBullets,
                        )
                    }
                    android.util.Log.d("Coach", "free answer ok hash=$freeHash")
                    settle(exchangeId, FreeAnswerUiState.Ready(CoachBubbles.split(response.body), request.reference))
                }

                is CoachValidationResult.Invalid -> {
                    android.util.Log.d("Coach", "free answer rejected: ${validated.reason}")
                    settle(
                        exchangeId,
                        FreeAnswerUiState.Fallback(CoachFallbackLabels.reasonCopy(validated.reason)),
                    )
                }
            }
        } catch (e: IOException) {
            if (!_consented.value) return
            android.util.Log.d("Coach", "free answer io: ${e.message}")
            settle(exchangeId, FreeAnswerUiState.Fallback(FreeAnswerCopy.NO_CONNECTION))
        } catch (e: Exception) {
            if (!_consented.value) return
            android.util.Log.d("Coach", "free answer failed: ${e.message}")
            settle(exchangeId, FreeAnswerUiState.Fallback(FreeAnswerCopy.GENERIC))
        }
    }

    private suspend fun answerProgress(parsed: FreeInputParse, exchangeId: String) {
        answerProgressContent(
            parsed = parsed,
            exchangeId = exchangeId,
            progress = sources.progress,
            settle = ::settle,
            send = ::freeAnswer,
        )
    }

    private fun reviewLine(prompt: CoachReviewPrompt): String = reviewSummaryLine(prompt)

    fun retryHistory() {
        historyRetryTick.value += 1
    }

    private fun deleteHistoryEntry(delete: suspend () -> Unit) {
        if (!_consented.value) return
        appScope.launch {
            runCatching { delete() }
                .onFailure {
                    android.util.Log.d("Coach", "history delete failed: ${it.message}")
                    _history.value = CoachHistoryState.Error("Não foi possível apagar este item.")
                }
        }
    }

    fun retry() {
        lastExplainContext = null
        lastReviewHash = null
        retryTick.value += 1
    }

    /**
     * Quick actions on the Coach tab evaluate T3 explicitly. "Ajustar a semana"
     * is the plain request; the other two open a picker and evaluate with an
     * explicit intent (3.3-B).
     */
    fun requestReplan() = replanController.replanNow()

    fun requestMissedSession() = replanController.requestMissedSession()

    fun replanMissed(date: LocalDate) = replanController.replanMissed(date)

    fun requestLessTime() = replanController.requestLessTime()

    fun replanLessTime(changes: List<LessTimeChange>) = replanController.replanLessTime(changes)

    fun dismissReplanIntent() = replanController.dismissIntent()

    fun acceptReplan() = replanController.accept()

    fun declineReplan() = replanController.decline()

    fun suppressReplan() = replanController.suppress()

    fun dismissReplanSuggestion() = replanController.dismissSuggestion()

    fun undoReplan() = replanController.undo()

    fun consumeReplanMessage() = replanController.consumeMessage()

    /** Memory failure must never break the tab: the engine copy stays on screen. */
    private inline fun recordSafely(block: () -> Unit) {
        if (!_consented.value) return
        runCatching(block).onFailure {
            android.util.Log.d("Coach", "memory write failed: ${it.message}")
        }
    }
}

private data class FreeAnswerRequest(
    val parsed: FreeInputParse,
    val exchangeId: String,
    val contextHash: String,
    val allowedMinutes: Set<Int>,
    val allowedPercents: Set<Int>,
    val allowedDistancesKm: Set<Double>,
    val forbiddenCodes: Set<String>,
    val engineContext: String,
    val reference: List<String> = emptyList(),
)

private suspend fun answerProgressContent(
    parsed: FreeInputParse,
    exchangeId: String,
    progress: CoachProgressSource,
    settle: (String, FreeAnswerUiState) -> Unit,
    send: suspend (FreeAnswerRequest) -> Unit,
) {
    val context = progress.observeProgress().first()
    val summary = CoachProgress.summarize(context.weeks, context.easyPercent28d)
    if (summary.availableWeeks == 0) {
        settle(exchangeId, FreeAnswerUiState.Fallback(FreeAnswerCopy.NO_PROGRESS))
        return
    }
    val answer = CoachProgressAnswer.build(summary)
    val forbidden = context.weeks.flatMap { it.ruleIds + it.evidenceIds }.toSet()
    send(
        FreeAnswerRequest(
            parsed = parsed,
            exchangeId = exchangeId,
            contextHash = "progress|${context.today}",
            allowedMinutes = answer.allowedMinutes,
            allowedPercents = answer.allowedPercents,
            allowedDistancesKm = emptySet(),
            forbiddenCodes = forbidden,
            engineContext = answer.engineContext,
        ),
    )
}

private fun reviewSummaryLine(prompt: CoachReviewPrompt): String {
    val done = prompt.adherence?.done
    val planned = prompt.adherence?.planned
    val percent = prompt.volume?.percentOfPlan
    return if (done != null && planned != null && percent != null) {
        "Semana ${prompt.weekStart}: $done de $planned treinos, $percent% do volume."
    } else {
        "Semana ${prompt.weekStart} fechada."
    }
}

/**
 * Days that never need a model call: out-of-week, past, rest or already
 * done. Returns the deterministic copy, or null when the question needs
 * the `free-answer` model path with the session context.
 */
private fun localSessionCopy(plan: WeeklyPlan, today: LocalDate, target: LocalDate, task: SessionTask): String? {
    val weekStart = CoachSessionAnswer.weekStart(today)
    val session = plan.sessions.find { it.date == target }
    val doneToday = target == today && session != null &&
        (session.status == SessionStatus.DONE || session.status == SessionStatus.CLOSED)
    return when {
        target.isBefore(weekStart) || target.isAfter(weekStart.plusDays(6)) -> FreeAnswerCopy.OUTSIDE_WEEK

        target.isBefore(today) -> FreeAnswerCopy.SESSION_PAST

        session == null -> if (task == SessionTask.HOW_TO) {
            FreeAnswerCopy.dayRestHowTo(dayLabel(today, target))
        } else {
            FreeAnswerCopy.dayRest(dayLabel(today, target))
        }

        doneToday -> FreeAnswerCopy.SESSION_DONE

        else -> null
    }
}

private fun dayLabel(today: LocalDate, target: LocalDate): String = when (target) {
    today -> "Hoje"
    today.plusDays(1) -> "Amanhã"
    else -> "Esse dia"
}

/**
 * Deterministic copy for the typed-message paths that need no model call
 * (ADR 0056). No numbers, no prescription, no diagnosis — just the next step.
 */
private const val MAX_EXCHANGES = 30
private object FreeAnswerCopy {
    const val OUT_OF_SCOPE =
        "Isso eu não faço por aqui — mas conto sua evolução, explico a semana e digo como correr. " +
            "Toca numa sugestão que eu organizo."
    const val CLARIFY = "Não entendi direito — me diz o dia e quantos minutos você tem?"
    const val CAPABILITIES =
        "Posso dizer o treino do dia, como correr, explicar a semana, contar sua evolução e ajustar. " +
            "É só me dizer — ou tocar numa sugestão."
    const val REPORT_ACK = "Boa! Marca lá no Hoje pra entrar na conta — por aqui eu sigo o plano."
    const val ADJUST_ACK = "Fechado, vamos ajustar. Confere as opções aqui embaixo."
    const val NO_PLAN = "Ainda estou montando sua semana — assim que o plano sair eu explico."
    const val NO_REVIEW = "A semana passada ainda não fechou por aqui — me pergunta de novo depois."
    const val NO_PROGRESS = "Ainda não tenho semanas fechadas por aqui — me pergunta de novo daqui a pouco."
    const val NO_CONNECTION = "Sem conexão com o Coach. O plano segue o mesmo — tá aqui embaixo."
    const val GENERIC = "O Coach não respondeu. O plano segue o mesmo — tá aqui embaixo."
    const val OUTSIDE_WEEK =
        "Esse dia é de outra semana — te mostro a semana atual ou como foi a passada?"
    const val SESSION_PAST =
        "Esse dia já passou por aqui — conta como foi lá no Hoje? Posso explicar a semana atual."
    const val SESSION_DONE = "Hoje já foi — boa! Marca lá no Hoje pra entrar na conta."

    fun dayRest(day: String): String =
        "$day é descanso — sem treino planejado. Dorme bem hoje que conta. Quer ver a semana?"

    fun dayRestHowTo(day: String): String =
        "$day é descanso — sem treino. Descanso é treino: sem rodar pra compensar. Dorme bem que conta."
}
