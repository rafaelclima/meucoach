package com.meucoach.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionClosureReason
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.replan.ReplanSuggestion
import com.meucoach.core.domain.replan.ReplanTrigger
import com.meucoach.core.model.DataConfidence
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrainingPlanRepositoryTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: TrainingPlanRepository
    private lateinit var executions: SessionExecutionRepository
    private val clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC)
    private val monday = LocalDate.of(2026, 11, 2)
    private val today = LocalDate.of(2026, 11, 4)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = TrainingPlanRepository(database, clock)
        executions = SessionExecutionRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saves and reads a week`(): Unit = runBlocking {
        repository.saveWeek(plan())

        assertThat(repository.getWeek(monday)).isEqualTo(plan())
        assertThat(repository.observeWeek(monday).first()).isEqualTo(plan())
    }

    @Test
    fun `replaces sessions when the week is saved again`(): Unit = runBlocking {
        repository.saveWeek(plan())
        val updated = plan().copy(
            sessions = plan().sessions.take(1).map { it.copy(durationMin = 30) },
            weeklyMinutes = 30,
            longRunMinutes = null,
        )

        repository.saveWeek(updated)

        val stored = repository.getWeek(monday)
        assertThat(stored?.sessions).hasSize(1)
        assertThat(stored?.sessions?.single()?.durationMin).isEqualTo(30)
        assertThat(stored?.weeklyMinutes).isEqualTo(30)
    }

    @Test
    fun `completes a session only through an execution with evidence`(): Unit = runBlocking {
        repository.saveWeek(plan())

        val outcome = executions.recordManualExecution("2026-11-02-2026-11-02", durationMin = 40, rpe = 5)
        assertThat(outcome).isInstanceOf(CompletionOutcome.Recorded::class.java)
        val done = repository.getWeek(monday)?.sessions?.first()
        assertThat(done?.status).isEqualTo(SessionStatus.DONE)
        assertThat(done?.completionEvidence?.name).isEqualTo("MANUAL")

        val closed = executions.closeSession(
            "2026-11-02-2026-11-07",
            SessionClosureReason.USER_SKIPPED,
            note = "dor no joelho",
        )
        assertThat(closed).isTrue()
        val skipped = repository.getWeek(monday)?.sessions?.last()
        assertThat(skipped?.status).isEqualTo(SessionStatus.CLOSED)
        assertThat(skipped?.closureReason).isEqualTo(SessionClosureReason.USER_SKIPPED)
        assertThat(skipped?.skipReason).isEqualTo("dor no joelho")
    }

    @Test
    fun `deletes the week and its sessions`(): Unit = runBlocking {
        repository.saveWeek(plan())

        assertThat(repository.deleteWeek(monday)).isEqualTo(1)
        assertThat(repository.getWeek(monday)).isNull()
        assertThat(database.trainingPlanDao().getSessions(monday.toString())).isEmpty()
    }

    @Test
    fun `updates session content without touching status`(): Unit = runBlocking {
        repository.saveWeek(plan())
        executions.recordManualExecution("2026-11-02-2026-11-02", durationMin = 40, rpe = 5)
        val updated = repository.getWeek(monday)!!.sessions.map { session ->
            if (session.date == monday) {
                session.copy(
                    type = WorkoutType.QUALITY,
                    title = "Tempo",
                    durationMin = 30,
                    rationale = "Qualidade.",
                    ruleIds = listOf("R-PLAN-02"),
                )
            } else {
                session
            }
        }

        assertThat(repository.updateSessions(monday, updated)).isTrue()

        val stored = repository.getWeek(monday)!!
        val first = stored.sessions.first { it.date == monday }
        assertThat(first.type).isEqualTo(WorkoutType.QUALITY)
        assertThat(first.title).isEqualTo("Tempo")
        assertThat(first.durationMin).isEqualTo(30)
        assertThat(first.status).isEqualTo(SessionStatus.DONE)
    }

    @Test
    fun `counts only consecutive weeks before the target week`(): Unit = runBlocking {
        repository.saveWeek(plan(monday.minusWeeks(1)))
        repository.saveWeek(plan(monday.minusWeeks(2)))
        repository.saveWeek(plan(monday.minusWeeks(4)))

        assertThat(repository.consecutiveWeeksBefore(monday)).isEqualTo(2)
    }

    @Test
    fun `loads multiple weeks and skips missing ones`(): Unit = runBlocking {
        repository.saveWeek(plan(monday.minusWeeks(1)))
        repository.saveWeek(plan(monday))

        val weeks = repository.getWeeks(
            listOf(monday.minusWeeks(2), monday.minusWeeks(1), monday),
        )

        assertThat(weeks.map { it.weekStart })
            .containsExactly(monday.minusWeeks(1), monday)
    }

    @Test
    fun `starts empty`(): Unit = runBlocking {
        assertThat(repository.getWeek(monday)).isNull()
        assertThat(repository.observeWeek(monday).first()).isNull()
        assertThat(repository.consecutiveWeeksBefore(monday)).isZero()
    }

    @Test
    fun `persists the plan creation date and session targets`(): Unit = runBlocking {
        val createdOn = monday.plusDays(3)
        val targets = com.meucoach.core.domain.prescription.PrescriptionTargets(
            pace = com.meucoach.core.domain.prescription.PaceTarget(
                fast = com.meucoach.core.model.Pace.fromSecondsPerKm(360),
                slow = com.meucoach.core.model.Pace.fromSecondsPerKm(370),
            ),
            heartRate = com.meucoach.core.domain.prescription.HeartRateTarget(146, 161),
            zone = "Z2",
            distanceKm = 6.4,
        )
        val withTargets = plan().copy(
            createdOn = createdOn,
            sessions = plan().sessions.map { it.copy(targets = targets) },
        )

        repository.saveWeek(withTargets)

        val stored = repository.getWeek(monday)
        assertThat(stored?.createdOn).isEqualTo(createdOn)
        assertThat(stored?.sessions?.map { it.targets }).containsOnly(targets)
    }

    @Test
    fun `applies a revision atomically and keeps an append-only snapshot`(): Unit = runBlocking {
        repository.saveWeek(revisionWeek())

        val outcome = repository.applyReplan(
            suggestion = suggestion(),
            candidate = candidateWeek(),
            today = today,
            currentInputsHash = "inputs-2",
        )

        assertThat(outcome).isInstanceOf(ReplanApplyOutcome.Applied::class.java)
        assertThat((outcome as ReplanApplyOutcome.Applied).version).isEqualTo(2)

        val stored = repository.getWeekRecord(monday)!!
        assertThat(stored.version).isEqualTo(2)
        assertThat(stored.revisionReason).isEqualTo(suggestion().rationale)
        assertThat(stored.revisedAt).isEqualTo(clock.instant())
        assertThat(stored.inputsHash).isEqualTo("inputs-2")
        assertThat(stored.availabilityHash).isEqualTo("availability-2")
        assertThat(stored.plan.createdOn).isEqualTo(monday)
        assertThat(stored.plan.weeklyMinutes).isEqualTo(140)
        assertThat(stored.plan.longRunMinutes).isEqualTo(45)

        val sessions = stored.plan.sessions.associateBy { it.date }
        assertThat(sessions[monday]?.durationMin).isEqualTo(40)
        assertThat(sessions[today]?.title).isEqualTo("Qualidade de hoje")
        assertThat(sessions[monday.plusDays(3)]?.durationMin).isEqualTo(25)
        assertThat(sessions[monday.plusDays(4)]?.durationMin).isEqualTo(45)

        val latest = repository.getLatestRevision(monday)!!
        assertThat(latest.fromVersion).isEqualTo(1)
        assertThat(latest.toVersion).isEqualTo(2)
        assertThat(latest.replacedSessions.map { it.date })
            .containsExactly(monday.plusDays(3), monday.plusDays(4))
        assertThat(latest.replacedSessions.first { it.date == monday.plusDays(3) }.durationMin)
            .isEqualTo(30)
        assertThat(latest.replacedSessions.first { it.date == monday.plusDays(3) }.status)
            .isEqualTo(SessionStatus.PLANNED)

        val revision = database.weeklyPlanRevisionDao().get(monday.toString(), 2)!!
        assertThat(revision.fromVersion).isEqualTo(1)
        assertThat(revision.fromInputsHash).isNull()
        assertThat(revision.toInputsHash).isEqualTo("inputs-2")
        assertThat(revision.fromAvailabilityHash).isNull()
        assertThat(revision.toAvailabilityHash).isEqualTo("availability-2")
        assertThat(
            revision.generatorVersion,
        ).isEqualTo(com.meucoach.core.domain.prescription.WeeklyPlanGenerator.ENGINE_VERSION)
        assertThat(revision.trigger).isEqualTo(ReplanTrigger.USER_REQUEST.name)
        assertThat(revision.reason).isEqualTo(suggestion().rationale)
        assertThat(JSONObject(revision.previousPlanJson).getInt("version")).isEqualTo(1)
        assertThat(JSONArray(revision.replacedSessionsJson).length()).isEqualTo(2)
    }

    @Test
    fun `recalculates weekly aggregates from the merged sessions`(): Unit = runBlocking {
        repository.saveWeek(revisionWeek())
        val inflated = candidateWeek().copy(
            weeklyMinutes = 999,
            longRunMinutes = 999,
            qualityCount = 9,
        )

        val outcome = repository.applyReplan(
            suggestion = suggestion(),
            candidate = inflated,
            today = today,
        )

        assertThat(outcome).isInstanceOf(ReplanApplyOutcome.Applied::class.java)
        val stored = repository.getWeek(monday)!!
        assertThat(stored.weeklyMinutes).isEqualTo(140)
        assertThat(stored.longRunMinutes).isEqualTo(45)
        assertThat(stored.qualityCount).isEqualTo(1)
    }

    @Test
    fun `rejects a stale version without touching the plan`(): Unit = runBlocking {
        repository.saveWeek(revisionWeek())

        val outcome = repository.applyReplan(
            suggestion = suggestion(expectedVersion = 2),
            candidate = candidateWeek(),
            today = today,
        )

        assertThat(outcome).isEqualTo(
            ReplanApplyOutcome.Failed(
                ReplanApplyFailure.STALE_VERSION,
                "O plano mudou desde esta sugestão. Avalie novamente.",
            ),
        )
        assertThat(repository.getWeekRecord(monday)?.version).isEqualTo(1)
        assertThat(repository.getWeek(monday)).isEqualTo(revisionWeek())
        assertThat(database.weeklyPlanRevisionDao().getAll()).isEmpty()
    }

    @Test
    fun `rejects inputs recomputed after the suggestion`(): Unit = runBlocking {
        repository.saveWeek(revisionWeek())

        val outcome = repository.applyReplan(
            suggestion = suggestion(),
            candidate = candidateWeek(),
            today = today,
            currentInputsHash = "other-inputs",
        )

        assertThat(outcome).isEqualTo(
            ReplanApplyOutcome.Failed(
                ReplanApplyFailure.STALE_INPUTS,
                "Os dados usados na sugestão mudaram. Avalie novamente.",
            ),
        )
        assertThat(repository.getWeekRecord(monday)?.version).isEqualTo(1)
        assertThat(database.weeklyPlanRevisionDao().getAll()).isEmpty()
    }

    @Test
    fun `rejects a stale availability baseline without touching the plan`(): Unit = runBlocking {
        repository.saveWeek(revisionWeek())
        val first = repository.applyReplan(
            suggestion = suggestion(),
            candidate = candidateWeek(),
            today = today,
            currentInputsHash = "inputs-2",
        )
        assertThat(first).isInstanceOf(ReplanApplyOutcome.Applied::class.java)

        val outcome = repository.applyReplan(
            suggestion = suggestion(expectedVersion = 2, expectedAvailabilityHash = "old-baseline"),
            candidate = candidateWeek(),
            today = today,
        )

        assertThat(outcome).isEqualTo(
            ReplanApplyOutcome.Failed(
                ReplanApplyFailure.STALE_AVAILABILITY,
                "Sua disponibilidade mudou desde esta sugestão. Avalie novamente.",
            ),
        )
        assertThat(repository.getWeekRecord(monday)?.version).isEqualTo(2)
        assertThat(database.weeklyPlanRevisionDao().getAll()).hasSize(1)
    }

    @Test
    fun `a new revision is not blocked by the previous version input hash`(): Unit = runBlocking {
        repository.saveWeek(revisionWeek())
        repository.applyReplan(
            suggestion = suggestion(),
            candidate = candidateWeek(),
            today = today,
            currentInputsHash = "inputs-2",
        )

        val second = repository.applyReplan(
            suggestion = suggestion(expectedVersion = 2, expectedInputsHash = "inputs-3"),
            candidate = candidateWeek().copy(rationale = "Revisão com novos dados."),
            today = today,
            currentInputsHash = "inputs-3",
        )

        assertThat(second).isInstanceOf(ReplanApplyOutcome.Applied::class.java)
        assertThat(repository.getWeekRecord(monday)?.version).isEqualTo(3)
        assertThat(repository.getWeekRecord(monday)?.inputsHash).isEqualTo("inputs-3")
        assertThat(database.weeklyPlanRevisionDao().getAll()).hasSize(2)
    }

    @Test
    fun `never replaces a future done or closed session`(): Unit = runBlocking {
        val doneDate = monday.plusDays(3)
        val closedDate = monday.plusDays(4)
        repository.saveWeek(revisionWeek())
        executions.recordManualExecution(
            com.meucoach.core.data.db.entity.plannedSessionId(monday, doneDate),
            durationMin = 30,
            rpe = 5,
        )
        executions.closeSession(
            com.meucoach.core.data.db.entity.plannedSessionId(monday, closedDate),
            SessionClosureReason.USER_SKIPPED,
        )
        val before = repository.getWeek(monday)

        val outcome = repository.applyReplan(
            suggestion = suggestion(),
            candidate = candidateWeek(),
            today = today,
        )

        assertThat(outcome).isEqualTo(
            ReplanApplyOutcome.Failed(
                ReplanApplyFailure.PROTECTED_CONFLICT,
                "Uma sessão já concluída ou encerrada seria alterada; nada foi aplicado.",
            ),
        )
        assertThat(repository.getWeekRecord(monday)?.version).isEqualTo(1)
        assertThat(repository.getWeek(monday)).isEqualTo(before)
        assertThat(database.weeklyPlanRevisionDao().getAll()).isEmpty()
    }

    @Test
    fun `rolls back the whole revision when a candidate insert conflicts`(): Unit = runBlocking {
        repository.saveWeek(revisionWeek())
        val duplicated = candidateWeek().copy(
            sessions = listOf(
                session(monday.plusDays(3), WorkoutType.EASY, 25, "Fácil A"),
                session(monday.plusDays(3), WorkoutType.EASY, 26, "Fácil B"),
                session(monday.plusDays(4), WorkoutType.LONG, 45, "Longão"),
            ),
            weeklyMinutes = 96,
        )

        val outcome = repository.applyReplan(
            suggestion = suggestion(),
            candidate = duplicated,
            today = today,
        )

        assertThat(outcome).isEqualTo(
            ReplanApplyOutcome.Failed(
                ReplanApplyFailure.INVALID_CANDIDATE,
                "A revisão conflitou com o estado atual; nada foi alterado.",
            ),
        )
        assertThat(repository.getWeekRecord(monday)?.version).isEqualTo(1)
        assertThat(repository.getWeek(monday)).isEqualTo(revisionWeek())
        assertThat(database.weeklyPlanRevisionDao().getAll()).isEmpty()
    }

    @Test
    fun `captures a legacy availability baseline only while it is unknown`(): Unit = runBlocking {
        repository.saveWeek(revisionWeek())

        assertThat(repository.captureAvailabilityBaseline(monday, "availability-1")).isTrue()
        assertThat(repository.captureAvailabilityBaseline(monday, "availability-2")).isFalse()

        val stored = repository.getWeekRecord(monday)!!
        assertThat(stored.version).isEqualTo(1)
        assertThat(stored.availabilityHash).isEqualTo("availability-1")
        assertThat(stored.revisionReason).isNull()
    }

    private fun revisionWeek() = WeeklyPlan(
        weekStart = monday,
        sessions = listOf(
            session(monday, WorkoutType.EASY, 40, "Fácil de segunda"),
            session(today, WorkoutType.QUALITY, 30, "Qualidade de hoje"),
            session(monday.plusDays(3), WorkoutType.EASY, 30, "Fácil de sexta"),
            session(monday.plusDays(4), WorkoutType.LONG, 50, "Longão"),
        ),
        weeklyMinutes = 150,
        longRunMinutes = 50,
        qualityCount = 1,
        rationale = "Semana original.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-03"),
        evidenceIds = listOf("EV-PFITZINGER-2008"),
    )

    private fun candidateWeek() = WeeklyPlan(
        weekStart = monday,
        sessions = listOf(
            session(monday, WorkoutType.EASY, 40, "Fácil de segunda"),
            session(today, WorkoutType.QUALITY, 30, "Qualidade de hoje"),
            session(monday.plusDays(3), WorkoutType.EASY, 25, "Fácil curto"),
            session(monday.plusDays(4), WorkoutType.LONG, 45, "Longão"),
        ),
        weeklyMinutes = 140,
        longRunMinutes = 45,
        qualityCount = 1,
        rationale = "Semana revisada.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-03"),
        evidenceIds = listOf("EV-PFITZINGER-2008"),
    )

    private fun suggestion(
        expectedVersion: Int = 1,
        expectedInputsHash: String = "inputs-2",
        expectedAvailabilityHash: String? = null,
    ) = ReplanSuggestion(
        weekStart = monday,
        trigger = ReplanTrigger.USER_REQUEST,
        missedTrainableCount = 0,
        expectedVersion = expectedVersion,
        expectedInputsHash = expectedInputsHash,
        expectedAvailabilityHash = expectedAvailabilityHash,
        availabilityHash = "availability-2",
        rationale = "Revisão pedida por você.",
        ruleIds = listOf("R-PLAN-03"),
        evidenceIds = listOf("EV-PFITZINGER-2008"),
        confidence = DataConfidence.MEDIUM,
    )

    private fun session(date: LocalDate, type: WorkoutType, durationMin: Int, title: String) = PlannedSession(
        date = date,
        type = type,
        title = title,
        durationMin = durationMin,
        rationale = "Base.",
        ruleIds = listOf("R-PLAN-03"),
        evidenceIds = listOf("EV-PFITZINGER-2008"),
    )

    private fun plan(weekStart: LocalDate = monday) = WeeklyPlan(
        weekStart = weekStart,
        sessions = listOf(
            PlannedSession(
                date = weekStart,
                type = WorkoutType.EASY,
                title = "Fácil e conversável",
                durationMin = 40,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01", "R-PLAN-06"),
                evidenceIds = listOf("EV-SEILER-2010"),
            ),
            PlannedSession(
                date = weekStart.plusDays(5),
                type = WorkoutType.LONG,
                title = "Longão",
                durationMin = 60,
                rationale = "Longo.",
                ruleIds = listOf("R-PLAN-03"),
                evidenceIds = listOf("EV-PFITZINGER-2008"),
            ),
        ),
        weeklyMinutes = 100,
        longRunMinutes = 60,
        qualityCount = 0,
        rationale = "Sem progressão até existir histórico.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-03"),
        evidenceIds = listOf("EV-PFITZINGER-2008"),
    )
}
