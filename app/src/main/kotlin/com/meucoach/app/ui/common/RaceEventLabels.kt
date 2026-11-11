package com.meucoach.app.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RACE_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu")
private val PT_BR = Locale.forLanguageTag("pt-BR")

fun raceDistanceLabel(distance: RaceDistance): String = when (distance) {
    RaceDistance.FIVE_K -> "5 km"
    RaceDistance.TEN_K -> "10 km"
    RaceDistance.HALF_MARATHON -> "Meia maratona"
    RaceDistance.MARATHON -> "Maratona"
}

fun raceStatusLabel(status: RaceStatus): String = when (status) {
    RaceStatus.PLANNED -> "Planejada"
    RaceStatus.COMPLETED -> "Concluída"
    RaceStatus.CANCELLED -> "Cancelada"
    RaceStatus.NO_SHOW -> "Não compareceu"
}

fun raceDateLabel(date: LocalDate): String = date.format(RACE_DATE)

/** Declared goal only; never converted to pace, range or prediction. */
fun raceTargetTimeLabel(seconds: Int?): String? {
    if (seconds == null) return null
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val rest = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, rest)
    }
}

/** Parses `ddMMuuuu` digits from the form into a local date. */
fun parseRaceDate(raw: String): LocalDate? {
    val digits = raw.filter { it.isDigit() }
    if (digits.length != 8) return null
    return runCatching {
        LocalDate.of(
            digits.substring(4, 8).toInt(),
            digits.substring(2, 4).toInt(),
            digits.substring(0, 2).toInt(),
        )
    }.getOrNull()
}

fun raceDateForDisplay(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return buildString {
        digits.take(8).forEachIndexed { index, char ->
            if (index == 2 || index == 4) append('/')
            append(char)
        }
    }
}

fun formatRaceDateForInput(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("ddMMuuuu", PT_BR))

/**
 * The date field stores only digits; slashes live in the visual layer so the
 * caret and mid-string edits are not reordered by reformatting the raw value.
 */
val RaceDateVisualTransformation = VisualTransformation { annotated ->
    val digits = annotated.text.filter { it.isDigit() }.take(8)
    val formatted = AnnotatedString(raceDateForDisplay(digits))
    TransformedText(
        formatted,
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 4 -> offset + 1
                else -> offset + 2
            }.coerceAtMost(formatted.length)

            override fun transformedToOriginal(offset: Int): Int {
                val slashes = (if (offset > 2) 1 else 0) + (if (offset > 5) 1 else 0)
                return (offset - slashes).coerceIn(0, digits.length)
            }
        },
    )
}
