package com.meucoach.core.domain.story

import java.util.Locale

private val PT_BR = Locale.forLanguageTag("pt-BR")

/**
 * Clock formatting shared by the story header and the closing frame. Hours are
 * only shown when they exist, so a 28:14 run keeps the familiar mm:ss shape.
 */
fun formatStoryDuration(seconds: Int): String {
    require(seconds >= 0) { "Duration must not be negative" }
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remaining = seconds % 60
    return if (hours > 0) {
        String.format(PT_BR, "%d:%02d:%02d", hours, minutes, remaining)
    } else {
        String.format(PT_BR, "%d:%02d", minutes, remaining)
    }
}
