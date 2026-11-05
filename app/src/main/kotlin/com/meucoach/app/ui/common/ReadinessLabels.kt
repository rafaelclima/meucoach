package com.meucoach.app.ui.common

import com.meucoach.core.model.ReadinessLevel

/**
 * Readiness is shown with two channels: the semantic color and a word.
 * The word carries the meaning on its own, so the level never depends on color alone.
 */
fun readinessLabel(level: ReadinessLevel): String = when (level) {
    ReadinessLevel.GREEN -> "Alta"
    ReadinessLevel.AMBER -> "Moderada"
    ReadinessLevel.RED -> "Baixa"
}

/** Lowercase form for sentences, e.g. "Sua prontidão está alta". */
fun readinessLabelLowercase(level: ReadinessLevel): String = readinessLabel(level).lowercase()
