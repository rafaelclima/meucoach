package com.meucoach.core.domain.coach

/**
 * Numeric tokens shared by the free-answer validator and the open dossier.
 *
 * The validator uses them to bar any number the answer was not allowed to
 * cite; [CoachOpenAnswer] uses the same patterns to make every number it
 * writes into `engineContext` citable. Keeping one definition avoids the
 * dossier and the allow-lists drifting apart (a number printed by the engine
 * but rejected in the answer).
 */
internal object CoachNumberPatterns {
    val minutes = Regex("""(\d{1,4})\s?min""", RegexOption.IGNORE_CASE)
    val percent = Regex("""(\d{1,3})\s?%""")
    val distanceKm = Regex("""(\d+(?:[.,]\d+)?)\s?km""", RegexOption.IGNORE_CASE)
    val bareNumber = Regex("""\d+(?:[.,]\d+)?""")
}
