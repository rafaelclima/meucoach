package com.meucoach.app.application

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.importer.ImportedRunPromptStore
import com.meucoach.app.time.FakeTimeProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.data.repository.ActivityRepository
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.NewRaceEvent
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.SaveActivityResult
import com.meucoach.core.data.repository.SessionExecutionRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionClosureReason
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.Sex
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
class OfferImportedRunTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val today = LocalDate.of(2026, 11, 6)
    private val timeProvider = FakeTimeProvider(
        instant = today.atTime(12, 0).atZone(zone).toInstant(),
        zone = zone,
    )
    private val clock = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var database: MeuCoachDatabase
    private lateinit var activities: ActivityRepository
    private lateinit var plans: TrainingPlanRepository
    private lateinit var checkIns: CheckInRepository
    private lateinit var executions: SessionExecutionRepository
    private lateinit var raceEvents: RaceEventRepository
    private lateinit var store: ImportedRunPromptStore
    private lateinit var offer: OfferImportedRun

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
            idGenerator = { "activity-1" },
        )
        plans = TrainingPlanRepository(database, clock)
        checkIns = CheckInRepository(database, clock)
        executions = SessionExecutionRepository(database, clock)
        raceEvents = RaceEventRepository(database, clock)
        store = ImportedRunPromptStore(context, "offer-import-${System.nanoTime()}")
        offer = OfferImportedRun(activities, plans, checkIns, executions, raceEvents, store, timeProvider)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `queues a run imported today when the session is still planned`(): Unit = runBlocking {
        plans.saveWeek(plan())
        val id = saveRun(today)

        offer.evaluate(id)

        assertThat(store.pending.value).containsExactly(id)
    }

    @Test
    fun `queues an informational prompt when the session is already done`(): Unit = runBlocking {
        plans.saveWeek(
            plan().let { plan ->
                plan.copy(
                    sessions = plan.sessions.map {
                        it.copy(
                            status = SessionStatus.DONE,
                            completionEvidence = CompletionEvidence.MANUAL,
                        )
                    },
                )
            },
        )
        val id = saveRun(today)

        offer.evaluate(id)

        assertThat(store.pending.value).containsExactly(id)
    }

    @Test
    fun `does not offer the legacy session on a race day`(): Unit = runBlocking {
        plans.saveWeek(plan())
        raceEvents.create(NewRaceEvent(RaceDistance.TEN_K, today))
        val id = saveRun(today)

        offer.evaluate(id)

        assertThat(store.pending.value).isEmpty()
    }

    @Test
    fun `ignores a run from another day`(): Unit = runBlocking {
        plans.saveWeek(plan())
        val id = saveRun(today.minusDays(1))

        offer.evaluate(id)

        assertThat(store.pending.value).isEmpty()
    }

    @Test
    fun `ignores a day without a planned session`(): Unit = runBlocking {
        plans.saveWeek(plan().copy(sessions = emptyList()))
        val id = saveRun(today)

        offer.evaluate(id)

        assertThat(store.pending.value).isEmpty()
    }

    @Test
    fun `ignores a closed session`(): Unit = runBlocking {
        plans.saveWeek(
            plan().let { plan ->
                plan.copy(
                    sessions = plan.sessions.map {
                        it.copy(
                            status = SessionStatus.CLOSED,
                            closureReason = SessionClosureReason.USER_SKIPPED,
                            closedAt = Instant.EPOCH,
                        )
                    },
                )
            },
        )
        val id = saveRun(today)

        offer.evaluate(id)

        assertThat(store.pending.value).isEmpty()
    }

    @Test
    fun `ignores a run already linked to the session`(): Unit = runBlocking {
        val id = saveRun(today)
        plans.saveWeek(
            plan().let { plan ->
                plan.copy(
                    sessions = plan.sessions.map {
                        it.copy(
                            status = SessionStatus.DONE,
                            completionEvidence = CompletionEvidence.ACTIVITY,
                            activityId = id,
                        )
                    },
                )
            },
        )

        offer.evaluate(id)

        assertThat(store.pending.value).isEmpty()
    }

    @Test
    fun `ignores an illness day`(): Unit = runBlocking {
        plans.saveWeek(plan())
        checkIns.save(
            DailyCheckIn(
                date = today,
                sleepQuality = 3,
                fatigue = 3,
                soreness = 1,
                stress = 3,
                illnessOrInjury = true,
            ),
        )
        val id = saveRun(today)

        offer.evaluate(id)

        assertThat(store.pending.value).isEmpty()
    }

    private suspend fun saveRun(date: LocalDate): String {
        val saved = activities.saveImportedActivity(
            ImportedActivity(
                source = ActivitySource.FIT,
                sport = Sport.RUNNING,
                startTime = date.atTime(7, 0).atZone(zone).toInstant(),
                durationSec = 2_400,
                distanceM = 6_000.0,
                averagePace = Pace.fromSecondsPerKm(400),
            ),
        )
        return (saved as SaveActivityResult.Saved).id
    }

    private fun plan(): WeeklyPlan {
        val weekStart = today.with(
            java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY),
        )
        return WeeklyPlan(
            weekStart = weekStart,
            sessions = listOf(
                PlannedSession(
                    date = today,
                    type = WorkoutType.EASY,
                    title = "Fácil e conversável",
                    durationMin = 40,
                    rationale = "Base.",
                    ruleIds = listOf("R-ZONE-01"),
                    evidenceIds = listOf("EV-SEILER-2010"),
                ),
            ),
            weeklyMinutes = 40,
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Sem progressão.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
        )
    }
}
