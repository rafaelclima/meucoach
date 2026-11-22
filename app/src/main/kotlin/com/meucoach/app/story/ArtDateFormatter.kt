package com.meucoach.app.story

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Date copy of the art card: "24 out 2026" (`d MMM uuuu`, pt-BR). Kept apart
 * from [com.meucoach.core.domain.story.StoryMetricCatalog] on purpose — the
 * video keeps the short "24 out", the card shows the full date (ADR 0058 §2.3).
 */
object ArtDateFormatter {
    private val PT_BR = Locale.forLanguageTag("pt-BR")
    private val FORMAT = DateTimeFormatter.ofPattern("d MMM uuuu", PT_BR)

    /** pt-BR abbreviates the month as "out."; the dot is noise at this size. */
    fun format(localDate: LocalDate?): String? = localDate?.format(FORMAT)?.replace(".", "")
}
