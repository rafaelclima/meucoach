package com.meucoach.app.story

import com.meucoach.core.domain.story.ArtFormat
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryPlanCalculator
import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class StoryFileNamesTest {
    @Test
    fun `names the video cover and poster with date and distance`() {
        val plan = plan()

        assertThat(StoryFileNames.video(plan, 123L))
            .startsWith("meucoach-story-2026-10-24-")
            .contains("km-123.mp4")
        assertThat(StoryFileNames.cover(plan, 123L))
            .startsWith("meucoach-story-2026-10-24-")
            .endsWith("-capa-9x16.png")
        assertThat(StoryFileNames.poster(plan, 123L))
            .startsWith("meucoach-story-2026-10-24-")
            .endsWith("-poster-4x5.png")
    }

    @Test
    fun `keeps a valid name when the plan has no date`() {
        val plan = plan().copy(localDate = null)

        assertThat(StoryFileNames.poster(plan, 9L))
            .startsWith("meucoach-story-sem-data-")
            .endsWith("-poster-4x5.png")
    }

    @Test
    fun `names the art card by format without clashing with the poster`() {
        val plan = plan()

        assertThat(StoryFileNames.art(plan, ArtFormat.PORTRAIT_4_5, 123L))
            .startsWith("meucoach-story-2026-10-24-")
            .endsWith("-arte-4x5.png")
        assertThat(StoryFileNames.art(plan, ArtFormat.SQUARE, 123L))
            .startsWith("meucoach-story-2026-10-24-")
            .endsWith("-arte-1x1.png")
        assertThat(StoryFileNames.art(plan, ArtFormat.PORTRAIT_9_16, 123L))
            .startsWith("meucoach-story-2026-10-24-")
            .endsWith("-arte-9x16.png")

        val poster = StoryFileNames.poster(plan, 123L)
        val art = StoryFileNames.art(plan, ArtFormat.PORTRAIT_4_5, 123L)
        assertThat(art).isNotEqualTo(poster)
    }

    @Test
    fun `names the photo art as jpg by format`() {
        val plan = plan()

        assertThat(StoryFileNames.artPhoto(plan, ArtFormat.PORTRAIT_4_5, 123L))
            .startsWith("meucoach-story-2026-10-24-")
            .endsWith("-arte-foto-4x5.jpg")
        assertThat(StoryFileNames.artPhoto(plan, ArtFormat.SQUARE, 123L))
            .endsWith("-arte-foto-1x1.jpg")
        assertThat(StoryFileNames.artPhoto(plan, ArtFormat.PORTRAIT_9_16, 123L))
            .endsWith("-arte-foto-9x16.jpg")
        assertThat(StoryFileNames.artPhoto(plan, ArtFormat.PORTRAIT_4_5, 123L))
            .isNotEqualTo(StoryFileNames.art(plan, ArtFormat.PORTRAIT_4_5, 123L))
    }

    private fun plan(): StoryPlan {
        val activity = ImportedActivity(
            source = ActivitySource.GPX,
            sport = Sport.RUNNING,
            startTime = Instant.parse("2026-10-24T12:00:00Z"),
            durationSec = 1_800,
            distanceM = 5_000.0,
            samples = listOf(
                ActivitySample(offsetSec = 0, latitude = -5.790, longitude = -35.210),
                ActivitySample(offsetSec = 900, latitude = -5.781, longitude = -35.205),
                ActivitySample(offsetSec = 1_800, latitude = -5.772, longitude = -35.200),
            ),
        )
        return checkNotNull(StoryPlanCalculator.calculate(activity))
            .copy(localDate = LocalDate.parse("2026-10-24"))
    }
}
