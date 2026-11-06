package com.meucoach.core.domain.prescription

import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.Distance
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SessionMatcherTest {
    private val matcher = SessionMatcher()
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val date = LocalDate.of(2026, 11, 2)
    private val session = session(durationMin = 40, distanceKm = 6.0)

    @Test
    fun `suggests a run on the same local date with compatible duration and distance`() {
        val activity = activity(durationMin = 42, distanceKm = 6.1)

        assertThat(matcher.matches(session, activity, zone)).isTrue()
        assertThat(matcher.suggest(session, listOf(activity), zone)).containsExactly(activity)
    }

    @Test
    fun `duration tolerance is twenty percent or ten minutes, whichever is larger`() {
        assertThat(matcher.matches(session, activity(durationMin = 50, distanceKm = 6.0), zone)).isTrue()
        assertThat(matcher.matches(session, activity(durationMin = 51, distanceKm = 6.0), zone)).isFalse()
    }

    @Test
    fun `distance tolerance is fifteen percent or one kilometre, whichever is larger`() {
        assertThat(matcher.matches(session, activity(durationMin = 40, distanceKm = 7.0), zone)).isTrue()
        assertThat(matcher.matches(session, activity(durationMin = 40, distanceKm = 7.1), zone)).isFalse()
    }

    @Test
    fun `distance is ignored when the session has no distance target`() {
        val noDistance = session(durationMin = 40, distanceKm = null)

        assertThat(matcher.matches(noDistance, activity(durationMin = 40, distanceKm = 20.0), zone)).isTrue()
    }

    @Test
    fun `rejects another day, another sport and walking`() {
        val otherDay = activity(durationMin = 40, distanceKm = 6.0, start = Instant.parse("2026-11-03T10:00:00Z"))
        val treadmill = activity(durationMin = 40, distanceKm = 6.0, sport = Sport.TREADMILL)
        val walking = activity(durationMin = 40, distanceKm = 6.0, sport = Sport.WALKING)

        assertThat(matcher.matches(session, otherDay, zone)).isFalse()
        assertThat(matcher.matches(session, treadmill, zone)).isTrue()
        assertThat(matcher.matches(session, walking, zone)).isFalse()
    }

    @Test
    fun `excludes activities already linked to a session`() {
        val activity = activity(durationMin = 40, distanceKm = 6.0)

        assertThat(matcher.suggest(session, listOf(activity), zone, excludedActivityIds = setOf(activity.id)))
            .isEmpty()
    }

    @Test
    fun `returns every compatible activity ordered by duration closeness`() {
        val far = activity(durationMin = 48, distanceKm = 6.0, id = "far")
        val close = activity(durationMin = 41, distanceKm = 6.0, id = "close")

        assertThat(matcher.suggest(session, listOf(far, close), zone).map { it.id })
            .containsExactly("close", "far")
    }

    private fun session(durationMin: Int, distanceKm: Double?) = PlannedSession(
        date = date,
        type = WorkoutType.EASY,
        title = "Fácil",
        durationMin = durationMin,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        targets = PrescriptionTargets(distanceKm = distanceKm),
    )

    private fun activity(
        durationMin: Int,
        distanceKm: Double,
        id: String = "activity-1",
        sport: Sport = Sport.RUNNING,
        start: Instant = Instant.parse("2026-11-02T10:00:00Z"),
    ) = ActivitySummary(
        id = id,
        source = ActivitySource.FIT,
        sport = sport,
        startTime = start,
        durationSec = durationMin * 60,
        distance = Distance(distanceKm * 1_000.0),
    )
}
