package com.meucoach.core.domain.prescription

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.LocalDate

/**
 * C4-01 — opportunity models (ADR 0044 §2.2, PRD §5.2).
 *
 * The opportunity is a read contract over a planned `QUALITY` session: it
 * carries the day, the quality kind and the structural decision, never the
 * readiness of the week's first day. Strides are micro-doses, not a main
 * opportunity.
 */
class QualityOpportunityTest {
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `quality kinds keep the legacy values and add the catalog kinds`() {
        assertThat(QualityKind.entries).containsExactly(
            QualityKind.STRIDES_ONLY,
            QualityKind.CONTINUOUS,
            QualityKind.INTERVALS,
            QualityKind.FARTLEK,
            QualityKind.HILLS,
            QualityKind.RACE_PACE,
        )
    }

    @Test
    fun `legacy planned quality without a kind still constructs`() {
        val session = session(kind = null)

        assertThat(session.qualityKind).isNull()
        assertThat(session.planDecision).isNull()
        assertThat(QualityOpportunity.from(session)).isNull()
    }

    @Test
    fun `quality opportunity reads the planned session contract`() {
        val decision = PlanDecision(
            reason = PlanDecisionReason.QUALITY_OPPORTUNITY,
            detail = "3 dias disponíveis e longão no domingo",
        )
        val session = session(kind = QualityKind.INTERVALS, planDecision = decision)

        val opportunity = QualityOpportunity.from(session)

        assertThat(opportunity).isNotNull
        requireNotNull(opportunity)
        assertThat(opportunity.date).isEqualTo(monday)
        assertThat(opportunity.kind).isEqualTo(QualityKind.INTERVALS)
        assertThat(opportunity.structuralReason).isEqualTo("Ritmo forte com 3 min fortes")
        assertThat(opportunity.status).isEqualTo(SessionStatus.PLANNED)
        assertThat(opportunity.planDecision).isEqualTo(decision)
        assertThat(opportunity.isOpen).isTrue()
    }

    @Test
    fun `done opportunity is historical and not open`() {
        val session = session(
            kind = QualityKind.FARTLEK,
            status = SessionStatus.DONE,
            completionEvidence = com.meucoach.core.model.CompletionEvidence.MANUAL,
        )

        val opportunity = QualityOpportunity.from(session)

        assertThat(opportunity).isNotNull
        requireNotNull(opportunity)
        assertThat(opportunity.isOpen).isFalse()
    }

    @Test
    fun `strides are a micro dose and never a main opportunity`() {
        val session = session(kind = QualityKind.STRIDES_ONLY)

        assertThat(QualityOpportunity.from(session)).isNull()
    }

    @Test
    fun `easy session cannot carry a quality kind`() {
        assertThatThrownBy {
            session(type = WorkoutType.EASY, kind = QualityKind.CONTINUOUS)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("quality")
    }

    @Test
    fun `plan decision reason covers the catalog and detail may be absent`() {
        val decision = PlanDecision(PlanDecisionReason.VOLUME_MAINTAINED)

        assertThat(decision.detail).isNull()
        assertThat(PlanDecisionReason.entries).containsExactly(
            PlanDecisionReason.QUALITY_OPPORTUNITY,
            PlanDecisionReason.QUALITY_BLOCKED_BEGINNER,
            PlanDecisionReason.QUALITY_BLOCKED_DAYS,
            PlanDecisionReason.QUALITY_BLOCKED_SAFE_DAY,
            PlanDecisionReason.PROGRESSION_COMPLETED,
            PlanDecisionReason.PROGRESSION_BLOCKED_COMPLETION,
            PlanDecisionReason.PROGRESSION_BLOCKED_READINESS,
            PlanDecisionReason.PROGRESSION_BLOCKED_LOAD,
            PlanDecisionReason.PROGRESSION_BLOCKED_ACWR,
            PlanDecisionReason.PROGRESSION_BLOCKED_MONOTONY,
            PlanDecisionReason.PROGRESSION_BLOCKED_STRAIN,
            PlanDecisionReason.VOLUME_MAINTAINED,
            PlanDecisionReason.VOLUME_CAPPED_BY_AVAILABILITY,
            PlanDecisionReason.LONG_DAY_TOO_SHORT,
        )
    }

    @Test
    fun `quality block reason covers every day gate reason`() {
        assertThat(QualityBlockReason.entries).containsExactly(
            QualityBlockReason.ILLNESS_OR_RED_READINESS,
            QualityBlockReason.BEGINNER_GUARD,
            QualityBlockReason.SUBJECTIVE_MISSING,
            QualityBlockReason.SHORT_SLEEP,
            QualityBlockReason.RECOVERY_WINDOW,
            QualityBlockReason.TAPER_WINDOW,
            QualityBlockReason.RACE_WEEK,
            QualityBlockReason.LOAD_STALE,
            QualityBlockReason.LOAD_INSUFFICIENT,
            QualityBlockReason.MONOTONY_HIGH,
            QualityBlockReason.STRAIN_SPIKE,
            QualityBlockReason.ACWR_HIGH,
        )
    }

    @Test
    fun `plan decision rejects a blank detail`() {
        assertThatThrownBy {
            PlanDecision(PlanDecisionReason.QUALITY_BLOCKED_DAYS, "  ")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("detail")
    }

    @Test
    fun `opportunity rejects strides and a blank structural reason`() {
        assertThatThrownBy {
            QualityOpportunity(
                date = monday,
                kind = QualityKind.STRIDES_ONLY,
                structuralReason = "Micro-dose",
                status = SessionStatus.PLANNED,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Strides")

        assertThatThrownBy {
            QualityOpportunity(
                date = monday,
                kind = QualityKind.CONTINUOUS,
                structuralReason = "  ",
                status = SessionStatus.PLANNED,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("structure")
    }

    @Test
    fun `from ignores an easy session without a kind`() {
        val session = session(type = WorkoutType.EASY, kind = null)

        assertThat(QualityOpportunity.from(session)).isNull()
    }

    @Test
    fun `eligibility accepts a coherent eligible answer`() {
        val eligibility = QualityEligibility(
            eligible = true,
            reason = null,
            detail = null,
            ruleId = "R-PER-03",
            evidenceIds = listOf("EV-SEILER-2010"),
            unlockHint = null,
        )

        assertThat(eligibility.eligible).isTrue()
    }

    @Test
    fun `eligibility rejects an eligible answer with a block reason`() {
        assertThatThrownBy {
            QualityEligibility(
                eligible = true,
                reason = QualityBlockReason.SHORT_SLEEP,
                detail = "sono curto",
                ruleId = "R-READY-04",
                evidenceIds = listOf("EV-WALSH-2021"),
                unlockHint = "Durma mais.",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("agree")
    }

    @Test
    fun `eligibility rejects a blocked answer without detail or hint`() {
        assertThatThrownBy {
            QualityEligibility(
                eligible = false,
                reason = QualityBlockReason.SHORT_SLEEP,
                detail = null,
                ruleId = "R-READY-04",
                evidenceIds = listOf("EV-WALSH-2021"),
                unlockHint = "Durma mais.",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("failing value")

        assertThatThrownBy {
            QualityEligibility(
                eligible = false,
                reason = QualityBlockReason.SHORT_SLEEP,
                detail = "sono curto",
                ruleId = "R-READY-04",
                evidenceIds = listOf("EV-WALSH-2021"),
                unlockHint = null,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("unlocks")
    }

    @Test
    fun `eligibility rejects a blank rule or empty evidence`() {
        assertThatThrownBy {
            QualityEligibility(
                eligible = true,
                reason = null,
                detail = null,
                ruleId = " ",
                evidenceIds = listOf("EV-SEILER-2010"),
                unlockHint = null,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("rule")

        assertThatThrownBy {
            QualityEligibility(
                eligible = true,
                reason = null,
                detail = null,
                ruleId = "R-PER-03",
                evidenceIds = emptyList(),
                unlockHint = null,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("evidence")
    }

    private fun session(
        type: WorkoutType = WorkoutType.QUALITY,
        kind: QualityKind? = QualityKind.CONTINUOUS,
        status: SessionStatus = SessionStatus.PLANNED,
        planDecision: PlanDecision? = null,
        completionEvidence: com.meucoach.core.model.CompletionEvidence? = null,
    ) = PlannedSession(
        date = monday,
        type = type,
        title = "Ritmo forte",
        durationMin = 40,
        rationale = "Ritmo forte com 3 min fortes",
        ruleIds = listOf("R-PER-03"),
        evidenceIds = listOf("EV-SEILER-2010"),
        qualityKind = kind,
        planDecision = planDecision,
        status = status,
        completionEvidence = completionEvidence,
    )
}
