package com.meucoach.core.domain.race

import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.Distance
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.RaceStatus
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RaceAutoCompletionRuleTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val raceDate = LocalDate.of(2026, 11, 8)
    private val at = Instant.parse("2026-10-01T12:00:00Z")

    @Test
    fun `same date and exact distance is eligible`() {
        val activity = activity("run-1", meters = 10_000.0)

        val decision = evaluate(activity = activity, activities = listOf(activity))

        assertThat(decision).isEqualTo(RaceAutoCompletionDecision.Eligible("race-1", "run-1"))
    }

    @Test
    fun `ninety percent is the inclusive lower bound`() {
        val activity = activity("run-1", meters = 9_000.0)

        assertThat(evaluate(activity = activity, activities = listOf(activity)))
            .isEqualTo(RaceAutoCompletionDecision.Eligible("race-1", "run-1"))
    }

    @Test
    fun `below ninety percent is too short`() {
        val activity = activity("run-1", meters = 8_999.0)

        assertThat(evaluate(activity = activity, activities = listOf(activity)))
            .isEqualTo(rejected(RaceAutoCompletionRejection.DISTANCE_TOO_SHORT))
    }

    @Test
    fun `one hundred and ten percent is the inclusive upper bound`() {
        val activity = activity("run-1", meters = 11_000.0)

        assertThat(evaluate(activity = activity, activities = listOf(activity)))
            .isEqualTo(RaceAutoCompletionDecision.Eligible("race-1", "run-1"))
    }

    @Test
    fun `above one hundred and ten percent is too long`() {
        val activity = activity("run-1", meters = 11_001.0)

        assertThat(evaluate(activity = activity, activities = listOf(activity)))
            .isEqualTo(rejected(RaceAutoCompletionRejection.DISTANCE_TOO_LONG))
    }

    @Test
    fun `treadmill is a valid race activity`() {
        val activity = activity("run-1", sport = Sport.TREADMILL)

        assertThat(evaluate(activity = activity, activities = listOf(activity)))
            .isEqualTo(RaceAutoCompletionDecision.Eligible("race-1", "run-1"))
    }

    @Test
    fun `walking is not a race activity`() {
        val activity = activity("run-1", sport = Sport.WALKING)

        assertThat(evaluate(activity = activity, activities = listOf(activity)))
            .isEqualTo(rejected(RaceAutoCompletionRejection.WRONG_SPORT))
    }

    @Test
    fun `another date is rejected`() {
        val activity = activity("run-1", date = raceDate.plusDays(1))

        assertThat(evaluate(activity = activity, activities = listOf(activity)))
            .isEqualTo(rejected(RaceAutoCompletionRejection.WRONG_DATE))
    }

    @Test
    fun `an activity linked to a session is rejected`() {
        val activity = activity("run-1")

        assertThat(
            evaluate(
                activity = activity,
                activities = listOf(activity),
                sessionLinked = setOf(activity.id),
            ),
        ).isEqualTo(rejected(RaceAutoCompletionRejection.ACTIVITY_ALREADY_LINKED_TO_SESSION))
    }

    @Test
    fun `an activity linked to another race is rejected`() {
        val activity = activity("run-1")

        assertThat(
            evaluate(
                activity = activity,
                activities = listOf(activity),
                raceLinked = setOf(activity.id),
            ),
        ).isEqualTo(rejected(RaceAutoCompletionRejection.ACTIVITY_ALREADY_LINKED_TO_RACE))
    }

    @Test
    fun `a previously declined activity is rejected`() {
        val activity = activity("run-1")

        assertThat(
            evaluate(
                activity = activity,
                activities = listOf(activity),
                declined = setOf(activity.id),
            ),
        ).isEqualTo(rejected(RaceAutoCompletionRejection.PREVIOUSLY_DECLINED))
    }

    @Test
    fun `terminal events are never completed again`() {
        for (status in listOf(RaceStatus.COMPLETED, RaceStatus.CANCELLED, RaceStatus.NO_SHOW)) {
            val activity = activity("run-1")
            val event = event(status = status)

            assertThat(evaluate(activity = activity, activities = listOf(activity), event = event))
                .isEqualTo(rejected(RaceAutoCompletionRejection.EVENT_NOT_PLANNED))
        }
    }

    @Test
    fun `two eligible candidates are not chosen automatically`() {
        val first = activity("run-1")
        val second = activity("run-2", meters = 10_100.0)

        assertThat(evaluate(activity = first, activities = listOf(first, second)))
            .isEqualTo(rejected(RaceAutoCompletionRejection.MULTIPLE_CANDIDATES))
    }

    @Test
    fun `a second activity outside the band does not block the only candidate`() {
        val race = activity("run-1")
        val warmUp = activity("warm-up", meters = 2_000.0)

        assertThat(evaluate(activity = race, activities = listOf(race, warmUp)))
            .isEqualTo(RaceAutoCompletionDecision.Eligible("race-1", "run-1"))
    }

    @Test
    fun `a second activity linked elsewhere does not block the only candidate`() {
        val race = activity("run-1")
        val linked = activity("linked", meters = 10_000.0)

        assertThat(
            evaluate(
                activity = race,
                activities = listOf(race, linked),
                sessionLinked = setOf(linked.id),
            ),
        ).isEqualTo(RaceAutoCompletionDecision.Eligible("race-1", "run-1"))
    }

    @Test
    fun `an activity missing from the day window is rejected`() {
        assertThat(evaluate(activity = activity("run-1"), activities = emptyList()))
            .isEqualTo(rejected(RaceAutoCompletionRejection.ACTIVITY_NOT_FOUND))
    }

    @Test
    fun `the profile timezone defines the local race date`() {
        // 02:30 UTC on the 9th is 23:30 on the 8th in São Paulo.
        val lateRun = activity(
            "run-1",
            start = Instant.parse("2026-11-09T02:30:00Z"),
        )

        assertThat(evaluate(activity = lateRun, activities = listOf(lateRun)))
            .isEqualTo(RaceAutoCompletionDecision.Eligible("race-1", "run-1"))
    }

    @Test
    fun `a run on the previous local day is rejected`() {
        // 02:00 UTC on the 8th is 23:00 on the 7th in São Paulo.
        val previousDay = activity(
            "run-1",
            start = Instant.parse("2026-11-08T02:00:00Z"),
        )

        assertThat(evaluate(activity = previousDay, activities = listOf(previousDay)))
            .isEqualTo(rejected(RaceAutoCompletionRejection.WRONG_DATE))
    }

    private fun evaluate(
        activity: ActivitySummary,
        activities: List<ActivitySummary>,
        event: RaceEvent = event(),
        sessionLinked: Set<String> = emptySet(),
        raceLinked: Set<String> = emptySet(),
        declined: Set<String> = emptySet(),
    ): RaceAutoCompletionDecision = RaceAutoCompletionRule.evaluate(
        RaceAutoCompletionInput(
            event = event,
            activityId = activity.id,
            activitiesOnDate = activities,
            zoneId = zone,
            sessionLinkedActivityIds = sessionLinked,
            raceLinkedActivityIds = raceLinked,
            declinedActivityIds = declined,
        ),
    )

    private fun rejected(reason: RaceAutoCompletionRejection) = RaceAutoCompletionDecision.Rejected(reason)

    private fun event(status: RaceStatus = RaceStatus.PLANNED) = RaceEvent(
        id = "race-1",
        distance = RaceDistance.TEN_K,
        targetDate = raceDate,
        status = status,
        createdAt = at,
        updatedAt = at,
        resolvedAt = if (status == RaceStatus.PLANNED) null else at,
    )

    private fun activity(
        id: String,
        date: LocalDate = raceDate,
        sport: Sport = Sport.RUNNING,
        meters: Double = 10_000.0,
        start: Instant? = null,
    ) = ActivitySummary(
        id = id,
        source = ActivitySource.FIT,
        sport = sport,
        startTime = start ?: date.atTime(7, 0).atZone(zone).toInstant(),
        durationSec = 3_000,
        distance = Distance(meters),
    )
}
