package com.meucoach.core.domain.replan

import com.meucoach.core.domain.prescription.PlanDecision
import com.meucoach.core.domain.prescription.PlanDecisionReason
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.QualityKind
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * C4-47 (ADR 0048 §2.2): the upgrade regenerates only strictly future days,
 * preserves today/past/DONE/CLOSED, adds at most one structural quality slot
 * and never grows volume, the long run or a second quality.
 */
class PlanUpgradeTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val tuesday = monday.plusDays(1)
    private val wednesday = monday.plusDays(2)
    private val today = LocalDate.of(2026, 11, 4)
    private val friday = monday.plusDays(4)
    private val sunday = monday.plusDays(6)
    private val target = "weekly-plan-v7"

    @Test
    fun `adds one structural quality without growing volume`() {
        val current = week(
            engineVersion = "weekly-plan-v6",
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
                session(wednesday, WorkoutType.EASY, 40, "Fácil de hoje"),
                session(friday, WorkoutType.EASY, 40, "Fácil de sexta"),
                session(sunday, WorkoutType.LONG, 40, "Corrida mais longa"),
            ),
        )
        val regenerated = week(
            engineVersion = target,
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
                session(wednesday, WorkoutType.EASY, 40, "Fácil de hoje"),
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
        )

        val result = PlanUpgrade.upgrade(current, regenerated, today, target, Experience.INTERMEDIATE)

        assertThat(result).isInstanceOf(PlanUpgradeResult.Upgraded::class.java)
        val upgraded = (result as PlanUpgradeResult.Upgraded).plan
        assertThat(upgraded.engineVersion).isEqualTo(target)
        assertThat(upgraded.createdOn).isEqualTo(monday)
        val byDate = upgraded.sessions.associateBy { it.date }
        assertThat(byDate[tuesday]?.status).isEqualTo(SessionStatus.DONE)
        assertThat(byDate[friday]?.type).isEqualTo(WorkoutType.QUALITY)
        assertThat(byDate[friday]?.durationMin).isEqualTo(40)
        assertThat(upgraded.weeklyMinutes).isEqualTo(200)
        assertThat(upgraded.sessions.count { it.type == WorkoutType.QUALITY }).isEqualTo(1)
    }

    @Test
    fun `is a no-op when the stored version is already current`() {
        val current = week(engineVersion = target)
        val regenerated = week(engineVersion = target)

        val result = PlanUpgrade.upgrade(current, regenerated, today, target, Experience.INTERMEDIATE)

        assertThat(result).isInstanceOf(PlanUpgradeResult.Skipped::class.java)
    }

    @Test
    fun `never touches a past week`() {
        val pastMonday = monday.minusWeeks(1)
        val current = week(engineVersion = "weekly-plan-v6", weekStart = pastMonday)
        val regenerated = week(engineVersion = target, weekStart = pastMonday)

        val result = PlanUpgrade.upgrade(current, regenerated, today, target, Experience.INTERMEDIATE)

        assertThat(result).isInstanceOf(PlanUpgradeResult.Skipped::class.java)
    }

    @Test
    fun `refuses a second quality beyond the single slot`() {
        val current = week(
            engineVersion = "weekly-plan-v6",
            sessions = listOf(
                session(monday, WorkoutType.EASY, 40, "Fácil de segunda"),
                session(wednesday, WorkoutType.EASY, 40, "Fácil de hoje"),
                session(friday, WorkoutType.QUALITY, 40, "Fartlek leve", kind = QualityKind.FARTLEK),
                session(sunday, WorkoutType.LONG, 40, "Corrida mais longa"),
            ),
        )
        val regenerated = week(
            engineVersion = target,
            sessions = listOf(
                session(monday, WorkoutType.EASY, 40, "Fácil de segunda"),
                session(wednesday, WorkoutType.EASY, 40, "Fácil de hoje"),
                session(friday, WorkoutType.QUALITY, 40, "Fartlek leve", kind = QualityKind.FARTLEK),
                session(monday.plusDays(5), WorkoutType.QUALITY, 30, "Intervalado", kind = QualityKind.INTERVALS),
                session(sunday, WorkoutType.LONG, 40, "Corrida mais longa"),
            ),
        )

        val result = PlanUpgrade.upgrade(current, regenerated, today, target, Experience.INTERMEDIATE)

        assertThat(result).isInstanceOf(PlanUpgradeResult.Skipped::class.java)
    }

    @Test
    fun `refuses to grow future volume`() {
        val current = week(
            engineVersion = "weekly-plan-v6",
            sessions = listOf(
                session(monday, WorkoutType.EASY, 40, "Fácil de segunda"),
                session(wednesday, WorkoutType.EASY, 40, "Fácil de hoje"),
                session(friday, WorkoutType.EASY, 40, "Fácil de sexta"),
                session(sunday, WorkoutType.LONG, 40, "Corrida mais longa"),
            ),
        )
        val regenerated = week(
            engineVersion = target,
            sessions = listOf(
                session(monday, WorkoutType.EASY, 40, "Fácil de segunda"),
                session(wednesday, WorkoutType.EASY, 40, "Fácil de hoje"),
                session(friday, WorkoutType.EASY, 60, "Fácil longo"),
                session(sunday, WorkoutType.LONG, 40, "Corrida mais longa"),
            ),
        )

        val result = PlanUpgrade.upgrade(current, regenerated, today, target, Experience.INTERMEDIATE)

        assertThat(result).isInstanceOf(PlanUpgradeResult.Skipped::class.java)
    }

    @Test
    fun `preserves future done sessions`() {
        val doneFriday = session(
            friday,
            WorkoutType.EASY,
            40,
            "Fácil de sexta",
            status = SessionStatus.DONE,
            evidence = CompletionEvidence.MANUAL,
        )
        val thursday = monday.plusDays(3)
        val current = week(
            engineVersion = "weekly-plan-v6",
            sessions = listOf(
                session(monday, WorkoutType.EASY, 40, "Fácil de segunda"),
                session(wednesday, WorkoutType.EASY, 40, "Fácil de hoje"),
                doneFriday,
                session(thursday, WorkoutType.EASY, 30, "Fácil de quinta"),
                session(sunday, WorkoutType.LONG, 40, "Corrida mais longa"),
            ),
        )
        val regenerated = week(
            engineVersion = target,
            sessions = listOf(
                session(monday, WorkoutType.EASY, 40, "Fácil de segunda"),
                session(wednesday, WorkoutType.EASY, 40, "Fácil de hoje"),
                session(friday, WorkoutType.QUALITY, 40, "Fartlek leve", kind = QualityKind.FARTLEK),
                session(thursday, WorkoutType.QUALITY, 30, "Fartlek leve", kind = QualityKind.FARTLEK),
                session(sunday, WorkoutType.LONG, 40, "Corrida mais longa"),
            ),
        )

        val result = PlanUpgrade.upgrade(current, regenerated, today, target, Experience.INTERMEDIATE)

        assertThat(result).isInstanceOf(PlanUpgradeResult.Upgraded::class.java)
        val upgraded = (result as PlanUpgradeResult.Upgraded).plan
        assertThat(upgraded.sessions.first { it.date == friday }).isEqualTo(doneFriday)
        assertThat(upgraded.sessions.first { it.date == thursday }.type).isEqualTo(WorkoutType.QUALITY)
    }

    private fun week(
        engineVersion: String,
        weekStart: LocalDate = monday,
        sessions: List<PlannedSession> = listOf(
            session(monday, WorkoutType.EASY, 40, "Fácil de segunda"),
            session(wednesday, WorkoutType.EASY, 40, "Fácil de hoje"),
            session(friday, WorkoutType.EASY, 40, "Fácil de sexta"),
            session(sunday, WorkoutType.LONG, 40, "Corrida mais longa"),
        ),
    ) = WeeklyPlan(
        weekStart = weekStart,
        sessions = sessions.filter { it.date in weekStart..weekStart.plusDays(6) || weekStart != monday },
        weeklyMinutes = sessions.sumOf { it.durationMin ?: 0 },
        longRunMinutes = sessions.firstOrNull { it.type == WorkoutType.LONG }?.durationMin,
        qualityCount = sessions.count { it.type == WorkoutType.QUALITY },
        rationale = "Semana base.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-03"),
        evidenceIds = listOf("EV-PFITZINGER-2008"),
        createdOn = weekStart,
        engineVersion = engineVersion,
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
