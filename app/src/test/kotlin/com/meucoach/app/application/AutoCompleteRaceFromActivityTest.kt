package com.meucoach.app.application

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.importer.RaceAutoCompletionStore
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.db.entity.SessionExecutionEntity
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.NewRaceEvent
import com.meucoach.core.data.repository.RaceEventOutcome
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.SaveActivityResult
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.domain.race.RaceAutoCompletionRejection
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ExecutionSource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.RaceCompletionSource
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
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
class AutoCompleteRaceFromActivityTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val raceDate = LocalDate.of(2026, 11, 8)
    private val timeProvider = FakeTimeProvider(
        instant = raceDate.atTime(12, 0).atZone(zone).toInstant(),
        zone = zone,
    )
    private val clock = Clock.fixed(Instant.parse("2026-11-08T15:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: MeuCoachDatabase
    private lateinit var activities: ActivityRepository
    private lateinit var executions: SessionExecutionRepository
    private lateinit var raceEvents: RaceEventRepository
    private lateinit var declines: RaceAutoCompletionStore
    private lateinit var autoComplete: AutoCompleteRaceFromActivity
    private lateinit var undo: UndoRaceAutoCompletion
    private var activitySequence = 0
    private var runSequence = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room
            .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        activities = ActivityRepository(
            database = database,
            clock = clock,
            zoneId = zone,
            idGenerator = { "activity-${++activitySequence}" },
        )
        executions = SessionExecutionRepository(database, clock)
        raceEvents = RaceEventRepository(database, clock)
        declines = RaceAutoCompletionStore(context, "race-auto-${System.nanoTime()}")
        autoComplete = AutoCompleteRaceFromActivity(raceEvents, activities, executions, declines, timeProvider)
        undo = UndoRaceAutoCompletion(raceEvents, declines)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `completes the race with a matching run without creating an execution`(): Unit = runBlocking {
        val event = createRace()
        val activityId = saveRun(raceDate, meters = 10_000.0)

        val result = autoComplete.evaluate(activityId)

        assertThat(result).isEqualTo(RaceAutoCompletionResult.Completed(event.id, activityId))
        val completed = raceEvents.getById(event.id)!!
        assertThat(completed.status).isEqualTo(RaceStatus.COMPLETED)
        assertThat(completed.completionSource).isEqualTo(RaceCompletionSource.AUTO_IMPORT)
        assertThat(completed.activityId).isEqualTo(activityId)
        assertThat(executions.listAll()).isEmpty()
    }

    @Test
    fun `does not complete a run below ninety percent`(): Unit = runBlocking {
        createRace()
        val activityId = saveRun(raceDate, meters = 8_900.0)

        assertThat(autoComplete.evaluate(activityId))
            .isEqualTo(RaceAutoCompletionResult.Skipped(RaceAutoCompletionRejection.DISTANCE_TOO_SHORT))
        assertThat(raceEvents.getPlanned()).isNotNull
    }

    @Test
    fun `does not complete a run in the trash`(): Unit = runBlocking {
        createRace()
        val activityId = saveRun(raceDate, meters = 10_000.0)
        activities.deleteActivity(activityId)

        assertThat(autoComplete.evaluate(activityId))
            .isEqualTo(RaceAutoCompletionResult.Skipped(RaceAutoCompletionRejection.ACTIVITY_NOT_FOUND))
        assertThat(raceEvents.getPlanned()).isNotNull
    }

    @Test
    fun `does not complete a run from another date`(): Unit = runBlocking {
        createRace()
        val activityId = saveRun(raceDate.plusDays(1), meters = 10_000.0)

        assertThat(autoComplete.evaluate(activityId))
            .isEqualTo(RaceAutoCompletionResult.NoPlannedEvent(raceDate.plusDays(1)))
        assertThat(raceEvents.getPlanned()).isNotNull
    }

    @Test
    fun `does not complete when there is no planned race`(): Unit = runBlocking {
        val activityId = saveRun(raceDate, meters = 10_000.0)

        assertThat(autoComplete.evaluate(activityId))
            .isEqualTo(RaceAutoCompletionResult.NoPlannedEvent(raceDate))
    }

    @Test
    fun `does not complete a run linked to a session`(): Unit = runBlocking {
        createRace()
        val activityId = saveRun(raceDate, meters = 10_000.0)
        database.sessionExecutionDao().insert(
            SessionExecutionEntity(
                id = "execution-1",
                plannedSessionId = null,
                activityId = activityId,
                startedAt = null,
                endedAt = null,
                pausesJson = "[]",
                durationSec = 3_000,
                distanceM = 10_000.0,
                rpe = null,
                source = ExecutionSource.ACTIVITY,
                createdAt = clock.millis(),
            ),
        )

        assertThat(autoComplete.evaluate(activityId)).isEqualTo(
            RaceAutoCompletionResult.Skipped(
                RaceAutoCompletionRejection.ACTIVITY_ALREADY_LINKED_TO_SESSION,
            ),
        )
        assertThat(raceEvents.getPlanned()).isNotNull
    }

    @Test
    fun `does not choose between two eligible runs`(): Unit = runBlocking {
        createRace()
        val first = saveRun(raceDate, meters = 10_000.0)
        saveRun(raceDate, meters = 10_100.0)

        assertThat(autoComplete.evaluate(first))
            .isEqualTo(RaceAutoCompletionResult.Skipped(RaceAutoCompletionRejection.MULTIPLE_CANDIDATES))
        assertThat(raceEvents.getPlanned()).isNotNull
    }

    @Test
    fun `does not complete a run linked to another race`(): Unit = runBlocking {
        val firstRace = createRace()
        val activityId = saveRun(raceDate, meters = 10_000.0)
        raceEvents.complete(firstRace.id, activityId, RaceCompletionSource.AUTO_IMPORT)
        val secondRace = (raceEvents.create(NewRaceEvent(RaceDistance.TEN_K, raceDate)) as RaceEventOutcome.Saved).event

        assertThat(autoComplete.evaluate(activityId)).isEqualTo(
            RaceAutoCompletionResult.Skipped(
                RaceAutoCompletionRejection.ACTIVITY_ALREADY_LINKED_TO_RACE,
            ),
        )
        assertThat(raceEvents.getById(secondRace.id)?.status).isEqualTo(RaceStatus.PLANNED)
    }

    @Test
    fun `reimporting the same run is idempotent`(): Unit = runBlocking {
        val event = createRace()
        val activityId = saveRun(raceDate, meters = 10_000.0)

        val first = autoComplete.evaluate(activityId)
        val second = autoComplete.evaluate(activityId)

        assertThat(first).isInstanceOf(RaceAutoCompletionResult.Completed::class.java)
        assertThat(second).isEqualTo(RaceAutoCompletionResult.NoPlannedEvent(raceDate))
        val completed = raceEvents.getById(event.id)!!
        assertThat(completed.activityId).isEqualTo(activityId)
        assertThat(completed.completionSource).isEqualTo(RaceCompletionSource.AUTO_IMPORT)
    }

    @Test
    fun `a treadmill run is accepted`(): Unit = runBlocking {
        createRace()
        val activityId = saveRun(raceDate, meters = 10_000.0, sport = Sport.TREADMILL)

        assertThat(autoComplete.evaluate(activityId)).isInstanceOf(RaceAutoCompletionResult.Completed::class.java)
    }

    @Test
    fun `undo reopens the event and blocks the same run from being inferred again`(): Unit = runBlocking {
        val event = createRace()
        val activityId = saveRun(raceDate, meters = 10_000.0)
        autoComplete.evaluate(activityId)

        val outcome = undo(event.id)

        assertThat(outcome).isInstanceOf(RaceEventOutcome.Saved::class.java)
        val reopened = raceEvents.getById(event.id)!!
        assertThat(reopened.status).isEqualTo(RaceStatus.PLANNED)
        assertThat(reopened.activityId).isNull()
        assertThat(reopened.completionSource).isNull()
        assertThat(declines.declinedActivityIds()).containsExactly(activityId)
        assertThat(autoComplete.evaluate(activityId)).isEqualTo(
            RaceAutoCompletionResult.Skipped(RaceAutoCompletionRejection.PREVIOUSLY_DECLINED),
        )
    }

    @Test
    fun `undo does not touch an explicit completion`(): Unit = runBlocking {
        val event = createRace()
        raceEvents.complete(event.id, source = RaceCompletionSource.USER)

        val outcome = undo(event.id)

        assertThat(outcome).isInstanceOf(RaceEventOutcome.Rejected::class.java)
        assertThat(raceEvents.getById(event.id)?.status).isEqualTo(RaceStatus.COMPLETED)
        assertThat(declines.declinedActivityIds()).isEmpty()
    }

    private suspend fun createRace(): RaceEvent =
        (raceEvents.create(NewRaceEvent(RaceDistance.TEN_K, raceDate)) as RaceEventOutcome.Saved).event

    private suspend fun saveRun(date: LocalDate, meters: Double, sport: Sport = Sport.RUNNING): String {
        runSequence += 1
        val saved = activities.saveImportedActivity(
            ImportedActivity(
                source = ActivitySource.FIT,
                sport = sport,
                startTime = date.atTime(7, 0).plusMinutes(runSequence * 10L).atZone(zone).toInstant(),
                durationSec = 3_000,
                distanceM = meters,
                averagePace = Pace.fromSecondsPerKm(300),
            ),
        )
        return (saved as SaveActivityResult.Saved).id
    }
}
