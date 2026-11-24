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
    fun `every text swatch warns because none clears both checker extremes`() {
        // White disappears on the light checker, black on the dark one. The
        // transparent card can land on either, so the advisory is always on.
        assertThat(ArtColor.entries).allSatisfy { color ->
            assertThat(ArtContrast.textWarning(color))
                .isEqualTo("Texto com pouco contraste — confira sobre a foto.")
        }
    }

    @Test
    fun `black route warns even though it passes on the light checker`() {
        // The route sits on the casing shell, not on the checkerboard: black
        // over the shell composited on a black photo is ~1:1.
        assertThat(ArtContrast.routeWarning(ArtColor.BLACK))
            .isEqualTo("Traçado com pouco contraste — confira sobre a foto.")
    }

    @Test
    fun `white route does not warn falsely against the casing`() {
        assertThat(ArtContrast.routeWarning(ArtColor.WHITE)).isNull()
    }

    @Test
    fun `a black route with a white text lists both failures`() {
        assertThat(ArtContrast.warning(ArtColor.WHITE, ArtColor.BLACK))
            .isEqualTo("Texto e traçado com pouco contraste — confira sobre a foto.")
    }

    @Test
    fun `the casing extremes keep the documented contrast`() {
        assertThat(ArtContrast.ratio(ArtColor.BLACK.argb, ArtContrast.CASING_ON_BLACK)).isLessThan(3.0)
        assertThat(ArtContrast.ratio(ArtColor.WHITE.argb, ArtContrast.CASING_ON_WHITE)).isGreaterThan(3.0)
    }

    @Test
    fun `white text clears AA on the 58 percent scrim over a white photo`() {
        val backdrop = ArtContrast.photoBackdrop(0xFFFFFFFF.toInt())

        assertThat(ArtContrast.ratio(ArtColor.WHITE.argb, backdrop)).isGreaterThan(4.5)
    }

    @Test
    fun `black text still fails on the scrim over a dark photo`() {
        val backdrop = ArtContrast.photoBackdrop(0xFF000000.toInt())

        assertThat(ArtContrast.ratio(ArtColor.BLACK.argb, backdrop)).isLessThan(4.5)
    }
}
