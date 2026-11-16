package com.meucoach.app.application

import com.meucoach.core.data.db.entity.plannedSessionId
import com.meucoach.core.domain.prescription.StrongRun
import com.meucoach.core.domain.prescription.VolumeReference
import com.meucoach.core.domain.prescription.VolumeReferenceBuilder
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.Sport
import java.time.ZoneId

/** Real execution projection: weekly references plus the strong runs behind them. */
data class VolumeProjection(val references: List<VolumeReference>, val strongRuns: List<StrongRun>)

/**
 * Builds the volume projection at the edge (ADR 0050 §2.1), where the persisted
 * plans and the imported activities meet. The domain stays pure: this object
 * only maps activities to strong runs and delegates the weekly aggregation to
 * [VolumeReferenceBuilder].
 *
 * The input activities are expected to exclude the trash ([ActivitySource] has
 * no deleted flag; the repository already filters it). Only running/treadmill
 * imports are strong volume; a manual entry never is. A run shorter than one
 * minute rounds down to zero and is not counted, so volume is never inflated.
 */
object VolumeReferenceProjection {
    fun build(plans: List<WeeklyPlan>, activities: List<ActivitySummary>, zoneId: ZoneId): VolumeProjection {
        val orderedPlans = plans.distinctBy { it.weekStart }.sortedBy { it.weekStart }
        val linkedSessions = orderedPlans
            .flatMap { plan ->
                plan.sessions.mapNotNull { session ->
                    session.activityId?.let { activityId ->
                        activityId to plannedSessionId(plan.weekStart, session.date)
                    }
                }
            }
            .toMap()
        val strongRuns = activities
            .asSequence()
            .filter { it.sport == Sport.RUNNING || it.sport == Sport.TREADMILL }
            .filter { it.source != ActivitySource.MANUAL }
            .mapNotNull { activity ->
                val durationMin = activity.durationSec / SECONDS_PER_MINUTE
                if (durationMin <= 0) return@mapNotNull null
                StrongRun(
                    date = activity.startTime.atZone(zoneId).toLocalDate(),
                    durationMin = durationMin,
                    source = activity.source,
                    plannedSessionId = linkedSessions[activity.id],
                )
            }
            .sortedWith(compareBy({ it.date }, { it.durationMin }, { it.source.name }, { it.plannedSessionId }))
            .toList()
        val references = orderedPlans.map { plan ->
            VolumeReferenceBuilder.build(plan.weekStart, plan, strongRuns)
        }
        return VolumeProjection(references = references, strongRuns = strongRuns)
    }

    private const val SECONDS_PER_MINUTE = 60
}
