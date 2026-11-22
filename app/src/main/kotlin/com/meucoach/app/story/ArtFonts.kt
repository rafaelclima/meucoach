package com.meucoach.app.story

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import com.meucoach.core.designsystem.R
import com.meucoach.core.domain.story.ArtFont

/**
 * Compose family per art font, used by the picker preview. Oswald uses its own
 * static 500/700 files instead of synthetic bold, and the variable fonts map
 * the weight axis explicitly (ADR 0058 §2.5).
 */
val ArtFont.family: FontFamily
    get() = when (this) {
        ArtFont.INTER -> artVariableFamily(R.font.inter_variable)

        ArtFont.GEIST -> artVariableFamily(R.font.geist_variable)

        ArtFont.BEBAS_NEUE -> FontFamily(Font(R.font.bebas_neue))

        ArtFont.ANTON -> FontFamily(Font(R.font.anton))

        ArtFont.OSWALD -> FontFamily(
            Font(R.font.oswald_500, FontWeight.Medium),
            Font(R.font.oswald_700, FontWeight.Bold),
        )

        ArtFont.ARCHIVO_BLACK -> FontFamily(Font(R.font.archivo_black))
    }

/**
 * Canvas typeface for the art renderer. Display fonts carry a single weight;
 * Inter/Geist resolve their variable files, and weight handling stays with the
 * renderer via `Typeface.create`. Fails loudly instead of falling back to the
 * system font: a silent fallback would corrupt goldens without notice.
 */
fun ArtFont.typeface(context: Context): Typeface = when (this) {
    ArtFont.INTER -> artTypeface(context, R.font.inter_variable)
    ArtFont.GEIST -> artTypeface(context, R.font.geist_variable)
    ArtFont.BEBAS_NEUE -> artTypeface(context, R.font.bebas_neue)
    ArtFont.ANTON -> artTypeface(context, R.font.anton)
    ArtFont.OSWALD -> artTypeface(context, R.font.oswald_500)
    ArtFont.ARCHIVO_BLACK -> artTypeface(context, R.font.archivo_black)
}

/** Static 700 of Oswald, kept apart so the renderer never fakes it with bold. */
fun ArtFont.typefaceBold(context: Context): Typeface = when (this) {
    ArtFont.OSWALD -> artTypeface(context, R.font.oswald_700)
    else -> Typeface.create(typeface(context), Typeface.BOLD)
}

private fun artTypeface(context: Context, resId: Int): Typeface =
    checkNotNull(ResourcesCompat.getFont(context, resId)) { "Art font resource $resId did not load" }

private fun artVariableFamily(resId: Int): FontFamily = FontFamily(
    Font(
        resId = resId,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        resId = resId,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        resId = resId,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        resId = resId,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)
