package com.meucoach.core.domain.story

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class ArtRecipeTest {
    @Test
    fun `default art is portrait 4x5 Inter white text lime route and no extras`() {
        val recipe = ArtRecipe()

        assertThat(recipe.format).isEqualTo(ArtFormat.PORTRAIT_4_5)
        assertThat(recipe.extras).isEmpty()
        assertThat(recipe.font).isEqualTo(ArtFont.INTER)
        assertThat(recipe.textColor).isEqualTo(ArtColor.WHITE)
        assertThat(recipe.routeColor).isEqualTo(ArtColor.LIME)
    }

    @Test
    fun `portrait is 1080x1350 and square is 1080x1080`() {
        assertThat(ArtFormat.PORTRAIT_4_5.width).isEqualTo(1080)
        assertThat(ArtFormat.PORTRAIT_4_5.height).isEqualTo(1350)
        assertThat(ArtFormat.SQUARE.width).isEqualTo(1080)
        assertThat(ArtFormat.SQUARE.height).isEqualTo(1080)
    }

    @Test
    fun `formats expose the file tag used by artifact names`() {
        assertThat(ArtFormat.PORTRAIT_4_5.fileTag).isEqualTo("4x5")
        assertThat(ArtFormat.SQUARE.fileTag).isEqualTo("1x1")
    }

    @Test
    fun `formats are limited to the two documented cards`() {
        assertThat(ArtFormat.entries).containsExactly(ArtFormat.PORTRAIT_4_5, ArtFormat.SQUARE)
    }

    @Test
    fun `fonts are the six documented families`() {
        assertThat(ArtFont.entries).containsExactly(
            ArtFont.INTER,
            ArtFont.GEIST,
            ArtFont.BEBAS_NEUE,
            ArtFont.ANTON,
            ArtFont.OSWALD,
            ArtFont.ARCHIVO_BLACK,
        )
    }

    @Test
    fun `colors are the eight documented swatches with the token values`() {
        assertThat(ArtColor.entries).containsExactly(
            ArtColor.LIME,
            ArtColor.CORAL,
            ArtColor.WHITE,
            ArtColor.BLACK,
            ArtColor.GRAY_LIGHT,
            ArtColor.GRAY_DARK,
            ArtColor.CYAN,
            ArtColor.BLUE,
        )
        assertThat(ArtColor.LIME.argb).isEqualTo(0xFFD8FF3E.toInt())
        assertThat(ArtColor.CORAL.argb).isEqualTo(0xFFFF6B5E.toInt())
        assertThat(ArtColor.WHITE.argb).isEqualTo(0xFFF4F6F7.toInt())
        assertThat(ArtColor.BLACK.argb).isEqualTo(0xFF0A0B0D.toInt())
        assertThat(ArtColor.GRAY_LIGHT.argb).isEqualTo(0xFFA2A9B0.toInt())
        assertThat(ArtColor.GRAY_DARK.argb).isEqualTo(0xFF5C646D.toInt())
        assertThat(ArtColor.CYAN.argb).isEqualTo(0xFF5BD0E0.toInt())
        assertThat(ArtColor.BLUE.argb).isEqualTo(0xFF6C9CFF.toInt())
    }

    @Test
    fun `extra choices are date heart rate elevation calories and cadence`() {
        assertThat(ArtRecipe.EXTRA_CHOICES).containsExactly(
            StoryExtra.DATE,
            StoryExtra.AVG_HR,
            StoryExtra.ELEVATION_GAIN,
            StoryExtra.CALORIES_TOTAL,
            StoryExtra.CADENCE,
        )
    }

    @Test
    fun `accepts zero one or two distinct extras`() {
        assertThat(ArtRecipe(extras = emptyList()).extras).isEmpty()
        assertThat(ArtRecipe(extras = listOf(StoryExtra.DATE)).extras).hasSize(1)
        assertThat(
            ArtRecipe(extras = listOf(StoryExtra.AVG_HR, StoryExtra.CADENCE)).extras,
        ).hasSize(2)
    }

    @Test
    fun `rejects a third extra and duplicates`() {
        assertThatThrownBy {
            ArtRecipe(
                extras = listOf(
                    StoryExtra.AVG_HR,
                    StoryExtra.CADENCE,
                    StoryExtra.DATE,
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            ArtRecipe(extras = listOf(StoryExtra.AVG_HR, StoryExtra.AVG_HR))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `toggling adds extras in selection order and keeps the rest of the recipe`() {
        val base = ArtRecipe(format = ArtFormat.SQUARE, font = ArtFont.ANTON)
        val first = base.toggleExtra(StoryExtra.DATE)
        val second = first.toggleExtra(StoryExtra.CALORIES_TOTAL)

        assertThat(first.extras).containsExactly(StoryExtra.DATE)
        assertThat(second.extras).containsExactly(StoryExtra.DATE, StoryExtra.CALORIES_TOTAL)
        assertThat(second.format).isEqualTo(ArtFormat.SQUARE)
        assertThat(second.font).isEqualTo(ArtFont.ANTON)
        assertThat(second.textColor).isEqualTo(ArtColor.WHITE)
        assertThat(second.routeColor).isEqualTo(ArtColor.LIME)
    }

    @Test
    fun `toggling a selected extra removes it`() {
        val recipe = ArtRecipe(extras = listOf(StoryExtra.AVG_HR, StoryExtra.CADENCE))

        val withoutHr = recipe.toggleExtra(StoryExtra.AVG_HR)

        assertThat(withoutHr.extras).containsExactly(StoryExtra.CADENCE)
    }

    @Test
    fun `toggling a third extra is ignored instead of throwing`() {
        val full = ArtRecipe(extras = listOf(StoryExtra.AVG_HR, StoryExtra.CADENCE))

        val unchanged = full.toggleExtra(StoryExtra.DATE)

        assertThat(unchanged).isEqualTo(full)
    }

    @Test
    fun `withExtras replaces the list and still validates`() {
        val replaced = ArtRecipe(extras = listOf(StoryExtra.DATE))
            .withExtras(listOf(StoryExtra.CADENCE, StoryExtra.ELEVATION_GAIN))

        assertThat(replaced.extras).containsExactly(StoryExtra.CADENCE, StoryExtra.ELEVATION_GAIN)

        assertThatThrownBy {
            ArtRecipe().withExtras(listOf(StoryExtra.AVG_HR, StoryExtra.CADENCE, StoryExtra.DATE))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `toStoryRecipe maps the extras and keeps the story defaults`() {
        val recipe = ArtRecipe(
            format = ArtFormat.SQUARE,
            extras = listOf(StoryExtra.DATE, StoryExtra.AVG_HR),
            font = ArtFont.OSWALD,
            textColor = ArtColor.BLACK,
            routeColor = ArtColor.CYAN,
        )

        val story = recipe.toStoryRecipe()

        assertThat(story.extras).containsExactly(StoryExtra.DATE, StoryExtra.AVG_HR)
        assertThat(story.preset).isEqualTo(StoryPreset.CUSTOM)
        assertThat(story.duration).isEqualTo(StoryDuration.AUTO)
        assertThat(story.mapStyle).isEqualTo(StoryMapStyle.MEUCOACH_DARK)
        assertThat(story.quality).isEqualTo(StoryQuality.STANDARD_1080P30)
    }
}
