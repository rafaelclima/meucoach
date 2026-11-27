package com.meucoach.core.domain.content

import org.assertj.core.api.Assertions.assertThat

/** One user-facing string plus the code location that produced it. */
internal data class CheckedText(val origin: String, val text: String?)

/**
 * Vocabulary gate without baseline (ADR 0071 F9).
 *
 * The F1–F8 baselines are gone: every known violation was fixed in its own
 * fatia and the baseline files were deleted. From here on any forbidden term
 * in user text fails the build. Fix the text in its owning fatia, never
 * loosen the gate. [scopePrefix] only scopes the failure message to the
 * caller's own origins (one source file or one fixture).
 */
internal object VocabularyGate {
    fun assertClean(texts: List<CheckedText>, scopePrefix: String) {
        val found = texts.flatMap { checked ->
            val text = checked.text ?: return@flatMap emptyList()
            ForbiddenVocabulary.findViolations(text).map { violation ->
                "${checked.origin} | ${violation.term.display} | ${violation.excerpt}"
            }
        }
        assertThat(found)
            .describedAs(
                "Vocabulary violations in $scopePrefix. F9 has no baseline: " +
                    "fix the text in its fatia, never loosen the gate.",
            )
            .isEmpty()
    }
}
