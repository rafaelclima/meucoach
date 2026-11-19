package com.meucoach.core.domain.coach

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
    private val forbiddenTokens = listOf("vdot", "acwr", "tss", "trimp", "ctl", "atl", "tsb", "lthr", "vo2")
    private val minutesPattern = Regex("""(\d{1,4})\s?min""", RegexOption.IGNORE_CASE)
    private val percentPattern = Regex("""(\d{1,3})\s?%""")
    private val internalCodePattern = Regex("""\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\b""")

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

    private fun invalidReason(
        combined: String,
        allowedMinutes: Set<Int>,
        allowedPercents: Set<Int>?,
        forbiddenCodes: Set<String>,
    ): String? {
        val forbidden = forbiddenTokens.firstOrNull { it in combined.lowercase() }
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
