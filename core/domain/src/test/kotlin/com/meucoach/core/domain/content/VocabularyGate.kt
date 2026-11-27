package com.meucoach.core.domain.content

import org.assertj.core.api.Assertions.assertThat

/** One user-facing string plus the code location that produced it. */
internal data class CheckedText(val origin: String, val text: String?)

internal data class BaselineEntry(val origin: String, val term: String, val excerpt: String)

/**
 * Baseline-backed vocabulary gate (ADR 0071 F1).
 *
 * The baseline file lives next to the tests (`content-vocabulary-baseline.txt`)
 * with one line per known violation:
 * `origin | term | excerpt | fatia`.
 *
 * - `origin` is the repo-relative path plus the producing context;
 * - `term` is a display name from [ForbiddenVocabulary.DISPLAYS];
 * - `excerpt` is the [VocabularyViolation.excerpt] window;
 * - `fatia` (`F2`–`F9`) points at the work-list slice that removes the line.
 *
 * The test fails on any violation missing from the baseline and on any
 * baseline entry that no longer matches, so the baseline can only shrink.
 * [scopePrefix] scopes the stale check to the caller's own origins (one
 * source file or one fixture): every gate call owns a disjoint slice of the
 * shared file.
 */
internal object VocabularyGate {
    fun assertClean(texts: List<CheckedText>, scopePrefix: String) {
        val baseline = loadBaseline(scopePrefix)
        val found = texts.flatMap { checked ->
            val text = checked.text ?: return@flatMap emptyList()
            ForbiddenVocabulary.findViolations(text).map { violation ->
                BaselineEntry(checked.origin, violation.term.display, violation.excerpt)
            }
        }
        val remaining = baseline.map { it.first }.toMutableList()
        val unbaselined = found.filter { entry ->
            val index = remaining.indexOf(entry)
            if (index >= 0) {
                remaining.removeAt(index)
                false
            } else {
                true
            }
        }
        assertThat(unbaselined.map { entry -> "${entry.origin} | ${entry.term} | ${entry.excerpt}" })
            .describedAs(
                "Vocabulary violations without baseline. F1 never edits user text: " +
                    "either the text is new (fix it in its fatia) or the baseline is " +
                    "missing a known F1 line (add origin | term | excerpt | fatia).",
            )
            .isEmpty()
        assertThat(remaining.map { entry -> "${entry.origin} | ${entry.term} | ${entry.excerpt}" })
            .describedAs("Baseline entries no longer violated. Remove them so the baseline only shrinks.")
            .isEmpty()
    }

    private fun loadBaseline(scopePrefix: String): List<Pair<BaselineEntry, String>> {
        val stream = VocabularyGate::class.java.getResourceAsStream("/content-vocabulary-baseline.txt")
        checkNotNull(stream) { "Missing content-vocabulary-baseline.txt on the test classpath" }
        val entries = mutableListOf<Pair<BaselineEntry, String>>()
        stream.bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                val parts = line.split("|").map { it.trim() }
                check(parts.size == 4) {
                    "Baseline line ${index + 1} must be `origin | term | excerpt | fatia`: $raw"
                }
                val (origin, term, excerpt, fatia) = parts
                check(origin.isNotBlank() && term.isNotBlank() && excerpt.isNotBlank()) {
                    "Baseline line ${index + 1} has a blank field: $raw"
                }
                check(term in ForbiddenVocabulary.DISPLAYS) {
                    "Baseline line ${index + 1} names an unknown term `$term`: $raw"
                }
                check(FATIA_RANGE.contains(fatia)) {
                    "Baseline line ${index + 1} must point at fatia F2–F9: $raw"
                }
                if (origin.startsWith(scopePrefix)) {
                    entries += BaselineEntry(origin, term, excerpt) to fatia
                }
            }
        }
        return entries
    }

    private val FATIA_RANGE = (2..9).map { "F$it" }.toSet()
}
