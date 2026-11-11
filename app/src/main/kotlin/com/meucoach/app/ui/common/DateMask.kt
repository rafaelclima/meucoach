package com.meucoach.app.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

private val DIGITS_DATE = DateTimeFormatter
    .ofPattern("ddMMuuuu")
    .withResolverStyle(ResolverStyle.STRICT)

/**
 * Date input as raw digits (`ddMMuuuu`) with slashes only on screen.
 *
 * Keeping separators out of the field value lets the caret follow the digits
 * normally: backspace deletes a digit and the slash appears/disappears without
 * fighting the cursor. Forms store the digits; [format] is only for display.
 */
internal object DateMask {
    const val MAX_DIGITS = 8

    fun digits(input: String): String = input.filter { it.isDigit() }.take(MAX_DIGITS)

    fun format(digits: String): String = buildString {
        digits.take(MAX_DIGITS).forEachIndexed { index, char ->
            if (index == 2 || index == 4) append('/')
            append(char)
        }
    }

    /** Parses raw digits (`14032027`) or a formatted value (`14/03/2027`). */
    fun parse(text: String): LocalDate? {
        val trimmed = text.trim()
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length != MAX_DIGITS) return null
        if (trimmed.length != MAX_DIGITS && trimmed.length != MAX_DIGITS + 2) return null
        return runCatching { LocalDate.parse(digits, DIGITS_DATE) }.getOrNull()
    }

    /** Formats a date for the digits-only field of a form. */
    fun toDigits(date: LocalDate): String = date.format(DIGITS_DATE)

    /** Caret position in `dd/MM/uuuu` for a caret in the raw digits. */
    fun originalToTransformed(offset: Int): Int = when {
        offset <= 2 -> offset
        offset <= 4 -> offset + 1
        else -> offset + 2
    }

    /** Caret position in the raw digits for a caret in `dd/MM/uuuu`. */
    fun transformedToOriginal(offset: Int): Int {
        val slashes = (if (offset > 2) 1 else 0) + (if (offset > 5) 1 else 0)
        return (offset - slashes).coerceAtLeast(0)
    }
}

internal val DateVisualTransformation = VisualTransformation { annotated ->
    val raw = annotated.text
    val digits = DateMask.digits(raw)
    val formatted = AnnotatedString(DateMask.format(digits))
    TransformedText(
        formatted,
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val digitsBefore = digitsBeforeOffset(raw, offset)
                return DateMask.originalToTransformed(digitsBefore).coerceAtMost(formatted.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val digitOffset = DateMask.transformedToOriginal(offset).coerceIn(0, digits.length)
                if (raw.length == digits.length) return digitOffset
                return raw.indexOfNthDigit(digitOffset)
            }
        },
    )
}

private fun digitsBeforeOffset(raw: String, offset: Int): Int =
    raw.take(offset.coerceIn(0, raw.length)).count { it.isDigit() }

/** Raw index of the digit at [digitIndex]; the end of the text when it does not exist. */
private fun String.indexOfNthDigit(digitIndex: Int): Int {
    var seen = 0
    forEachIndexed { index, char ->
        if (char.isDigit()) {
            if (seen == digitIndex) return index
            seen++
        }
    }
    return length
}
