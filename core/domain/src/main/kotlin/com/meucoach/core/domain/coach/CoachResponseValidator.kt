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
    private val internalCodePattern = Regex("""\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\b""")

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
        listOf("dor", "dores", "doendo", "doeu", "febre", "tontura", "desmaio", "vertigem")
            .map { token -> token to Regex("""\b${Regex.escape(token)}\b""") } +
            listOf(
                "doenca",
                "doente",
                "sintoma",
                "lesao",
                "tendinite",
                "canelite",
                "medico",
                "diagnostico",
                "remedio",
                "medicament",
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
                "inchac",
                "sangr",
                "formig",
                "gripad",
                "gripe",
                "covid",
                "antibiotic",
                "anti-inflamat",
                "arritmia",
                "palpit",
                "cardiac",
                "cardiopat",
                "infarto",
                "avc",
                "derrame",
                "desmai",
                "hipertens",
            ).map { prefix -> prefix to Regex("""\b${Regex.escape(prefix)}""") } +
            listOf(
                "falta de ar" to Regex("""\bfalta de ar\b"""),
                "sem ar" to Regex("""\bsem ar\b"""),
                "nao consigo respirar" to Regex("""\bnao consigo respirar\b"""),
                "aperto no peito" to Regex("""\b(aperto|apertou|apertando|peso|queimacao)\b.*\bpeito\b"""),
            )
    private val memoryPromises =
        listOf("vou lembrar", "nao vou esquecer", "anotei", "memorizei", "jamais esqueco")
            .map { phrase -> phrase to Regex(Regex.escape(phrase)) }

    /**
     * [allowed] carries the typed facts the caller computed: minutes,
     * percentages, distances, counts and decimals. An empty category means no
     * number of that kind exists in the dossier; it is never an unchecked
     * wildcard (COACH-10/U-1). The explain and review paths pass explicit sets;
     * the free-answer path adds its own distances and counts.
     *
     * [forbiddenCodes] bars the prompt's internal codes (observation kinds and
     * rule ids, e.g. `INTENSITY_ABOVE_EASY_TARGET`, `R-REVIEW-01`): the UI
     * shows prose, never engine identifiers (PRD IA Coach, decisão 22). The
     * match is case-insensitive and any SCREAMING_SNAKE token is barred even
     * when the caller forgets the set.
     */
    fun validate(
        raw: CoachRawResponse,
        allowed: CoachNumberAllowList,
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
        val reason = invalidReason(combined, allowed, forbiddenCodes)
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
     * Free-answer path of the validator (ADR 0056 §2.2, refined by ADR 0057
     * and ADR 0063): every base rule still applies, and the response may only
     * cite durations, distances, shares and counts already present in the
     * current facts. A prescription verb dies with an uncovered number
     * ("faça 6 repetições") or a structural change ("troque por descanso"),
     * never for citing a planned duration ("os 40 min"). Medical/scope lexicon
     * and memory promises are `Invalid`. Both paths share the same typed
     * number allow-list; free-answer only adds its structural/medical rules.
     */
    fun validateFreeAnswer(
        raw: CoachRawResponse,
        allowed: CoachNumberAllowList,
        forbiddenCodes: Set<String> = emptySet(),
    ): CoachValidationResult {
        val base = validate(raw, allowed, forbiddenCodes)
        if (base is CoachValidationResult.Invalid) return base
        val normalized = normalizedKeywordForm(
            (listOf(raw.title, raw.body) + raw.bullets + listOfNotNull(raw.suggestedAction)).joinToString("\n"),
        )
        val reason = freeAnswerReason(normalized) ?: return base
        return CoachValidationResult.Invalid(reason)
    }

    private fun freeAnswerReason(normalized: String): String? {
        val prescribes = prescriptionVerbs.any { (_, pattern) -> pattern.containsMatchIn(normalized) } &&
            (
                structuralTokens.any { (_, pattern) -> pattern.containsMatchIn(normalized) } ||
                    hasPrescriptionNumber(normalized)
                )
        val outOfScope = medicalTokens.any { (_, pattern) -> pattern.containsMatchIn(normalized) }
        val memoryPromise = memoryPromises.any { (_, pattern) -> pattern.containsMatchIn(normalized) }
        return when {
            outOfScope -> "Resposta fora do escopo do Coach"
            prescribes -> "Resposta sugere treino novo — ajuste só com sua confirmação"
            memoryPromise -> "Resposta promete memória que o Coach não tem"
            else -> null
        }
    }

    /**
     * Any bare digit next to a prescription verb is a dose the model invented:
     * the unit ("40 min") is the minimum needed, and only when the motor
     * planned it. Typed tokens (minutes, percents, distances) never count as a
     * bare dose; an allowed count still cannot authorize "faça 6 repetições".
     */
    private fun hasPrescriptionNumber(normalized: String): Boolean {
        val typed = typedRanges(normalized)
        return CoachNumberPatterns.bareNumber.findAll(normalized).any { match ->
            typed.none { span -> match.range.first in span && match.range.last in span }
        }
    }

    private fun typedRanges(text: String): List<IntRange> = buildList {
        CoachNumberPatterns.minutes.findAll(text).forEach { add(it.range) }
        CoachNumberPatterns.percent.findAll(text).forEach { add(it.range) }
        CoachNumberPatterns.distanceKm.findAll(text).forEach { add(it.range) }
    }

    private fun invalidReason(combined: String, allowed: CoachNumberAllowList, forbiddenCodes: Set<String>): String? {
        val forbidden = forbiddenTokens
            .firstOrNull { (_, pattern) -> pattern.containsMatchIn(combined) }
            ?.first
        val internalCode = internalCodePattern.find(combined)?.value
            ?: forbiddenCodes.firstOrNull { it.lowercase() in combined.lowercase() }
        val inventedMinutes = CoachNumberPatterns.minutes.findAll(combined)
            .map { it.groupValues[1].toInt() }
            .firstOrNull { it !in allowed.minutes }
        val inventedPercent = CoachNumberPatterns.percent.findAll(combined)
            .map { it.groupValues[1].toInt() }
            .firstOrNull { it !in allowed.percents }
        val inventedDistance = CoachNumberPatterns.distanceKm.findAll(combined)
            .map { it.groupValues[1].replace(',', '.').toDouble() }
            .firstOrNull { candidate -> allowed.distancesKm.none { abs(it - candidate) < 1e-6 } }
        val uncoveredNumber = uncoveredBareNumber(combined, allowed)
        return when {
            pacePattern.containsMatchIn(combined) ->
                "Resposta contém pace, que só o motor pode prescrever"

            bpmPattern.containsMatchIn(combined) ->
                "Resposta contém FC, que só o motor pode prescrever"

            forbidden != null -> "Resposta contém métrica proibida: $forbidden"

            internalCode != null -> "Resposta contém código interno: $internalCode"

            inventedMinutes != null -> "Resposta contém duração não planejada: $inventedMinutes min"

            inventedPercent != null -> "Resposta contém porcentagem não calculada: $inventedPercent%"

            inventedDistance != null ->
                "Resposta contém distância fora do plano: ${formatNumber(inventedDistance)} km"

            uncoveredNumber != null -> "Resposta contém número fora do contexto: $uncoveredNumber"

            else -> null
        }
    }

    /**
     * Any bare number the answer cites must be an allowed count (integer) or
     * an allowed decimal. Numbers that belong to a typed token (minutes,
     * percent, distance) are already checked by their own category.
     */
    private fun uncoveredBareNumber(text: String, allowed: CoachNumberAllowList): String? {
        val typed = typedRanges(text)
        return CoachNumberPatterns.bareNumber.findAll(text)
            .filterNot { match -> typed.any { span -> match.range.first in span && match.range.last in span } }
            .mapNotNull { match ->
                val value = match.value.replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
                if (value == value.toInt().toDouble()) {
                    value.toInt().takeIf { it !in allowed.counts }?.toString()
                } else {
                    match.value.takeUnless { candidate ->
                        allowed.decimals.any { abs(it - value) < 1e-6 }
                    }
                }
            }
            .firstOrNull()
    }

    private fun formatNumber(value: Double): String = if (value == value.toInt().toDouble()) {
        value.toInt().toString()
    } else {
        value.toString().replace('.', ',')
    }
}
