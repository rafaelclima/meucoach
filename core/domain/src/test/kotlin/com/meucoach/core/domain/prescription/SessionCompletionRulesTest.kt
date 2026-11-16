package com.meucoach.core.domain.prescription

import com.meucoach.core.model.CompletionEvidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class SessionCompletionRulesTest {
    private val monday = LocalDate.of(2026, 11, 2)
    private val wednesday = monday.plusDays(2)
    private val thursday = monday.plusDays(3)

    @Test
    fun `rejects a future session`() {
        val decision = SessionCompletionRules.checkDate(thursday, monday, today = wednesday)

        assertThat(decision).isInstanceOf(SessionCompletionDecision.Blocked::class.java)
        assertThat((decision as SessionCompletionDecision.Blocked).message).contains("ainda não aconteceu")
    }

    @Test
    fun `rejects a session from a previous week`() {
        val decision = SessionCompletionRules.checkDate(
            sessionDate = monday.minusWeeks(1),
            weekStart = monday,
            today = monday,
        )

        assertThat(decision).isInstanceOf(SessionCompletionDecision.Blocked::class.java)
        assertThat((decision as SessionCompletionDecision.Blocked).message).contains("semana anterior")
    }

    @Test
    fun `accepts today without a warning`() {
        val decision = SessionCompletionRules.checkDate(thursday, monday, today = thursday)

        assertThat(decision).isEqualTo(SessionCompletionDecision.Allowed())
    }

    @Test
    fun `accepts an earlier day of the same week with a warning`() {
        val decision = SessionCompletionRules.checkDate(wednesday, monday, today = thursday)

        assertThat(decision).isInstanceOf(SessionCompletionDecision.Allowed::class.java)
        assertThat((decision as SessionCompletionDecision.Allowed).warning).isNotNull()
    }

    @Test
    fun `blocks a session that is already done or closed`() {
        assertThat(
            SessionCompletionRules.checkSessionState(
                status = SessionStatus.DONE,
                prescriptionIsRest = false,
                illnessOrInjury = false,
            ),
        ).isInstanceOf(SessionCompletionDecision.Blocked::class.java)

        assertThat(
            SessionCompletionRules.checkSessionState(
                status = SessionStatus.CLOSED,
                prescriptionIsRest = false,
                illnessOrInjury = false,
            ),
        ).isInstanceOf(SessionCompletionDecision.Blocked::class.java)
    }

    @Test
    fun `illness blocks and closes the day instead of leaving it pending`() {
        val decision = SessionCompletionRules.checkSessionState(
            status = SessionStatus.PLANNED,
            prescriptionIsRest = true,
            illnessOrInjury = true,
        ) as SessionCompletionDecision.Blocked

        assertThat(decision.closureReason).isEqualTo(SessionClosureReason.ILLNESS_OR_INJURY)
        assertThat(decision.message).contains("doença ou lesão")
    }

    @Test
    fun `a prescribed rest closes the day without illness`() {
        val decision = SessionCompletionRules.checkSessionState(
            status = SessionStatus.PLANNED,
            prescriptionIsRest = true,
            illnessOrInjury = false,
        ) as SessionCompletionDecision.Blocked

        assertThat(decision.closureReason).isEqualTo(SessionClosureReason.REST_PRESCRIBED)
    }

    @Test
    fun `allows completion when the state is trainable`() {
        assertThat(
            SessionCompletionRules.checkSessionState(
                status = SessionStatus.PLANNED,
                prescriptionIsRest = false,
                illnessOrInjury = false,
            ),
        ).isEqualTo(SessionCompletionDecision.Allowed())
    }

    @Test
    fun `manual evidence needs positive duration and RPE 1 to 10`() {
        assertThat(SessionCompletionRules.checkManual(null, 5))
            .isInstanceOf(SessionCompletionDecision.Blocked::class.java)
        assertThat(SessionCompletionRules.checkManual(0, 5))
            .isInstanceOf(SessionCompletionDecision.Blocked::class.java)
        assertThat(SessionCompletionRules.checkManual(40, null))
            .isInstanceOf(SessionCompletionDecision.Blocked::class.java)
        assertThat(SessionCompletionRules.checkManual(40, 0))
            .isInstanceOf(SessionCompletionDecision.Blocked::class.java)
        assertThat(SessionCompletionRules.checkManual(40, 11))
            .isInstanceOf(SessionCompletionDecision.Blocked::class.java)
        assertThat(SessionCompletionRules.checkManual(40, 1))
            .isEqualTo(SessionCompletionDecision.Allowed())
        assertThat(SessionCompletionRules.checkManual(40, 10))
            .isEqualTo(SessionCompletionDecision.Allowed())
    }

    @Test
    fun `rest closure reason reflects illness`() {
        assertThat(SessionCompletionRules.closureForRest(illnessOrInjury = true))
            .isEqualTo(SessionClosureReason.ILLNESS_OR_INJURY)
        assertThat(SessionCompletionRules.closureForRest(illnessOrInjury = false))
            .isEqualTo(SessionClosureReason.REST_PRESCRIBED)
    }

    @Test
    fun `non-trainable closures are not adherence failures`() {
        assertThat(SessionClosureReason.REST_PRESCRIBED.isNonTrainable).isTrue()
        assertThat(SessionClosureReason.ILLNESS_OR_INJURY.isNonTrainable).isTrue()
        assertThat(SessionClosureReason.PLAN_REPLACED.isNonTrainable).isTrue()
        assertThat(SessionClosureReason.USER_SKIPPED.isNonTrainable).isFalse()
        assertThat(SessionClosureReason.MISSED.isNonTrainable).isFalse()
    }

    @Test
    fun `allows repairing a same-day legacy completion after confirmation`() {
        val decision = SessionCompletionRules.checkLegacyRepair(
            session = legacySession(thursday),
            activityDate = thursday,
            today = thursday,
            confirmed = true,
        )

        assertThat(decision).isEqualTo(SessionCompletionDecision.Allowed())
    }

    @Test
    fun `legacy repair requires explicit confirmation`() {
        val decision = SessionCompletionRules.checkLegacyRepair(
            session = legacySession(thursday),
            activityDate = thursday,
            today = thursday,
            confirmed = false,
        ) as SessionCompletionDecision.Blocked

        assertThat(decision.message).contains("Confirme")
    }

    @Test
    fun `legacy repair never promotes migrated history from another day`() {
        val decision = SessionCompletionRules.checkLegacyRepair(
            session = legacySession(wednesday),
            activityDate = thursday,
            today = thursday,
            confirmed = true,
        ) as SessionCompletionDecision.Blocked

        assertThat(decision.message).contains("histórico migrado")
    }

    @Test
    fun `legacy repair rejects a future day`() {
        val decision = SessionCompletionRules.checkLegacyRepair(
            session = legacySession(thursday.plusDays(1)),
            activityDate = thursday.plusDays(1),
            today = thursday,
            confirmed = true,
        ) as SessionCompletionDecision.Blocked

        assertThat(decision.message).contains("ainda não aconteceu")
    }

    @Test
    fun `legacy repair rejects a run from another day`() {
        val decision = SessionCompletionRules.checkLegacyRepair(
            session = legacySession(thursday),
            activityDate = wednesday,
            today = thursday,
            confirmed = true,
        ) as SessionCompletionDecision.Blocked

        assertThat(decision.message).contains("outro dia")
    }

    @Test
    fun `legacy repair rejects a completion that still keeps a link`() {
        val linked = SessionCompletionRules.checkLegacyRepair(
            session = legacySession(thursday, activityId = "activity-1"),
            activityDate = thursday,
            today = thursday,
            confirmed = true,
        ) as SessionCompletionDecision.Blocked
        val withExecution = SessionCompletionRules.checkLegacyRepair(
            session = legacySession(thursday, executionId = "execution-1"),
            activityDate = thursday,
            today = thursday,
            confirmed = true,
        ) as SessionCompletionDecision.Blocked

        assertThat(linked.message).contains("vínculo")
        assertThat(withExecution.message).contains("vínculo")
    }

    @Test
    fun `legacy repair rejects evidence that is not legacy`() {
        val planned = SessionCompletionRules.checkLegacyRepair(
            session = legacySession(thursday).copy(
                status = SessionStatus.PLANNED,
                completionEvidence = null,
            ),
            activityDate = thursday,
            today = thursday,
            confirmed = true,
        ) as SessionCompletionDecision.Blocked
        val manual = SessionCompletionRules.checkLegacyRepair(
            session = legacySession(thursday).copy(completionEvidence = CompletionEvidence.MANUAL),
            activityDate = thursday,
            today = thursday,
            confirmed = true,
        ) as SessionCompletionDecision.Blocked

        assertThat(planned.message).contains("registro legado")
        assertThat(manual.message).contains("registro legado")
    }

    private fun legacySession(
        date: LocalDate,
        activityId: String? = null,
        executionId: String? = null,
    ): PlannedSession = PlannedSession(
        date = date,
        type = WorkoutType.LONG,
        title = "Longão",
        durationMin = 60,
        rationale = "Base.",
        ruleIds = listOf("R-PLAN-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = SessionStatus.DONE,
        completionEvidence = CompletionEvidence.LEGACY_UNVERIFIED,
        activityId = activityId,
        executionId = executionId,
    )
}
