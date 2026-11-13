package com.meucoach.app.story

import com.meucoach.core.domain.story.StoryPlan
import java.util.Locale

/**
 * Display names shared by the video, the 9:16 cover and the 4:5 poster.
 *
 * The date and the distance make the files findable in the gallery without
 * opening them; the timestamp keeps two exports of the same run apart. The
 * prefix stays `meucoach-story-` so old and new files sort together.
 */
internal object StoryFileNames {
    fun video(plan: StoryPlan, stamp: Long): String = "${base(plan, stamp)}.mp4"

    fun cover(plan: StoryPlan, stamp: Long): String = "${base(plan, stamp)}-capa-9x16.png"

    fun poster(plan: StoryPlan, stamp: Long): String = "${base(plan, stamp)}-poster-4x5.png"

    private fun base(plan: StoryPlan, stamp: Long): String {
        val date = plan.localDate?.toString() ?: "sem-data"
        val distance = String.format(Locale.US, "%.2f", plan.distanceKm)
        return "meucoach-story-$date-${distance}km-$stamp"
    }
}
