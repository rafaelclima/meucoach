package com.meucoach.app.story

import android.content.Context
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.domain.story.ArtFont
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtFontsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the six art fonts resolve from designsystem resources`() {
        val typefaces = ArtFont.entries.map { it.typeface(context) }

        assertThat(typefaces).doesNotContainNull()
        assertThat(typefaces).doesNotContain(android.graphics.Typeface.DEFAULT)
    }

    @Test
    fun `every art font carries the glyphs the card needs`() {
        // Numbers, units and pt-BR accents of "5,00 km · 31:04 · 5:58 /km" + date "24 out 2026".
        val glyphs = "5,00km·3104:/çãáéô".toCharArray().toSet()

        val missing = ArtFont.entries
            .map { font -> font to glyphs.filterNot { font.textPaint(context).hasGlyph(it.toString()) } }
            .filter { (_, absent) -> absent.isNotEmpty() }

        assertThat(missing).isEmpty()
    }

    @Test
    fun `inter and geist keep tabular figures when tnum is set`() {
        for (font in listOf(ArtFont.INTER, ArtFont.GEIST)) {
            val paint = font.textPaint(context, tnum = true)

            val widths = ('0'..'9').map { paint.measureText(it.toString()) }

            assertThat(widths.distinct()).hasSize(1)
        }
    }

    @Test
    fun `display fonts accept tnum without crashing and keep the paint usable`() {
        for (font in ArtFont.entries - ArtFont.INTER - ArtFont.GEIST) {
            val paint = font.textPaint(context, tnum = true)

            assertThat(paint.fontFeatureSettings).isEqualTo("tnum")
            assertThat(paint.measureText("5,00 km")).isGreaterThan(0f)
        }
    }

    @Test
    fun `display fonts are visually distinct instead of silently falling back`() {
        val sample = "ROTA 5,00"
        val display = listOf(ArtFont.BEBAS_NEUE, ArtFont.ANTON, ArtFont.OSWALD, ArtFont.ARCHIVO_BLACK)
        val system = Paint().apply {
            isAntiAlias = true
            textSize = 48f
            typeface = android.graphics.Typeface.DEFAULT
        }

        val widths = display.associateWith { font -> font.textPaint(context).measureText(sample) }
        val systemWidth = system.measureText(sample)

        assertThat(widths.values.distinct()).hasSize(display.size)
        assertThat(widths.values).doesNotContain(systemWidth)
    }

    private fun ArtFont.textPaint(context: Context, tnum: Boolean = false): Paint = Paint().apply {
        isAntiAlias = true
        textSize = 48f
        typeface = typeface(context)
        if (tnum) fontFeatureSettings = "tnum"
    }
}
