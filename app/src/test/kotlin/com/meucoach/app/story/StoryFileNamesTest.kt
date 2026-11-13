package com.meucoach.app.story

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
