package com.meucoach.core.domain.load

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Fatia 1 — gate de carga v2 (TDD RED).
 *
 * Contrato proposto (SPORTS_SCIENCE v1.1, R-LOAD-03 v2):
 * - ACWR fora de faixa sozinho não veta qualidade/progressão.
 * - Permissão exige carga objetiva KNOWN + monotonia < 2.0 + sem spike de strain.
 * - Monotonia nula bloqueia (conservador).
 */
class LoadGateV2Test {

    private fun snapshot(
        acwr: Double?,
        monotony: Double?,
        strain: Double?,
        chronicAverage28d: Double = 50.0,
        objectiveKnown: Boolean = true,
    ): LoadSnapshot {
        val daysWithLoad = if (objectiveKnown) 12 else 0
        val subjective = if (objectiveKnown) 0 else 12
        return LoadSnapshot(
            daily = emptyList(),
            ctlSeries = emptyList(),
            atlSeries = emptyList(),
            ctl = 0.0,
            atl = 0.0,
            tsb = 0.0,
            acuteAverage7d = 50.0,
            chronicAverage28d = chronicAverage28d,
            acwr = acwr,
            monotony = monotony,
            missingDays = 0,
            coverage = LoadCoverage(
                daysInWindow = 28,
                daysWithLoad = daysWithLoad,
                daysWithoutActivity = 0,
                daysWithoutData = 28 - daysWithLoad,
                lastActivityAt = null,
                sourceFresh = true,
            ),
            subjectiveDaysInWindow = subjective,
            strain = strain,
        )
    }

    @Test
    fun `acwr alto isolado nao veta quando monotonia e strain estao ok`() {
        val s = snapshot(acwr = 1.6, monotony = 1.2, strain = 400.0, chronicAverage28d = 50.0)
        assertThat(s.permitsQualityAndProgression).isTrue()
        assertThat(s.strainSpike).isFalse()
    }

    @Test
    fun `monotonia alta bloqueia mesmo com acwr normal`() {
        val s = snapshot(acwr = 1.0, monotony = 2.5, strain = 900.0)
        assertThat(s.permitsQualityAndProgression).isFalse()
    }

    @Test
    fun `monotonia nula bloqueia`() {
        val s = snapshot(acwr = 1.0, monotony = null, strain = null)
        assertThat(s.permitsQualityAndProgression).isFalse()
    }

    @Test
    fun `strain em spike bloqueia mesmo com monotonia ok`() {
        // baseline semanal = 50 * 7 = 350; spike > 2x = > 700
        val s = snapshot(acwr = 1.0, monotony = 1.5, strain = 900.0, chronicAverage28d = 50.0)
        assertThat(s.strainSpike).isTrue()
        assertThat(s.permitsQualityAndProgression).isFalse()
    }

    @Test
    fun `carga so-subjetiva nunca permite`() {
        val s = snapshot(acwr = 1.0, monotony = 1.2, strain = 400.0, objectiveKnown = false)
        assertThat(s.permitsQualityAndProgression).isFalse()
    }

    @Test
    fun `strain spike exige baseline positiva`() {
        val s = snapshot(acwr = 1.0, monotony = 1.2, strain = 900.0, chronicAverage28d = 0.0)
        assertThat(s.strainSpike).isFalse()
        assertThat(s.monotonyOk).isTrue()
    }
}
