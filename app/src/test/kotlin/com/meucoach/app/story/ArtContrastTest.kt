package com.meucoach.app.story

import com.meucoach.core.domain.story.ArtColor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Test

class ArtContrastTest {
    @Test
    fun `black on white is the maximum ratio`() {
        assertThat(ArtContrast.ratio(0xFF000000.toInt(), 0xFFFFFFFF.toInt())).isCloseTo(21.0, within(0.1))
        assertThat(ArtContrast.ratio(0xFFFFFFFF.toInt(), 0xFF000000.toInt())).isCloseTo(21.0, within(0.1))
    }

    @Test
    fun `identical colors have no contrast`() {
        assertThat(ArtContrast.ratio(0xFF5BD0E0.toInt(), 0xFF5BD0E0.toInt())).isCloseTo(1.0, within(0.01))
    }

    @Test
    fun `default recipe passes on the dark checker`() {
        assertThat(ArtContrast.warning(ArtColor.WHITE, ArtColor.LIME, darkPreview = true)).isNull()
    }

    @Test
    fun `warns when the text fails the dark checker`() {
        val warning = ArtContrast.warning(ArtColor.GRAY_DARK, ArtColor.LIME, darkPreview = true)

        assertThat(warning).isEqualTo("Texto com pouco contraste no fundo escuro — confira sobre a foto.")
    }

    @Test
    fun `white text warns on the light checker by design`() {
        // White over a light photo is exactly the case the warning exists for.
        val warning = ArtContrast.warning(ArtColor.WHITE, ArtColor.LIME, darkPreview = false)

        assertThat(warning).isEqualTo("Texto com pouco contraste no fundo claro — confira sobre a foto.")
    }

    @Test
    fun `warns when the route fails the light checker`() {
        val warning = ArtContrast.warning(ArtColor.BLACK, ArtColor.LIME, darkPreview = false)

        assertThat(warning).isEqualTo("Traçado com pouco contraste no fundo claro — confira sobre a foto.")
    }

    @Test
    fun `text failure takes precedence over the route`() {
        val warning = ArtContrast.warning(ArtColor.GRAY_DARK, ArtColor.GRAY_DARK, darkPreview = true)

        assertThat(warning).startsWith("Texto")
    }
}
