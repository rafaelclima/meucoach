package com.meucoach.core.domain.coach

import kotlin.math.abs

data class CoachRawResponse(
    val title: String,
    val body: String,
    val bullets: List<String> = emptyList(),
    val suggestedAction: String? = null,
)

data class CoachResponse(
    val title: String,
    val body: String,
    val bullets: List<String>,
    val suggestedAction: String? = null,
)

sealed interface CoachValidationResult {
    data class Valid(val response: CoachResponse) : CoachValidationResult

    data class Invalid(val reason: String) : CoachValidationResult
}

object CoachResponseValidator {
    private val pacePattern = Regex("""\d{1,2}:\d{2}(/km)?""")
    private val bpmPattern = Regex("""\d{2,3}\s?bpm""", RegexOption.IGNORE_CASE)

    /**
     * Absurd-length guard for the chat surface (PRD_COACH_CHAT F2). Real
     * narratives stay far below this; a runaway completion must fall back
     * instead of flooding the timeline. Brevity itself (3 short bubbles) is
     * enforced by the system prompts, not here.
     */
    const val MAX_TOTAL_CHARS = 2000

    /**
     * Tokens that only exist as metrics. The word boundary keeps "atl" out of
     * "atleta"/"atletas" and "ctl" out of "controle" (P-10), while still
     * rejecting the standalone acronyms.
     */
    private val forbiddenTokens = listOf("vdot", "acwr", "tss", "trimp", "ctl", "atl", "tsb", "lthr", "vo2")
        .map { token -> token to Regex("""\b${Regex.escape(token)}\b""", RegexOption.IGNORE_CASE) }
    private val minutesPattern = Regex("""(\d{1,4})\s?min""", RegexOption.IGNORE_CASE)
    private val percentPattern = Regex("""(\d{1,3})\s?%""")
    private val internalCodePattern = Regex("""\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\b""")
    private val distancePattern = Regex("""(\d+(?:[.,]\d+)?)\s?km""", RegexOption.IGNORE_CASE)
    private val bareNumberPattern = Regex("""\d+(?:[.,]\d+)?""")

    /**
     * Normalized (lowercase, no accents) tokens for the free-answer path.
     * Prescription verbs only matter with an uncovered number or a
     * structural change; citing a planned duration ("faça os 40 min") is
     * guidance, inventing load ("faça 6 repetições") is prescription.
     * Plain rest-day rewording ("descanse bem") has neither and still passes.
     */
    private val prescriptionVerbs =
        listOf(
            "faca",
            "corra",
            "troque",
            "descanse",
            "aumente",
            "reduza",
            "pule",
            "corte",
            "adicione",
            "inclua",
            "repita",
        ).map { verb -> verb to Regex("""\b${Regex.escape(verb)}\b""") }
    private val structuralTokens =
        listOf("descanso", "pule", "adiciona", "corta", "move", "adiar", "antecipa")
            .map { token -> token to Regex("""\b${Regex.escape(token)}\b""") }
    private val medicalTokens =
        listOf("dor", "dores", "doendo")
            .map { token -> token to Regex("""\b${Regex.escape(token)}\b""") } +
            listOf(
                "doenca",
                "lesao",
                "tendinite",
                "canelite",
                "medico",
                "diagnostico",
                "remedio",
                "suplemento",
                "dieta",
                "muscula",
                "academia",
                "fisioter",
                "torcao",
                "entorse",
                "fratura",
                "cirurgia",
                "inflam",
                "emagrec",
            ).map { prefix -> prefix to Regex("""\b${Regex.escape(prefix)}""") }
    private val memoryPromises =
        listOf("vou lembrar", "nao vou esquecer", "anotei", "memorizei", "jamais esqueco")
            .map { phrase -> phrase to Regex(Regex.escape(phrase)) }

    /**
     * [allowedPercents] enables the share check used by the weekly review: when
     * the caller supplies the computed shares, any other percentage is an
     * invention. `null` keeps the explain behaviour unchanged.
     *
     * [forbiddenCodes] bars the prompt's internal codes (observation kinds and
     * rule ids, e.g. `INTENSITY_ABOVE_EASY_TARGET`, `R-REVIEW-01`): the UI
     * shows prose, never engine identifiers (PRD IA Coach, decisão 22). The
     * match is case-insensitive and any SCREAMING_SNAKE token is barred even
     * when the caller forgets the set.
     */
    fun validate(
        raw: CoachRawResponse,
        allowedMinutes: Set<Int>,
        allowedPercents: Set<Int>? = null,
        forbiddenCodes: Set<String> = emptySet(),
    ): CoachValidationResult {
        if (raw.title.isBlank() || raw.body.isBlank()) {
            return CoachValidationResult.Invalid("Título e corpo são obrigatórios")
        }
        if (raw.title.length + raw.body.length + raw.bullets.sumOf { it.length } > MAX_TOTAL_CHARS) {
            return CoachValidationResult.Invalid("Resposta longa demais para o chat")
        }
        val combined = (
            listOf(
                raw.title,
                raw.body,
            ) + raw.bullets + listOfNotNull(raw.suggestedAction)
            ).joinToString("\n")
        val reason = invalidReason(combined, allowedMinutes, allowedPercents, forbiddenCodes)
        if (reason != null) return CoachValidationResult.Invalid(reason)
        return CoachValidationResult.Valid(
            CoachResponse(
                title = raw.title.trim(),
                body = raw.body.trim(),
                bullets = raw.bullets.map { it.trim() }.filter { it.isNotEmpty() }.take(5),
                suggestedAction = raw.suggestedAction?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    /**
     * Free-answer path of the validator (ADR 0056 §2.2, refinado na ADR 0057):
     * every base rule still applies, and the response may only cite
     * durations, distances and shares already present in the current
     * prescription. A prescription verb dies with an uncovered number
     * ("faça 6 repetições") or a structural change ("troque por descanso"),
     * never for citing a planned duration ("os 40 min"). Medical/scope
     * lexicon and memory promises are `Invalid`. The legacy `validate`
     * path is untouched.
     */
    fun validateFreeAnswer(
        raw: CoachRawResponse,
        allowedMinutes: Set<Int>,
        allowedPercents: Set<Int>,
        allowedDistancesKm: Set<Double>,
        forbiddenCodes: Set<String> = emptySet(),
    ): CoachValidationResult {
        val base = validate(raw, allowedMinutes, allowedPercents, forbiddenCodes)
        if (base is CoachValidationResult.Invalid) return base
        val normalized = normalizedKeywordForm(
            (listOf(raw.title, raw.body) + raw.bullets + listOfNotNull(raw.suggestedAction)).joinToString("\n"),
        )
        val reason = freeAnswerReason(normalized, allowedMinutes, allowedPercents, allowedDistancesKm) ?: return base
        return CoachValidationResult.Invalid(reason)
    }

    private fun freeAnswerReason(
        normalized: String,
        allowedMinutes: Set<Int>,
        allowedPercents: Set<Int>,
        allowedDistancesKm: Set<Double>,
    ): String? {
        val inventedDistance = distancePattern.findAll(normalized)
            .map { it.groupValues[1].replace(',', '.').toDouble() }
            .firstOrNull { candidate -> allowedDistancesKm.none { abs(it - candidate) < 1e-6 } }
        val prescribes = prescriptionVerbs.any { (_, pattern) -> pattern.containsMatchIn(normalized) } &&
            (
                structuralTokens.any { (_, pattern) -> pattern.containsMatchIn(normalized) } ||
                    hasUncoveredNumber(normalized, allowedMinutes, allowedPercents, allowedDistancesKm)
                )
        val outOfScope = medicalTokens.any { (_, pattern) -> pattern.containsMatchIn(normalized) }
        val memoryPromise = memoryPromises.any { (_, pattern) -> pattern.containsMatchIn(normalized) }
        return when {
            inventedDistance != null -> "Resposta contém distância fora do plano: $inventedDistance km"
            prescribes -> "Resposta sugere treino novo — ajuste só com sua confirmação"
            outOfScope -> "Resposta fora do escopo do Coach"
            memoryPromise -> "Resposta promete memória que o Coach não tem"
            else -> null
        }
    }

    /**
     * Any digit the answer may not cite: numbers with a unit must be covered
     * by the allowed sets, and bare digits next to a prescription verb never
     * pass — the model must write the unit ("40 min"), and even then only
     * when the motor planned it.
     */
    private fun hasUncoveredNumber(
        normalized: String,
        allowedMinutes: Set<Int>,
        allowedPercents: Set<Int>,
        allowedDistancesKm: Set<Double>,
    ): Boolean {
        val covered = buildList {
            minutesPattern.findAll(normalized).forEach { match ->
                if (match.groupValues[1].toInt() in allowedMinutes) add(match.range)
            }
            percentPattern.findAll(normalized).forEach { match ->
                if (match.groupValues[1].toInt() in allowedPercents) add(match.range)
            }
            distancePattern.findAll(normalized).forEach { match ->
                val value = match.groupValues[1].replace(',', '.').toDouble()
                if (allowedDistancesKm.any { abs(it - value) < 1e-6 }) add(match.range)
            }
        }
        return bareNumberPattern.findAll(normalized).any { match ->
            covered.none { span -> match.range.first in span && match.range.last in span }
        }
    }

    private fun invalidReason(
        combined: String,
        allowedMinutes: Set<Int>,
        allowedPercents: Set<Int>?,
        forbiddenCodes: Set<String>,
    ): String? {
        val forbidden = forbiddenTokens
            .firstOrNull { (_, pattern) -> pattern.containsMatchIn(combined) }
            ?.first
        val internalCode = internalCodePattern.find(combined)?.value
            ?: forbiddenCodes.firstOrNull { it.lowercase() in combined.lowercase() }
        val inventedMinutes = minutesPattern.findAll(combined)
            .map { it.groupValues[1].toInt() }
            .firstOrNull { it !in allowedMinutes }
        val inventedPercent = allowedPercents?.let { allowed ->
            percentPattern.findAll(combined)
                .map { it.groupValues[1].toInt() }
                .firstOrNull { it !in allowed }
        }
        return when {
            pacePattern.containsMatchIn(combined) ->
                "Resposta contém pace, que só o motor pode prescrever"

            bpmPattern.containsMatchIn(combined) ->
                "Resposta contém FC, que só o motor pode prescrever"

            forbidden != null -> "Resposta contém métrica proibida: $forbidden"

            internalCode != null -> "Resposta contém código interno: $internalCode"

            inventedMinutes != null -> "Resposta contém duração não planejada: $inventedMinutes min"

            inventedPercent != null -> "Resposta contém porcentagem não calculada: $inventedPercent%"

            else -> null
        }
    }
}
