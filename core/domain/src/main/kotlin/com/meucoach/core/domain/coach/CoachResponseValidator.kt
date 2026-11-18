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

    fun validate(raw: CoachRawResponse, allowedMinutes: Set<Int>): CoachValidationResult {
        if (raw.title.isBlank() || raw.body.isBlank()) {
            return CoachValidationResult.Invalid("Título e corpo são obrigatórios")
        }
        val combined = (
            listOf(
                raw.title,
                raw.body,
            ) + raw.bullets + listOfNotNull(raw.suggestedAction)
            ).joinToString("\n")
        if (pacePattern.containsMatchIn(combined)) {
            return CoachValidationResult.Invalid("Resposta contém pace, que só o motor pode prescrever")
        }
        if (bpmPattern.containsMatchIn(combined)) {
            return CoachValidationResult.Invalid("Resposta contém FC, que só o motor pode prescrever")
        }
        val lower = combined.lowercase()
        val forbidden = forbiddenTokens.firstOrNull { it in lower }
        if (forbidden != null) {
            return CoachValidationResult.Invalid("Resposta contém métrica proibida: $forbidden")
        }
        val invented = minutesPattern.findAll(combined)
            .map { it.groupValues[1].toInt() }
            .firstOrNull { it !in allowedMinutes }
        if (invented != null) {
            return CoachValidationResult.Invalid("Resposta contém duração não planejada: $invented min")
        }
        return CoachValidationResult.Valid(
            CoachResponse(
                title = raw.title.trim(),
                body = raw.body.trim(),
                bullets = raw.bullets.map { it.trim() }.filter { it.isNotEmpty() }.take(5),
                suggestedAction = raw.suggestedAction?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }
}
