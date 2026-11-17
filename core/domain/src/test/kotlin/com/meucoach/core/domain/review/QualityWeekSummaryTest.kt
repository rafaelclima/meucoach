package com.meucoach.core.domain.review

import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.QualityBlockReason
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.LocalDate

/**
 * C4-43 (ADR 0050, FR-401/FR-408): the review distinguishes a planned quality
 * opportunity from what the day decided — kept, downgraded (with the reason)
 * or without a day decision.
 */
class QualityWeekSummaryTest {
    private val monday = LocalDate.of(2026, 10, 19)
    private val wednesday = monday.plusDays(2)

    @Test
    fun `a kept quality counts as maintained`() {
        val summary = QualityWeekSummary.summarize(
            qualityDates = listOf(wednesday),
            latestByDate = mapOf(wednesday to prescription(adjustment = PrescriptionAdjustment.KEEP)),
        )

        assertThat(summary.planned).isEqualTo(1)
        assertThat(summary.kept).isEqualTo(1)
        assertThat(summary.downgraded).isZero()
        assertThat(summary.firstDowngradeDetail).isNull()
        assertThat(summary.hasDecision).isTrue()
    }

    @Test
    fun `a downgraded quality carries the day reason`() {
        val summary = QualityWeekSummary.summarize(
            qualityDates = listOf(wednesday),
            latestByDate = mapOf(wednesday to prescription(downgraded = true)),
        )

        assertThat(summary.planned).isEqualTo(1)
        assertThat(summary.kept).isZero()
        assertThat(summary.downgraded).isEqualTo(1)
        assertThat(summary.firstDowngradeDetail).isEqualTo("check-in de hoje ausente")
        assertThat(summary.hasDecision).isTrue()
    }

    @Test
    fun `a week without day decisions reports no decision`() {
        val summary = QualityWeekSummary.summarize(
            qualityDates = listOf(wednesday),
            latestByDate = emptyMap(),
        )

        assertThat(summary.planned).isEqualTo(1)
        assertThat(summary.kept).isZero()
        assertThat(summary.downgraded).isZero()
        assertThat(summary.hasDecision).isFalse()
    }

    @Test
    fun `a week without planned quality reports zero opportunities`() {
        val summary = QualityWeekSummary.summarize(
            qualityDates = emptyList(),
            latestByDate = mapOf(wednesday to prescription()),
        )

        assertThat(summary.planned).isZero()
        assertThat(summary.hasDecision).isFalse()
    }

    @Test
    fun `two opportunities keep their own day decisions in date order`() {
        val friday = monday.plusDays(4)
        val summary = QualityWeekSummary.summarize(
            qualityDates = listOf(friday, wednesday),
            latestByDate = mapOf(
                wednesday to prescription(downgraded = true),
                friday to prescription(adjustment = PrescriptionAdjustment.KEEP),
            ),
        )

        assertThat(summary.planned).isEqualTo(2)
        assertThat(summary.kept).isEqualTo(1)
        assertThat(summary.downgraded).isEqualTo(1)
        assertThat(summary.firstDowngradeDetail).isEqualTo("check-in de hoje ausente")
    }

    @Test
    fun `rejects counts that do not fit the planned opportunities`() {
        assertThatThrownBy {
            WeeklyQuality(planned = 1, kept = 1, downgraded = 1, firstDowngradeDetail = "motivo")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `rejects a downgrade detail without a downgrade`() {
        assertThatThrownBy {
            WeeklyQuality(planned = 1, kept = 1, downgraded = 0, firstDowngradeDetail = "motivo")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun prescription(
        adjustment: PrescriptionAdjustment = PrescriptionAdjustment.KEEP,
        downgraded: Boolean = false,
    ) = DailyPrescription(
        date = wednesday,
        plannedSessionId = "plan-$wednesday",
        plannedType = WorkoutType.QUALITY,
        plannedTitle = "Ritmo forte",
        plannedDurationMin = 40,
        prescribedType = if (downgraded) WorkoutType.EASY else WorkoutType.QUALITY,
        title = if (downgraded) "Fácil e conversável" else "Ritmo forte",
        prescribedDurationMin = 40,
        targets = PrescriptionTargets(zone = "Z2", rpe = 4, primary = TargetKind.RPE),
        adjustment = if (downgraded) PrescriptionAdjustment.DOWNGRADE else adjustment,
        rationale = "Decisão do dia.",
        ruleIds = listOf("R-PER-03", "R-PRESC-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = "daily-prescription-v6",
        inputsHash = "hash-1",
        qualityBlockReason = if (downgraded) QualityBlockReason.SUBJECTIVE_MISSING else null,
        decisionDetail = if (downgraded) "check-in de hoje ausente" else null,
        unlockHint = if (downgraded) "Faça o check-in de hoje." else null,
    )
}
