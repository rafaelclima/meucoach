package com.meucoach.app.ui.today

import com.meucoach.app.application.VolumeReferenceProjection
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.CompletedRace
import com.meucoach.core.domain.prescription.RacePlan
import com.meucoach.core.domain.prescription.RecoveryWindow
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WeeklyPlanInput
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.RaceStatus
import java.time.LocalDate
import java.time.ZoneId

/**
 * Inputs for the Sunday preview of the next civil week.
 *
 * The preview is an intent: it is generated with the data known on Sunday and
 * persisted before Monday. It never rewrites the current day and never counts
 * as history before its own week starts.
 */
internal data class TodayPreviewInput(
    val profile: AthleteProfile,
    val readiness: ReadinessResult,
    val load: LoadSnapshot?,
    val today: LocalDate,
    val weekStart: LocalDate,
    val currentPlan: WeeklyPlan,
    val recentWeeks: List<WeeklyPlan>,
    val weekNumberInBlock: Int,
    val events: List<RaceEvent>,
    val activities: List<ActivitySummary>,
    val zoneId: ZoneId,
    val dataIncomplete: Boolean,
)

internal class TodayPreviewGenerator(private val generator: WeeklyPlanGenerator = WeeklyPlanGenerator()) {
    fun generate(input: TodayPreviewInput): WeeklyPlan {
        val nextRaceEvent = input.events
            .filter { event ->
                event.status == RaceStatus.PLANNED &&
                    !event.targetDate.isBefore(input.weekStart)
            }
            .maxByOrNull { event -> event.updatedAt }
        val race = nextRaceEvent?.let(RacePlan::from)
        val raceTargetTimeSec = if (nextRaceEvent != null) {
            nextRaceEvent.targetTimeSec
        } else {
            input.profile.goal?.targetTimeSec
        }
        val volumeReferences = VolumeReferenceProjection.build(
            plans = input.recentWeeks + input.currentPlan,
            activities = input.activities,
            zoneId = input.zoneId,
        ).references
        val recovery = RecoveryWindow.covering(
            races = input.events.mapNotNull(CompletedRace::from),
            start = input.weekStart,
            end = input.weekStart.plusDays(6),
        )
        return generator.generate(
            WeeklyPlanInput(
                profile = input.profile,
                readiness = input.readiness,
                load = input.load,
                weekStart = input.weekStart,
                today = input.today,
                previousWeek = input.currentPlan,
                recentWeeks = input.recentWeeks,
                volumeReferences = volumeReferences,
                weekNumberInBlock = input.weekNumberInBlock,
                race = race,
                raceTargetTimeSec = raceTargetTimeSec,
                eventDates = input.events.map { event -> event.targetDate }.toSet(),
                recovery = recovery,
                dataIncomplete = input.dataIncomplete,
            ),
        )
    }
}
