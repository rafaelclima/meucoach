package com.meucoach.app.story

import com.meucoach.app.ui.activitydetail.ActivityDetailTestData
import com.meucoach.core.domain.story.StoryPlanCalculator
import com.meucoach.core.domain.story.StorySegmentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class StoryMapScriptTest {
    @Test
    fun `three keyframes use opening follow and final pitches`() {
        assertThat(StoryMapScript.OPENING_PITCH_DEGREES).isEqualTo(30f)
        assertThat(StoryMapScript.FOLLOW_PITCH_DEGREES).isEqualTo(42f)
        assertThat(StoryMapScript.FINAL_PITCH_DEGREES).isEqualTo(35f)
    }

    @Test
    fun `bookends render smaller than the follow shot`() {
        assertThat(StoryMapScript.openingWidth()).isLessThan(StoryVideoExporter.mapWidth())
        assertThat(StoryMapScript.finalWidth()).isLessThan(StoryVideoExporter.mapWidth())
        assertThat(StoryMapScript.openingWidth()).isEqualTo(StoryMapScript.finalWidth())
    }

    @Test
    fun `keyframe follows the plan segments`() {
        val activity = ActivityDetailTestData.state().activity!!.imported
        val plan = checkNotNull(StoryPlanCalculator.calculate(activity, targetDurationSec = 30))
        val firstCardStart = plan.segments.first { it.type == StorySegmentType.CARD }.startSec
        val finalStart = plan.segments.last { it.type == StorySegmentType.FINAL }.startSec

        assertThat(StoryMapScript.keyframeAt(plan, 0f)).isEqualTo(StoryKeyframe.OPENING)
        assertThat(StoryMapScript.keyframeAt(plan, firstCardStart - 0.01f)).isEqualTo(StoryKeyframe.OPENING)
        assertThat(StoryMapScript.keyframeAt(plan, firstCardStart + 0.01f)).isEqualTo(StoryKeyframe.FOLLOW)
        assertThat(StoryMapScript.keyframeAt(plan, finalStart - 0.01f)).isEqualTo(StoryKeyframe.FOLLOW)
        assertThat(StoryMapScript.keyframeAt(plan, finalStart + 0.01f)).isEqualTo(StoryKeyframe.FINAL)
    }
}
