package com.meucoach.core.domain.coach

import java.text.Normalizer

/**
 * Lowercase, diacritic-stripped form for keyword matching.
 *
 * Display text stays accented; only matcher input goes through here so that
 * "só"/"so" and "faça"/"faca" match the same rule. Pure JVM, no Android.
 */
internal fun normalizedKeywordForm(value: String): String =
    Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
