package com.meucoach.app.story

import com.meucoach.core.domain.story.ArtRecipe
import com.meucoach.core.domain.story.StoryEssential
import com.meucoach.core.domain.story.StoryExtra
import com.meucoach.core.domain.story.StoryPlan
import com.meucoach.core.domain.story.StoryRenderSpec
import com.meucoach.core.domain.story.StoryRenderSpecResolver
import java.time.LocalDate

/**
 * Builds the measured text rows of the art card from a plan (ADR 0061 §2.4).
 * The renderer and the Studio clamp share this factory so the preview, the
 * export and the persisted nudge offsets cannot drift.
 */
internal object ArtLayoutTextFactory {
    fun fromPlan(plan: StoryPlan, localDate: LocalDate?, recipe: ArtRecipe): ArtLayoutText? {
        if (plan.route.points.size < 2) return null
        val spec = StoryRenderSpecResolver.fromPlan(plan, localDate, recipe.toStoryRecipe())
        val distance = spec.closing.firstOrNull { it.id == StoryEssential.DISTANCE.name } ?: return null
        val pace = spec.closing.firstOrNull { it.id == StoryEssential.AVG_PACE.name } ?: return null
        val time = spec.closing.firstOrNull { it.id == StoryEssential.TOTAL_TIME.name } ?: return null
        val values = recipe.extras.mapNotNull { extra -> extraValue(spec, extra, localDate) }
        return ArtLayoutText(
            distanceLabel = distance.label,
            distanceValue = distance.value,
            paceLabel = pace.label,
            paceValue = pace.value,
            timeLabel = time.label,
            timeValue = time.value,
            extras = values.takeIf { it.isNotEmpty() }?.joinToString(" · "),
        )
    }

    private fun extraValue(spec: StoryRenderSpec, extra: StoryExtra, localDate: LocalDate?): String? {
        val resolved = spec.closing.firstOrNull { it.id == extra.name && it.isAvailable } ?: return null
        return if (extra == StoryExtra.DATE) ArtDateFormatter.format(localDate) else resolved.value
    }
}
