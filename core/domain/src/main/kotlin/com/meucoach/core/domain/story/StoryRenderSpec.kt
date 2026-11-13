package com.meucoach.core.domain.story

import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.model.ImportedActivity
import java.time.LocalDate

/**
 * One metric already formatted for the frame. The renderer never decides
 * availability, never formats and never substitutes a missing value with zero.
 * Unavailable essentials keep an em dash and a reason; unavailable extras are
 * omitted from the video (the Studio still explains why they are disabled).
 */
data class ResolvedMetric(
    val id: String,
    val label: String,
    val value: String,
    val isAvailable: Boolean = true,
    val reason: String? = null,
) {
    init {
        require(id.isNotBlank()) { "Metric id must not be blank" }
        require(label.isNotBlank()) { "Metric label must not be blank" }
        require(value.isNotBlank()) { "Metric value must not be blank" }
        require(isAvailable || reason != null) { "An unavailable metric needs a reason" }
    }
}

/** Km card: pace first, then up to two per-km extras that have data. */
data class StoryCardSpec(
    val split: StorySplit,
    val metrics: List<ResolvedMetric>,
    val showFastestBadge: Boolean = false,
) {
    init {
        require(metrics.isNotEmpty()) { "A story card needs at least the pace metric" }
        require(metrics.size <= 3) { "A story card shows at most three metrics" }
    }
}

/**
 * Everything one story frame needs, already resolved. Preview, worker and
 * poster consume the same spec, so they cannot disagree (ADR 0040 §2.1).
 */
data class StoryRenderSpec(
    val plan: StoryPlan,
    /** Distance and total time, always two slots. */
    val header: List<ResolvedMetric>,
    val cards: List<StoryCardSpec>,
    /** Three essentials + up to two extras. */
    val closing: List<ResolvedMetric>,
    val highlights: StoryHighlights,
    val recipe: StoryRecipe,
    val mapStyle: StoryMapStyle,
    val quality: StoryQuality,
) {
    init {
        require(header.size == 2) { "The story header always has distance and time" }
        require(closing.size in 3..5) { "The closing frame has 3 essentials and up to 2 extras" }
    }
}

/** Derives summary facts from the plan, never from UI state. */
object StoryMetricValuesResolver {
    fun from(plan: StoryPlan, localDate: LocalDate?): StoryMetricValues {
        val fastest = plan.allSplits.minByOrNull { it.pace.secondsPerKm }
        return StoryMetricValues(
            distanceKm = plan.distanceKm,
            totalTimeSec = plan.durationTotalSec,
            averagePace = plan.averagePace,
            calories = plan.calories,
            elevationGainM = plan.elevationGainM,
            averageHeartRate = plan.averageHeartRate,
            maxHeartRate = plan.maxHeartRate,
            averageCadenceSpm = plan.averageCadenceSpm,
            completedSplits = plan.allSplits.size,
            fastestKm = fastest?.km,
            fastestPace = fastest?.pace,
            localDate = localDate,
        )
    }
}

/**
 * Resolves activity + local date + recipe into a [StoryRenderSpec]. The plan
 * itself is rebuilt here, so the same call works from the preview and from the
 * background worker.
 */
object StoryRenderSpecResolver {
    fun resolve(
        activity: ImportedActivity,
        localDate: LocalDate?,
        recipe: StoryRecipe,
        lthrBpm: Int? = null,
        session: PlannedSession? = null,
        nextSession: PlannedSession? = null,
    ): StoryRenderSpec? {
        val targetDurationSec = recipe.targetDurationSec(activity.distanceM / 1_000.0)
        val plan = StoryPlanCalculator.calculate(
            activity = activity,
            targetDurationSec = targetDurationSec,
            lthrBpm = lthrBpm,
            session = session,
            nextSession = nextSession,
        ) ?: return null
        return fromPlan(plan, localDate, recipe)
    }

    fun fromPlan(plan: StoryPlan, localDate: LocalDate?, recipe: StoryRecipe): StoryRenderSpec {
        val values = StoryMetricValuesResolver.from(plan, localDate)
        val highlights = StoryHighlightsCalculator.calculate(plan)
        val selectedExtras = recipe.resolvedExtras(values)
        val header = listOf(
            resolveEssential(StoryEssential.DISTANCE, values),
            resolveEssential(StoryEssential.TOTAL_TIME, values),
        )
        val cards = plan.splits.map { split ->
            val metrics = mutableListOf(paceMetric(split))
            selectedExtras
                .filter { StoryMetricCatalog.appearsPerKm(it) }
                .forEach { extra ->
                    StoryMetricCatalog.formatPerKm(extra, split)?.let { value ->
                        metrics += ResolvedMetric(
                            id = extra.name,
                            label = StoryMetricCatalog.perKmLabel(extra),
                            value = value,
                        )
                    }
                }
            StoryCardSpec(
                split = split,
                metrics = metrics.take(3),
                showFastestBadge = StoryExtra.FASTEST_KM in selectedExtras &&
                    highlights.fastestKm == split.km,
            )
        }
        val closing = buildList {
            StoryRecipe.ESSENTIALS.forEach { add(resolveEssential(it, values)) }
            selectedExtras.forEach { extra ->
                resolveExtra(extra, values)?.let(::add)
            }
        }
        return StoryRenderSpec(
            plan = plan,
            header = header,
            cards = cards,
            closing = closing,
            highlights = highlights,
            recipe = recipe,
            mapStyle = recipe.mapStyle,
            quality = recipe.quality,
        )
    }

    private fun paceMetric(split: StorySplit): ResolvedMetric = ResolvedMetric(
        id = StoryEssential.AVG_PACE.name,
        label = "RITMO",
        value = String.format(
            java.util.Locale.forLanguageTag("pt-BR"),
            "%d:%02d /km",
            split.pace.secondsPerKm / 60,
            split.pace.secondsPerKm % 60,
        ),
    )

    private fun resolveEssential(metric: StoryEssential, values: StoryMetricValues): ResolvedMetric {
        val availability = StoryMetricCatalog.availability(metric, values)
        return ResolvedMetric(
            id = metric.name,
            label = StoryMetricCatalog.label(metric),
            value = StoryMetricCatalog.format(metric, values) ?: "—",
            isAvailable = availability.isAvailable,
            reason = availability.reason?.label,
        )
    }

    private fun resolveExtra(metric: StoryExtra, values: StoryMetricValues): ResolvedMetric? {
        val availability = StoryMetricCatalog.availability(metric, values)
        if (!availability.isAvailable) return null
        val formatted = StoryMetricCatalog.format(metric, values) ?: return null
        return ResolvedMetric(
            id = metric.name,
            label = StoryMetricCatalog.label(metric),
            value = formatted,
        )
    }
}
