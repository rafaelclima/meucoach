package com.meucoach.app.ui.activitydetail

import com.meucoach.core.domain.story.StorySegmentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class StoryPreviewFrameTest {
    private val plan = requireNotNull(
        com.meucoach.core.domain.story.StoryPlanCalculator.calculate(
            ActivityDetailTestData.activity().imported,
        ),
    )

    @Test
    fun `opening lands after the fade in`() {
        assertThat(StoryPreviewFrame.OPENING.timeSec(plan)).isEqualTo(0.9f)
    }

    @Test
    fun `km frame lands one second inside the first card`() {
        val card = plan.segments.first { it.type == StorySegmentType.CARD }

        assertThat(StoryPreviewFrame.KM.timeSec(plan)).isEqualTo(card.startSec + 1f)
    }

    @Test
    fun `final frame stays before the fade out`() {
        assertThat(StoryPreviewFrame.FINAL.timeSec(plan)).isEqualTo(plan.durationSec - 0.6f)
    }

    @Test
    fun `frames stay in narrative order`() {
        val times = StoryPreviewFrame.entries.map { it.timeSec(plan) }

        assertThat(times).isSorted()
        assertThat(times.last()).isLessThan(plan.durationSec)
    }
}
