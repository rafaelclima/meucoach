package com.meucoach.app.ui.content

import org.assertj.core.api.Assertions.assertThat

/** One user-facing string plus the code location that produced it. */
internal data class CheckedText(val origin: String, val text: String?)

/**
 * Vocabulary gate without baseline for the UI layer (ADR 0071 F9).
 *
 * Same contract as the domain gate: the F1–F8 baselines are gone and any
 * forbidden term fails the build. [scopePrefix] only scopes the failure
 * message to the caller's own file or folder.
 */
internal object UiVocabularyGate {
    fun assertClean(texts: List<CheckedText>, scopePrefix: String) {
        val found = texts.flatMap { checked ->
            val text = checked.text ?: return@flatMap emptyList()
            com.meucoach.core.domain.content.ForbiddenVocabulary.findViolations(text).map { violation ->
                "${checked.origin} | ${violation.term.display} | ${violation.excerpt}"
            }
        }
        assertThat(found)
            .describedAs(
                "UI vocabulary violations in $scopePrefix. F9 has no baseline: " +
                    "fix the text in its fatia, never loosen the gate.",
            )
            .isEmpty()
    }
}
