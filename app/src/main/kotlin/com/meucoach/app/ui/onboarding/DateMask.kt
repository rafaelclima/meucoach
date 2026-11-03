package com.meucoach.app.ui.onboarding

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Date input as raw digits (`ddMMuuuu`) with slashes only on screen.
 *
 * Keeping separators out of the field value lets the caret follow the digits
 * normally: backspace deletes a digit and the slash appears/disappears without
 * fighting the cursor. `OnboardingForm` stores the digits; [DateMask.format] is
 * only for display.
 */
internal object DateMask {
    fun digits(input: String): String = input.filter { it.isDigit() }.take(MAX_DIGITS)

    fun format(digits: String): String = buildString {
        digits.take(MAX_DIGITS).forEachIndexed { index, char ->
            if (index == 2 || index == 4) append('/')
            append(char)
        }
    }

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

    const val MAX_DIGITS = 8
}

internal val DateVisualTransformation = VisualTransformation { annotated ->
    val digits = annotated.text
    val formatted = AnnotatedString(DateMask.format(digits))
    TransformedText(
        formatted,
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                DateMask.originalToTransformed(offset).coerceAtMost(formatted.length)

            override fun transformedToOriginal(offset: Int): Int =
                DateMask.transformedToOriginal(offset).coerceIn(0, digits.length)
        },
    )
}
