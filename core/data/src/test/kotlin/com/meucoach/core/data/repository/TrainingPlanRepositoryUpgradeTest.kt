package com.meucoach.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.domain.prescription.PlanDecision
import com.meucoach.core.domain.prescription.PlanDecisionReason
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.QualityKind
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.replan.PlanUpgrade
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
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
import java.time.ZoneOffset

/**
 * C4-47 (ADR 0048 §2.2): the stored v6 week upgrades to the target version
 * preserving today/past/DONE, adding at most the structural quality slot and
 * recording an `ENGINE_UPGRADE` revision. A second run changes nothing and a
 * mid-write failure keeps the v6 week intact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrainingPlanRepositoryUpgradeTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: TrainingPlanRepository
    private val clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC)
    private val monday = LocalDate.of(2026, 11, 2)
    private val tuesday = monday.plusDays(1)
    private val today = LocalDate.of(2026, 11, 4)
    private val friday = monday.plusDays(4)
    private val sunday = monday.plusDays(6)
    private val target = "weekly-plan-v7"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = TrainingPlanRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `applies the upgrade preserving history and recording engine upgrade`(): Unit = runBlocking {
        repository.saveWeek(storedWeek())

        val outcome = repository.applyUpgrade(
            candidate = regeneratedWeek(),
            today = today,
            targetVersion = target,
            experience = Experience.INTERMEDIATE,
        )

        assertThat(outcome).isInstanceOf(PlanUpgradeOutcome.Applied::class.java)
        val applied = outcome as PlanUpgradeOutcome.Applied
        assertThat(applied.version).isEqualTo(2)

        val stored = repository.getWeekRecord(monday)!!
        assertThat(stored.version).isEqualTo(2)
        assertThat(stored.generatorVersion).isEqualTo(target)
        assertThat(stored.plan.engineVersion).isEqualTo(target)
        assertThat(stored.plan.createdOn).isEqualTo(monday)
        assertThat(stored.revisionReason).isEqualTo(regeneratedWeek().rationale)
        assertThat(stored.revisedAt).isEqualTo(clock.instant())

        val byDate = stored.plan.sessions.associateBy { it.date }
        assertThat(byDate[tuesday]?.status).isEqualTo(SessionStatus.DONE)
        assertThat(byDate[friday]?.type).isEqualTo(WorkoutType.QUALITY)
        assertThat(byDate[friday]?.durationMin).isEqualTo(40)
        assertThat(stored.plan.weeklyMinutes).isEqualTo(200)
        assertThat(stored.plan.qualityCount).isEqualTo(1)

        val latest = repository.getLatestRevision(monday)!!
        assertThat(latest.trigger).isEqualTo(PlanUpgrade.ENGINE_UPGRADE_TRIGGER)
        assertThat(latest.fromVersion).isEqualTo(1)
        assertThat(latest.toVersion).isEqualTo(2)
        assertThat(latest.replacedSessions.map { it.date }).containsExactly(friday, sunday)
    }

    @Test
    fun `a second upgrade run changes nothing`(): Unit = runBlocking {
        repository.saveWeek(storedWeek())
        val first = repository.applyUpgrade(
            candidate = regeneratedWeek(),
            today = today,
            targetVersion = target,
            experience = Experience.INTERMEDIATE,
        )
        assertThat(first).isInstanceOf(PlanUpgradeOutcome.Applied::class.java)

        val second = repository.applyUpgrade(
            candidate = regeneratedWeek(),
            today = today,
            targetVersion = target,
            experience = Experience.INTERMEDIATE,
        )

        assertThat(second).isInstanceOf(PlanUpgradeOutcome.Skipped::class.java)
        assertThat(repository.getWeekRecord(monday)?.version).isEqualTo(2)
        assertThat(database.weeklyPlanRevisionDao().getAll()).hasSize(1)
    }

    @Test
    fun `skips a week that already matches the target version`(): Unit = runBlocking {
        repository.saveWeek(storedWeek())

        val outcome = repository.applyUpgrade(
            candidate = regeneratedWeek().copy(engineVersion = "weekly-plan-v6"),
            today = today,
            targetVersion = "weekly-plan-v6",
            experience = Experience.INTERMEDIATE,
        )

        assertThat(outcome).isInstanceOf(PlanUpgradeOutcome.Skipped::class.java)
        assertThat(repository.getWeekRecord(monday)?.version).isEqualTo(1)
        assertThat(database.weeklyPlanRevisionDao().getAll()).isEmpty()
    }

    @Test
    fun `rolls back the whole upgrade when a candidate insert conflicts`(): Unit = runBlocking {
        repository.saveWeek(storedWeek())
        // Same future volume (20 + 20) so the domain guard passes, but the two
        // Friday rows share the deterministic session id and abort the insert.
        val duplicated = regeneratedWeek().copy(
            sessions = regeneratedWeek().sessions.flatMap { session ->
                if (session.date == friday) {
                    listOf(
                        session.copy(durationMin = 20),
                        session(friday, WorkoutType.EASY, 20, "Fácil duplicado"),
                    )
                } else {
                    listOf(session)
                }
            },
        )

        val outcome = repository.applyUpgrade(
            candidate = duplicated,
            today = today,
            targetVersion = target,
            experience = Experience.INTERMEDIATE,
        )

        assertThat(outcome).isInstanceOf(PlanUpgradeOutcome.Failed::class.java)
        assertThat((outcome as PlanUpgradeOutcome.Failed).failure).isEqualTo(ReplanApplyFailure.INVALID_CANDIDATE)
        assertThat(repository.getWeekRecord(monday)?.version).isEqualTo(1)
        assertThat(repository.getWeekRecord(monday)?.generatorVersion).isEqualTo("weekly-plan-v6")
        assertThat(repository.getWeek(monday)).isEqualTo(storedWeek())
        assertThat(database.weeklyPlanRevisionDao().getAll()).isEmpty()
    }

    @Test
    fun `never touches a past week`(): Unit = runBlocking {
        val pastMonday = monday.minusWeeks(1)
        val past = storedWeek().copy(
            weekStart = pastMonday,
            createdOn = pastMonday,
            sessions = storedWeek().sessions.map { it.copy(date = it.date.minusWeeks(1)) },
        )
        repository.saveWeek(past)
        val candidate = regeneratedWeek().copy(
            weekStart = pastMonday,
            createdOn = pastMonday,
            sessions = regeneratedWeek().sessions.map { it.copy(date = it.date.minusWeeks(1)) },
        )

        val outcome = repository.applyUpgrade(
            candidate = candidate,
            today = today,
            targetVersion = target,
            experience = Experience.INTERMEDIATE,
        )

        assertThat(outcome).isInstanceOf(PlanUpgradeOutcome.Skipped::class.java)
        assertThat(repository.getWeekRecord(pastMonday)?.version).isEqualTo(1)
        assertThat(repository.getWeekRecord(pastMonday)?.generatorVersion).isEqualTo("weekly-plan-v6")
        assertThat(database.weeklyPlanRevisionDao().getAll()).isEmpty()
    }

    private fun storedWeek() = WeeklyPlan(
        weekStart = monday,
        sessions = listOf(
            session(monday, WorkoutType.EASY, 40, "Fácil de segunda"),
            session(
                tuesday,
                WorkoutType.EASY,
                40,
                "Fácil de terça",
                status = SessionStatus.DONE,
                evidence = CompletionEvidence.MANUAL,
            ),
            session(today, WorkoutType.EASY, 40, "Fácil de hoje"),
            session(friday, WorkoutType.EASY, 40, "Fácil de sexta"),
            session(sunday, WorkoutType.LONG, 40, "Corrida mais longa"),
        ),
        weeklyMinutes = 200,
        longRunMinutes = 40,
        qualityCount = 0,
        rationale = "Semana base.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-03"),
        evidenceIds = listOf("EV-PFITZINGER-2008"),
        createdOn = monday,
        engineVersion = "weekly-plan-v6",
    )

    private fun regeneratedWeek() = WeeklyPlan(
        weekStart = monday,
        sessions = listOf(
            session(monday, WorkoutType.EASY, 40, "Fácil de segunda"),
            session(
                tuesday,
                WorkoutType.EASY,
                40,
                "Fácil de terça",
                status = SessionStatus.DONE,
                evidence = CompletionEvidence.MANUAL,
            ),
            session(today, WorkoutType.EASY, 40, "Fácil de hoje"),
            session(
                friday,
                WorkoutType.QUALITY,
                40,
                "Fartlek leve",
                kind = QualityKind.FARTLEK,
                decision = PlanDecisionReason.QUALITY_OPPORTUNITY,
            ),
            session(sunday, WorkoutType.LONG, 40, "Corrida mais longa"),
        ),
        weeklyMinutes = 200,
        longRunMinutes = 40,
        qualityCount = 1,
        rationale = "Semana base com oportunidade estrutural de qualidade na sexta; o dia confirma.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-03", "R-PER-03"),
        evidenceIds = listOf("EV-PFITZINGER-2008"),
        createdOn = monday,
        engineVersion = target,
    )

    private fun session(
        date: LocalDate,
        type: WorkoutType,
        durationMin: Int,
        title: String,
        status: SessionStatus = SessionStatus.PLANNED,
        evidence: CompletionEvidence? = null,
        kind: QualityKind? = null,
        decision: PlanDecisionReason? = null,
    ) = PlannedSession(
        date = date,
        type = type,
        title = title,
        durationMin = durationMin,
        rationale = "Base.",
        ruleIds = listOf("R-PLAN-03"),
        evidenceIds = listOf("EV-PFITZINGER-2008"),
        status = status,
        completionEvidence = evidence,
        qualityKind = kind,
        planDecision = decision?.let { PlanDecision(it, "Oportunidade estrutural.") },
    )
}
