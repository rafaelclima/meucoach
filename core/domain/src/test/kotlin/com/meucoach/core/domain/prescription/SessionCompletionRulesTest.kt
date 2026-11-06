package com.meucoach.core.domain.prescription

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
}
