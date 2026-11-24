package com.meucoach.core.domain.coach

import java.time.DayOfWeek

/**
 * Intent of a free-text turn typed in the Coach tab (ADR 0056).
 *
 * The intent is the only thing that ever leaves the device: the proxy
 * receives `intent + slots + motor context`, never the raw text. Anything
 * the app refuses (diagnosis, new numbers, out-of-scope training) is
 * [OUT_OF_SCOPE] and gets an honest fallback; anything ambiguous is
 * [ASK_CLARIFY] with a single question back, never a guess.
 */
enum class FreeInputIntent {
    ASK_WEEK,
    ASK_SESSION,
    ASK_HOW_TO_RUN,
    ASK_REVIEW,
    ASK_PROGRESS,

    /** Open running/training question: answered with facts, never new numbers. */
    ASK_COACHING,
    REPORT_DONE,
    REQUEST_ADJUST,
    ASK_CAPABILITIES,
    ASK_CLARIFY,
    OUT_OF_SCOPE,
}

/** Validated slots extracted from the text; absent means the text had none. */
data class FreeInputSlots(
    val dayOfWeek: DayOfWeek? = null,
    val minutes: Int? = null,
    /**
     * Exact day offset when the text used a relative word (`ontem` = -1,
     * `hoje` = 0, `amanhã` = +1). Relative words resolve against the day
     * itself, never against the week layout: on a Sunday, "amanhã" is the
     * next Monday (outside the current week), not the Monday six days back.
     * Null means the day came from a weekday name (or there was no day).
     */
    val dayOffset: Int? = null,
)

/**
 * The parse result. [sanitized] is the trimmed, whitespace-collapsed text
 * capped at [FreeInputParser.MAX_CHARS] — the only string the repository may
 * later persist as the `YOU` turn body.
 */
data class FreeInputParse(
    val intent: FreeInputIntent,
    val slots: FreeInputSlots = FreeInputSlots(),
    val sanitized: String,
)

/**
 * Deterministic, on-device parser for the Coach free input (ADR 0056).
 *
 * Pure Kotlin/JVM: lowercase matching with diacritics stripped, ordered
 * checks (blank, then scope refusal, then one intent per group), no network,
 * no LLM. Safety first: scope refusal wins over any slot, and two matching
 * intents collapse to [FreeInputIntent.ASK_CLARIFY] instead of a guess.
 */
object FreeInputParser {
    /** Longest `YOU` body the timeline stores; anything longer is capped, not rejected. */
    const val MAX_CHARS = 500

    private val whitespace = Regex("\\s+")
    private val minutesPattern = Regex("""(\d{1,4})\s?min""")
    private val negation = Regex("""\b(nao|nem|nunca)\b""")

    private val scopePatterns = listOf(
        Regex("""\b(dor|doi|doeu|doendo|lesao|medico|diagnostico|remedio|suplemento|dieta|academia)\b"""),
        Regex("""\b(tendinite|canelite|torcao|entorse|fratura|cirurgia|batimento|inchaco|febre|tontura)\b"""),
        Regex("""\b(gripe|covid)\b"""),
        Regex("""\b(machuc|emagrec|muscula|fisioter|inflam|sangr|formig|gripad|resfriado|antibiotic|medicament)"""),
        Regex("""anti-inflamat"""),
        Regex("""(monta|monte|cria|crie|prescrev|planeja|planeje).*(treino|plano|planilha)"""),
    )

    private val adjustExplicit = listOf(
        Regex("""\b(perdi|perca)\b"""),
        Regex("""\b(mover|remarca|adiar|antecip|inverter)\b"""),
        Regex("""\b(viajo|viagem|compromisso)\b"""),
        Regex("""nao (vou conseguir|consigo|da|vai dar)"""),
        Regex("""(treino|corrida|correr|fazer).*nao (fiz|consegui|deu|terminei)"""),
        Regex("""nao (fiz|consegui|deu|terminei).*(treino|corrida|correr|fazer)"""),
    )
    private val timeShortage = Regex("""(so tenho|sem tempo|pouco tempo|menos tempo)""")

    private val reportPatterns = listOf(
        Regex("""\b(fiz|consegui|terminei|conclui|completei|rodei|corri)\b"""),
        Regex("""\b(dormi|sono)\b"""),
        Regex("""treino (feito|concluido|pago|ok)"""),
    )

    private val reviewPatterns = listOf(
        Regex("""semana passada"""),
        Regex("""ultima semana"""),
        Regex("""\b(revisao|resumo da semana)\b"""),
        Regex("""como (foi|ta|esta) (a |minha )?semana\b"""),
    )

    private val progressPatterns = listOf(
        Regex("""\bevolu"""),
        Regex("""\bprogresso\b"""),
        Regex("""\bmelhorando\b"""),
        Regex("""\bcomo (estou|anda)\b"""),
    )

    private val weekPatterns = listOf(
        Regex("""\b(explica|explicacao|minha semana)\b"""),
        Regex("""\bvolume\b.*\bsemana\b|\bsemana\b.*\bvolume\b"""),
    )

    private val howToPatterns = listOf(
        Regex("""\bcomo\s+(corro|correr|faco|fazer|devo|levar)\b"""),
        Regex("""\bcomo\s+e\b"""),
        Regex("""\bdica\b.*(correr|corrida|treino|amanha|hoje|ontem|longao|rodagem)"""),
        Regex("""\bdica\b.*(segunda|terca|quarta|quinta|sexta|sabado|domingo)"""),
        Regex("""(correr|corrida|treino|longao|rodagem).*\bdica\b"""),
        Regex("""\b(hoje|amanha|ontem)\b.*\bdica\b"""),
        Regex("""(segunda|terca|quarta|quinta|sexta|sabado|domingo|seg|ter|qua|qui|sex|sab|dom)\b.*\bdica\b"""),
        Regex("""\bestrategia\b.*(treino|corrida|prova|longao)"""),
        Regex("""\britmo\b.*(manter|levar|segurar)"""),
        Regex(
            """\b(pace|ritmo|fc|bpm|frequencia cardiaca)\b""" +
                """.*\b(hoje|amanha|ontem|treino|corrida|sessao|prova|longao)\b""",
        ),
        Regex(
            """\b(treino|corrida|sessao|prova|longao)\b""" +
                """.*\b(pace|ritmo|fc|bpm|frequencia cardiaca)\b""",
        ),
    )

    private fun takesSlots(intent: FreeInputIntent): Boolean = when (intent) {
        FreeInputIntent.REQUEST_ADJUST,
        FreeInputIntent.ASK_SESSION,
        FreeInputIntent.ASK_HOW_TO_RUN,
        FreeInputIntent.ASK_COACHING,
        -> true

        else -> false
    }

    /**
     * Free-text safety is local: diagnosis, nutrition and "create a plan" stay
     * out. Pace/FC/readiness *questions* are allowed to reach the model, but
     * the answer can only cite the deterministic facts (validator).
     */
    private val questionCues = listOf(
        Regex("""\?"""),
        Regex("""^(como|o que|por que|porque|qual|quais|quando|onde|posso|devo|vale|e melhor)\b"""),
        Regex("""\b(me explica|me conta|me ajuda|queria saber|quero saber|tira uma duvida)\b"""),
    )

    /**
     * A statement without a question mark only becomes an open answer when it
     * is clearly about running or the app's training context. This keeps
     * nonsense out of the model while leaving real questions free.
     */
    private val runningTopics = listOf(
        Regex("""\b(corr|treino|plano|semana|sessao|prova|longao|rodagem|interval|fartlek|tiro)\b"""),
        Regex("""\b(pace|ritmo|fc|bpm|frequencia|cardio|aerobi|aquec|along|esteira|subida|ladeira)\b"""),
        Regex("""\b(descanso|recuper|prontidao|sono|fadiga|cansaco|carga|volume|hidrat|calor|vento|chuva)\b"""),
        Regex("""\b(postura|respir|passada|cadencia|tenis|meta|objetivo|evolu|melhor)\b"""),
    )

    private val sessionPatterns = listOf(
        Regex("""\b(o que (tem|tinha)|qual (e|o) treino|tem treino|proximo treino)\b"""),
        Regex("""\btreino\b.*\b(hoje|amanha|ontem)\b"""),
        Regex("""\b(hoje|amanha|ontem)\b.*\b(treino|corrida|correr|longao|teve|foi)\b"""),
        Regex("""(hoje|amanha) tem"""),
        Regex("""treino de (hoje|amanha|ontem)"""),
        Regex("""(segunda|terca|quarta|quinta|sexta|sabado|domingo)\b.*(treino|corrida|tem|longao)"""),
        Regex("""(seg|ter|qua|qui|sex|sab|dom)\b.*(treino|corrida|tem|longao)"""),
        Regex("""(treino|corrida|longao).*(segunda|terca|quarta|quinta|sexta|sabado|domingo)"""),
    )

    private val capabilitiesPatterns = listOf(
        Regex("""o que voce (pode|faz|sabe|consegue)"""),
        Regex("""\b(ajuda|ajudar|como funciona|como usar)\b"""),
    )

    private data class WeekdayHit(val start: Int, val day: DayOfWeek)

    /**
     * Parses the typed text against [today] for relative day tokens
     * (`hoje`, `amanhã`, `ontem`).
     */
    fun parse(text: String, today: DayOfWeek): FreeInputParse {
        val sanitized = whitespace.replace(text.trim(), " ").take(MAX_CHARS)
        val normalized = normalizedKeywordForm(sanitized)
        val intent = resolveIntent(normalized)
        val slots = if (takesSlots(intent)) {
            extractSlots(normalized, today)
        } else {
            FreeInputSlots()
        }
        return FreeInputParse(intent, slots, sanitized)
    }

    private fun resolveIntent(normalized: String): FreeInputIntent {
        if (normalized.isEmpty()) return FreeInputIntent.ASK_CLARIFY
        if (scopePatterns.any { it.containsMatchIn(normalized) }) return FreeInputIntent.OUT_OF_SCOPE
        val flags = matchFlags(normalized)
        if (flags.matches >= 2) return FreeInputIntent.ASK_CLARIFY
        if (flags.adjust && needsClarification(normalized)) return FreeInputIntent.ASK_CLARIFY
        val decided = decideIntent(flags)
        if (decided != FreeInputIntent.OUT_OF_SCOPE) return decided
        return if (looksLikeQuestion(normalized) || runningTopics.any { it.containsMatchIn(normalized) }) {
            FreeInputIntent.ASK_COACHING
        } else {
            FreeInputIntent.OUT_OF_SCOPE
        }
    }

    private data class MatchFlags(
        val adjust: Boolean,
        val report: Boolean,
        val review: Boolean,
        val progress: Boolean,
        val training: Boolean,
        val howTo: Boolean,
        val session: Boolean,
        val week: Boolean,
        val capabilities: Boolean,
    ) {
        val matches: Int get() = listOf(adjust, report, review, progress, training, week, capabilities).count { it }
    }

    private fun matchFlags(normalized: String): MatchFlags {
        val explicitAdjust = adjustExplicit.any { it.containsMatchIn(normalized) }
        val howTo = !explicitAdjust && howToPatterns.any { it.containsMatchIn(normalized) }
        val session = !explicitAdjust && sessionPatterns.any { it.containsMatchIn(normalized) }
        return MatchFlags(
            adjust = explicitAdjust || timeShortage.containsMatchIn(normalized),
            report = !looksLikeQuestion(normalized) &&
                !negation.containsMatchIn(normalized) &&
                reportPatterns.any { it.containsMatchIn(normalized) },
            review = reviewPatterns.any { it.containsMatchIn(normalized) },
            progress = progressPatterns.any { it.containsMatchIn(normalized) },
            training = howTo || session,
            howTo = howTo,
            session = session,
            week = weekPatterns.any { it.containsMatchIn(normalized) },
            capabilities = capabilitiesPatterns.any { it.containsMatchIn(normalized) },
        )
    }

    private fun looksLikeQuestion(normalized: String): Boolean = questionCues.any { it.containsMatchIn(normalized) }

    private fun decideIntent(flags: MatchFlags): FreeInputIntent = when {
        flags.adjust -> FreeInputIntent.REQUEST_ADJUST
        flags.report -> FreeInputIntent.REPORT_DONE
        flags.review -> FreeInputIntent.ASK_REVIEW
        flags.progress -> FreeInputIntent.ASK_PROGRESS
        flags.howTo -> FreeInputIntent.ASK_HOW_TO_RUN
        flags.session -> FreeInputIntent.ASK_SESSION
        flags.week -> FreeInputIntent.ASK_WEEK
        flags.capabilities -> FreeInputIntent.ASK_CAPABILITIES
        else -> FreeInputIntent.OUT_OF_SCOPE
    }

    /**
     * A time-shortage ask without a usable number ("só tenho pouco tempo")
     * needs one question back; an explicit adjust ("perdi o treino") never does.
     */
    private fun needsClarification(normalized: String): Boolean {
        val explicit = adjustExplicit.any { it.containsMatchIn(normalized) }
        return !explicit && minutesOf(normalized) == null
    }

    private fun minutesOf(normalized: String): Int? =
        minutesPattern.find(normalized)?.groupValues?.get(1)?.toInt()?.takeIf { it in 5..600 }

    private fun extractSlots(normalized: String, today: DayOfWeek): FreeInputSlots {
        val minutes = minutesOf(normalized)
        val day = extractDay(normalized, today)
        return FreeInputSlots(dayOfWeek = day?.dayOfWeek, minutes = minutes, dayOffset = day?.offset)
    }

    private data class DaySlot(val dayOfWeek: DayOfWeek, val offset: Int?)

    private fun extractDay(normalized: String, today: DayOfWeek): DaySlot? {
        val relative = when {
            Regex("""\bhoje\b""").containsMatchIn(normalized) -> DaySlot(today, 0)
            Regex("""\bamanha\b""").containsMatchIn(normalized) -> DaySlot(today.plus(1), 1)
            Regex("""\bontem\b""").containsMatchIn(normalized) -> DaySlot(today.minus(1), -1)
            else -> null
        }
        if (relative != null) return relative
        return namedDay(normalized)?.let { DaySlot(it, null) }
    }

    private fun namedDay(normalized: String): DayOfWeek? = listOf(
        "segunda" to DayOfWeek.MONDAY,
        "seg" to DayOfWeek.MONDAY,
        "terca" to DayOfWeek.TUESDAY,
        "ter" to DayOfWeek.TUESDAY,
        "quarta" to DayOfWeek.WEDNESDAY,
        "qua" to DayOfWeek.WEDNESDAY,
        "quinta" to DayOfWeek.THURSDAY,
        "qui" to DayOfWeek.THURSDAY,
        "sexta" to DayOfWeek.FRIDAY,
        "sex" to DayOfWeek.FRIDAY,
        "sabado" to DayOfWeek.SATURDAY,
        "sab" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY,
        "dom" to DayOfWeek.SUNDAY,
    )
        .mapNotNull { (token, day) ->
            Regex("""\b$token\b""").find(normalized)?.let { WeekdayHit(it.range.first, day) }
        }
        .minByOrNull { it.start }
        ?.day
}
