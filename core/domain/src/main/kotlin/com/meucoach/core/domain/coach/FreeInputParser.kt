package com.meucoach.core.domain.coach

import java.text.Normalizer
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
    ASK_REVIEW,
    REPORT_DONE,
    REQUEST_ADJUST,
    ASK_CAPABILITIES,
    ASK_CLARIFY,
    OUT_OF_SCOPE,
}

/** Validated slots extracted from the text; absent means the text had none. */
data class FreeInputSlots(val dayOfWeek: DayOfWeek? = null, val minutes: Int? = null)

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
        Regex("""\b(dor|doi|doendo|lesao|machuc|medico|diagnostico|remedio|suplemento)\b"""),
        Regex("""\b(dieta|emagrec|muscula|academia|fisioter|tendinite|canelite|inflam)\b"""),
        Regex("""\b(torcao|entorse|fratura|cirurgia|batimento)\b"""),
        Regex("""\b(pace|vdot|vo2|bpm)\b"""),
        Regex("""\bfc\b"""),
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
        Regex("""\b(revisao|resumo da semana|como foi)\b"""),
    )

    private val weekPatterns = listOf(
        Regex("""(porque|por que).*(hoje|treino|facil|forte|descanso)"""),
        Regex("""\b(o que tem|qual (e|o) treino|explica|explicacao|minha semana|proximo treino)\b"""),
        Regex("""(hoje|amanha) tem"""),
        Regex("""treino de (hoje|amanha)"""),
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
        val normalized = stripAccents(sanitized.lowercase())
        val intent = resolveIntent(normalized)
        val slots = if (intent == FreeInputIntent.REQUEST_ADJUST) extractSlots(normalized, today) else FreeInputSlots()
        return FreeInputParse(intent, slots, sanitized)
    }

    private fun resolveIntent(normalized: String): FreeInputIntent {
        if (normalized.isEmpty()) return FreeInputIntent.ASK_CLARIFY
        if (scopePatterns.any { it.containsMatchIn(normalized) }) return FreeInputIntent.OUT_OF_SCOPE
        val adjust = adjustExplicit.any { it.containsMatchIn(normalized) } || timeShortage.containsMatchIn(normalized)
        val report = !negation.containsMatchIn(normalized) && reportPatterns.any { it.containsMatchIn(normalized) }
        val review = reviewPatterns.any { it.containsMatchIn(normalized) }
        val week = weekPatterns.any { it.containsMatchIn(normalized) }
        val capabilities = capabilitiesPatterns.any { it.containsMatchIn(normalized) }
        val matches = listOf(adjust, report, review, week, capabilities).count { it }
        return when {
            matches >= 2 -> FreeInputIntent.ASK_CLARIFY
            adjust && needsClarification(normalized) -> FreeInputIntent.ASK_CLARIFY
            adjust -> FreeInputIntent.REQUEST_ADJUST
            report -> FreeInputIntent.REPORT_DONE
            review -> FreeInputIntent.ASK_REVIEW
            week -> FreeInputIntent.ASK_WEEK
            capabilities -> FreeInputIntent.ASK_CAPABILITIES
            else -> FreeInputIntent.OUT_OF_SCOPE
        }
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
        val named = listOf(
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
        val relative = when {
            Regex("""\bhoje\b""").containsMatchIn(normalized) -> today
            Regex("""\bamanha\b""").containsMatchIn(normalized) -> today.plus(1)
            Regex("""\bontem\b""").containsMatchIn(normalized) -> today.minus(1)
            else -> null
        }
        return FreeInputSlots(dayOfWeek = relative ?: named, minutes = minutes)
    }

    private fun stripAccents(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
}
