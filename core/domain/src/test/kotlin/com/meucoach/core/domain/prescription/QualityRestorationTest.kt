package com.meucoach.core.domain.prescription

import com.meucoach.core.model.DataConfidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * C4-31 (ADR 0044 §2.5): the UI shows "Atualizado: com o check-in, a qualidade
 * foi liberada" only when a planned quality was downgraded and later restored
 * before the session was done.
 */
class QualityRestorationTest {
    private val date = LocalDate.of(2026, 11, 3)

    @Test
    fun `empty history never counts as restored`() {
        assertThat(emptyList<DailyPrescription>().hasRestoredQuality()).isFalse()
    }

    @Test
    fun `a single kept quality is not a restoration`() {
        assertThat(listOf(keepQuality()).hasRestoredQuality()).isFalse()
    }

    @Test
    fun `a downgrade restored to quality before done counts`() {
        val versions = listOf(downgradedQuality(), keepQuality())

        assertThat(versions.hasRestoredQuality()).isTrue()
    }

    @Test
    fun `a quality that stays downgraded does not count`() {
        val versions = listOf(downgradedQuality(), downgradedQuality())

        assertThat(versions.hasRestoredQuality()).isFalse()
    }

    @Test
    fun `a restored quality that is downgraded again does not count`() {
        val versions = listOf(downgradedQuality(), keepQuality(), downgradedQuality())

        assertThat(versions.hasRestoredQuality()).isFalse()
    }

    @Test
    fun `a downgraded long run does not restore a quality`() {
        val versions = listOf(downgradedLong(), keepQuality())

        assertThat(versions.hasRestoredQuality()).isFalse()
    }

    @Test
    fun `two kept qualities without a downgrade do not count`() {
        val versions = listOf(keepQuality(), keepQuality())

        assertThat(versions.hasRestoredQuality()).isFalse()
    }

    private fun downgradedQuality() = prescription(
        plannedType = WorkoutType.QUALITY,
        prescribedType = WorkoutType.EASY,
        adjustment = PrescriptionAdjustment.DOWNGRADE,
    )

    private fun keepQuality() = prescription(
        plannedType = WorkoutType.QUALITY,
        prescribedType = WorkoutType.QUALITY,
        adjustment = PrescriptionAdjustment.KEEP,
    )

    private fun downgradedLong() = prescription(
        plannedType = WorkoutType.LONG,
        prescribedType = WorkoutType.EASY,
        adjustment = PrescriptionAdjustment.DOWNGRADE,
    )

    private fun prescription(
        plannedType: WorkoutType?,
        prescribedType: WorkoutType,
        adjustment: PrescriptionAdjustment,
    ) = DailyPrescription(
        date = date,
        plannedSessionId = "week-1-$plannedType",
        plannedType = plannedType,
        plannedTitle = "Planejado",
        plannedDurationMin = 40,
        prescribedType = prescribedType,
        title = "Prescrito",
        prescribedDurationMin = 30,
        targets = PrescriptionTargets(),
        adjustment = adjustment,
        rationale = "Rationale.",
        ruleIds = listOf("R-PRESC-02"),
        evidenceIds = listOf("EV-SAW-2016"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = PrescriptionPolicy.ENGINE_VERSION,
        inputsHash = "hash-$adjustment-$prescribedType-${System.nanoTime()}",
    )
}
