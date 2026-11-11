package com.meucoach.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.SessionExecutionEntity
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ExecutionSource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceStatus
import com.meucoach.core.model.Sport
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RaceEventRepositoryTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var activities: ActivityRepository
    private lateinit var events: RaceEventRepository
    private val clock = Clock.fixed(Instant.parse("2026-11-10T13:00:00Z"), ZoneOffset.UTC)
    private val date = LocalDate.of(2026, 12, 6)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        activities = ActivityRepository(
            database = database,
            clock = clock,
            zoneId = ZoneId.of("America/Sao_Paulo"),
            idGenerator = { "activity-1" },
        )
        events = RaceEventRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `creates one planned event and rejects a second active event`(): Unit = runBlocking {
        val first = events.create(NewRaceEvent(RaceDistance.TEN_K, date, targetTimeSec = 3_600))

        assertThat(first).isInstanceOf(RaceEventOutcome.Saved::class.java)
        val saved = (first as RaceEventOutcome.Saved).event
        assertThat(saved.status).isEqualTo(RaceStatus.PLANNED)
        assertThat(saved.resolvedAt).isNull()
        assertThat(saved.targetTimeSec).isEqualTo(3_600)

        val second = events.create(NewRaceEvent(RaceDistance.FIVE_K, date.plusDays(1)))

        assertThat(second).isInstanceOf(RaceEventOutcome.ActiveEventExists::class.java)
        assertThat((second as RaceEventOutcome.ActiveEventExists).active.id).isEqualTo(saved.id)
    }

    @Test
    fun `updates only a planned event`(): Unit = runBlocking {
        val saved = (events.create(NewRaceEvent(RaceDistance.TEN_K, date)) as RaceEventOutcome.Saved).event

        val updated = events.update(
            saved.id,
            NewRaceEvent(RaceDistance.HALF_MARATHON, date.plusDays(7), targetTimeSec = 5_400),
        )

        val event = (updated as RaceEventOutcome.Saved).event
        assertThat(event.distance).isEqualTo(RaceDistance.HALF_MARATHON)
        assertThat(event.targetDate).isEqualTo(date.plusDays(7))
        assertThat(event.targetTimeSec).isEqualTo(5_400)
    }

    @Test
    fun `completes with an optional activity and preserves the event`(): Unit = runBlocking {
        activities.saveImportedActivity(activity())
        val saved = (events.create(NewRaceEvent(RaceDistance.TEN_K, date)) as RaceEventOutcome.Saved).event

        val completed = events.complete(saved.id, activityId = "activity-1")

        val event = (completed as RaceEventOutcome.Saved).event
        assertThat(event.status).isEqualTo(RaceStatus.COMPLETED)
        assertThat(event.activityId).isEqualTo("activity-1")
        assertThat(event.completionSource).isEqualTo(com.meucoach.core.model.RaceCompletionSource.USER)
        assertThat(event.resolvedAt).isNotNull
        assertThat(events.getPlanned()).isNull()
    }

    @Test
    fun `an inferred completion is auditable and reversible`(): Unit = runBlocking {
        activities.saveImportedActivity(activity())
        val saved = (events.create(NewRaceEvent(RaceDistance.TEN_K, date)) as RaceEventOutcome.Saved).event
        events.complete(
            saved.id,
            activityId = "activity-1",
            source = com.meucoach.core.model.RaceCompletionSource.AUTO_IMPORT,
        )

        val reopened = events.reopenAutoCompleted(saved.id)

        val event = (reopened as RaceEventOutcome.Saved).event
        assertThat(event.status).isEqualTo(RaceStatus.PLANNED)
        assertThat(event.activityId).isNull()
        assertThat(event.completionSource).isNull()
        assertThat(event.resolvedAt).isNull()
        assertThat(events.getPlanned()?.id).isEqualTo(saved.id)
        assertThat(activities.getActivitySummary("activity-1")).isNotNull
    }

    @Test
    fun `an explicit completion is not undone by the automatic undo`(): Unit = runBlocking {
        val saved = (events.create(NewRaceEvent(RaceDistance.TEN_K, date)) as RaceEventOutcome.Saved).event
        events.complete(saved.id)

        val outcome = events.reopenAutoCompleted(saved.id)

        assertThat(outcome).isInstanceOf(RaceEventOutcome.Rejected::class.java)
        assertThat(events.getById(saved.id)?.status).isEqualTo(RaceStatus.COMPLETED)
    }

    @Test
    fun `undo is rejected while another race is planned`(): Unit = runBlocking {
        val first = (events.create(NewRaceEvent(RaceDistance.TEN_K, date)) as RaceEventOutcome.Saved).event
        events.complete(
            first.id,
            source = com.meucoach.core.model.RaceCompletionSource.AUTO_IMPORT,
        )
        val second = (
            events.create(
                NewRaceEvent(RaceDistance.FIVE_K, date.plusDays(7)),
            ) as RaceEventOutcome.Saved
            ).event

        val outcome = events.reopenAutoCompleted(first.id)

        assertThat(outcome).isInstanceOf(RaceEventOutcome.ActiveEventExists::class.java)
        assertThat((outcome as RaceEventOutcome.ActiveEventExists).active.id).isEqualTo(second.id)
        assertThat(events.getById(first.id)?.status).isEqualTo(RaceStatus.COMPLETED)
    }

    @Test
    fun `undo of a planned event is rejected`(): Unit = runBlocking {
        val saved = (events.create(NewRaceEvent(RaceDistance.TEN_K, date)) as RaceEventOutcome.Saved).event

        assertThat(events.reopenAutoCompleted(saved.id))
            .isInstanceOf(RaceEventOutcome.Rejected::class.java)
    }

    @Test
    fun `rejects an activity already linked to another event`(): Unit = runBlocking {
        activities.saveImportedActivity(activity())
        val first = (events.create(NewRaceEvent(RaceDistance.TEN_K, date)) as RaceEventOutcome.Saved).event
        events.complete(first.id, activityId = "activity-1")
        val second = (
            events.create(
                NewRaceEvent(RaceDistance.FIVE_K, date.plusDays(7)),
            ) as RaceEventOutcome.Saved
            ).event

        val outcome = events.complete(second.id, activityId = "activity-1")

        assertThat(outcome).isInstanceOf(RaceEventOutcome.ActivityAlreadyLinked::class.java)
        assertThat(events.getPlanned()?.id).isEqualTo(second.id)
    }

    @Test
    fun `rejects an activity linked to a session execution`(): Unit = runBlocking {
        activities.saveImportedActivity(activity())
        database.sessionExecutionDao().insert(
            SessionExecutionEntity(
                id = "execution-1",
                plannedSessionId = null,
                activityId = "activity-1",
                startedAt = null,
                endedAt = null,
                pausesJson = "[]",
                durationSec = 1_800,
                distanceM = 5_000.0,
                rpe = null,
                source = ExecutionSource.ACTIVITY,
                createdAt = clock.millis(),
            ),
        )
        val saved = (events.create(NewRaceEvent(RaceDistance.TEN_K, date)) as RaceEventOutcome.Saved).event

        val outcome = events.complete(saved.id, activityId = "activity-1")

        assertThat(outcome).isInstanceOf(RaceEventOutcome.ActivityAlreadyLinked::class.java)
    }

    @Test
    fun `cancelling and no show free the active slot without starting recovery`(): Unit = runBlocking {
        val cancelled = (events.create(NewRaceEvent(RaceDistance.TEN_K, date)) as RaceEventOutcome.Saved).event
        assertThat((events.cancel(cancelled.id) as RaceEventOutcome.Saved).event.status)
            .isEqualTo(RaceStatus.CANCELLED)

        val noShow = (
            events.create(
                NewRaceEvent(RaceDistance.FIVE_K, date.plusDays(1)),
            ) as RaceEventOutcome.Saved
            ).event
        assertThat((events.noShow(noShow.id) as RaceEventOutcome.Saved).event.status)
            .isEqualTo(RaceStatus.NO_SHOW)

        assertThat(events.getPlanned()).isNull()
        assertThat(events.listAll()).hasSize(2)
        assertThat(events.listAll().map { it.activityId }).containsOnlyNulls()
    }

    @Test
    fun `resolving a finished event is rejected`(): Unit = runBlocking {
        val saved = (events.create(NewRaceEvent(RaceDistance.TEN_K, date)) as RaceEventOutcome.Saved).event
        events.cancel(saved.id)

        assertThat(events.complete(saved.id)).isInstanceOf(RaceEventOutcome.Rejected::class.java)
        assertThat(events.cancel(saved.id)).isInstanceOf(RaceEventOutcome.Rejected::class.java)
        assertThat(events.update(saved.id, NewRaceEvent(RaceDistance.FIVE_K, date)))
            .isInstanceOf(RaceEventOutcome.Rejected::class.java)
    }

    @Test
    fun `returns the event of the date and the dates in a window`(): Unit = runBlocking {
        events.create(NewRaceEvent(RaceDistance.TEN_K, date))
        events.cancel((events.getPlanned() as com.meucoach.core.model.RaceEvent).id)
        events.create(NewRaceEvent(RaceDistance.FIVE_K, date.plusDays(14)))

        assertThat(events.getForDate(date)?.distance).isEqualTo(RaceDistance.TEN_K)
        assertThat(events.getDatesBetween(date.minusDays(1), date.plusDays(20)))
            .containsExactlyInAnyOrder(date, date.plusDays(14))
    }

    @Test
    fun `trash suspends the link and purge clears it without deleting the event`(): Unit = runBlocking {
        activities.saveImportedActivity(activity())
        val saved = (events.create(NewRaceEvent(RaceDistance.TEN_K, date)) as RaceEventOutcome.Saved).event
        events.complete(saved.id, activityId = "activity-1")

        activities.deleteActivity("activity-1")
        assertThat(events.getForDate(date)?.activityId).isEqualTo("activity-1")

        activities.restoreActivity("activity-1")
        assertThat(events.getForDate(date)?.activityId).isEqualTo("activity-1")

        activities.deleteActivity("activity-1")
        activities.purgeActivity("activity-1")

        val event = events.getForDate(date)!!
        assertThat(event.status).isEqualTo(RaceStatus.COMPLETED)
        assertThat(event.activityId).isNull()
    }

    private fun activity() = ImportedActivity(
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-12-06T10:00:00Z"),
        durationSec = 3_600,
        distanceM = 10_000.0,
        averagePace = Pace.fromSecondsPerKm(360),
    )
}
