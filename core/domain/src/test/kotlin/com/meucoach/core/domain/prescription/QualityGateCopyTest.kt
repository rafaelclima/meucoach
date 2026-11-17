package com.meucoach.core.domain.prescription

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * C4-51 (FR-412): every day-level block reason has a card headline, and the
 * headlines match the approved copy table.
 */
class QualityGateCopyTest {
    @Test
    fun `every block reason has a card headline`() {
        QualityBlockReason.entries.forEach { reason ->
            assertThat(reason.cardTitle()).describedAs(reason.name).isNotBlank()
        }
    }

    @Test
    fun `matches the FR-412 card copy`() {
        assertThat(QualityBlockReason.SUBJECTIVE_MISSING.cardTitle()).isEqualTo("Qualidade a confirmar")
        assertThat(QualityBlockReason.ACWR_HIGH.cardTitle()).isEqualTo("Hoje é fácil")
        assertThat(QualityBlockReason.LOAD_STALE.cardTitle()).isEqualTo("Carga em calibração")
        assertThat(QualityBlockReason.LOAD_INSUFFICIENT.cardTitle()).isEqualTo("Carga em calibração")
        assertThat(QualityBlockReason.BEGINNER_GUARD.cardTitle()).isEqualTo("Base primeiro")
        assertThat(QualityBlockReason.TAPER_WINDOW.cardTitle()).isEqualTo("Semana de prova")
        assertThat(QualityBlockReason.RACE_WEEK.cardTitle()).isEqualTo("Semana de prova")
    }
}
