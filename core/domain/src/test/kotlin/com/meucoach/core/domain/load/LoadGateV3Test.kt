package com.meucoach.core.domain.load

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Load gate v3 (ADR 0046, R-LOAD-03 v3).
 *
 * - Quality requires objective load KNOWN + monotony < 2.0 + no reliable
 *   strain spike + no reliable ACWR > 1.5.
 * - Progression requires quality plus a reliable ACWR inside 0.8–1.3.
 * - An unreliable ratio never vetoes quality or progression (it keeps both on
 *   the conservative side).
 */
class LoadGateV3Test {

    private fun snapshot(
        acwr: Double?,
        monotony: Double? = 1.2,
        strain: Double? = 400.0,
        chronicAverage28d: Double = 50.0,
        objectiveKnown: Boolean = true,
        reliable: Boolean = true,
    ): LoadSnapshot {
        val daysWithLoad = 12
        val subjective = if (objectiveKnown) 0 else 12
        val daysWithoutData = if (reliable && objectiveKnown) 0 else 2
        val daysWithoutActivity = 28 - daysWithLoad - daysWithoutData
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
                daysWithoutActivity = daysWithoutActivity,
                daysWithoutData = daysWithoutData,
                lastActivityAt = null,
                sourceFresh = true,
            ),
            subjectiveDaysInWindow = subjective,
            strain = strain,
        )
    }

    @Test
    fun `a reliable acwr above one point five blocks quality and progression`() {
        val s = snapshot(acwr = 1.6)
        assertThat(s.acwrReliable).isTrue()
        assertThat(s.acwrHigh).isTrue()
        assertThat(s.permitsQuality).isFalse()
        assertThat(s.permitsProgression).isFalse()
    }

    @Test
    fun `a reliable acwr one point four keeps quality and blocks progression`() {
        val s = snapshot(acwr = 1.4)
        assertThat(s.acwrHigh).isFalse()
        assertThat(s.permitsQuality).isTrue()
        assertThat(s.permitsProgression).isFalse()
    }

    @Test
    fun `a reliable acwr zero point seven keeps quality and never accelerates`() {
        val s = snapshot(acwr = 0.7)
        assertThat(s.permitsQuality).isTrue()
        assertThat(s.permitsProgression).isFalse()
    }

    @Test
    fun `an unreliable acwr one point six does not veto quality`() {
        val s = snapshot(acwr = 1.6, reliable = false)
        assertThat(s.acwrReliable).isFalse()
        assertThat(s.acwrHigh).isFalse()
        assertThat(s.permitsQuality).isTrue()
        assertThat(s.permitsProgression).isFalse()
    }

    @Test
    fun `monotony high blocks even with normal acwr`() {
        val s = snapshot(acwr = 1.0, monotony = 2.5, strain = 900.0)
        assertThat(s.permitsQuality).isFalse()
        assertThat(s.permitsProgression).isFalse()
    }

    @Test
    fun `null monotony blocks`() {
        val s = snapshot(acwr = 1.0, monotony = null, strain = null)
        assertThat(s.permitsQuality).isFalse()
    }

    @Test
    fun `a reliable strain spike blocks even with normal monotony`() {
        // baseline semanal = 50 * 7 = 350; spike > 2x = > 700
        val s = snapshot(acwr = 1.0, monotony = 1.5, strain = 900.0, chronicAverage28d = 50.0)
        assertThat(s.strainSpike).isTrue()
        assertThat(s.strainReliable).isTrue()
        assertThat(s.permitsQuality).isFalse()
    }

    @Test
    fun `an unreliable strain spike is informative and does not block`() {
        val s = snapshot(acwr = 1.0, monotony = 1.5, strain = 900.0, reliable = false)
        assertThat(s.strainSpike).isTrue()
        assertThat(s.strainReliable).isFalse()
        assertThat(s.permitsQuality).isTrue()
    }

    @Test
    fun `subjective-only load never permits`() {
        val s = snapshot(acwr = 1.0, objectiveKnown = false)
        assertThat(s.permitsQuality).isFalse()
        assertThat(s.permitsProgression).isFalse()
    }

    @Test
    fun `strain spike needs a positive baseline`() {
        val s = snapshot(acwr = 1.0, monotony = 1.2, strain = 900.0, chronicAverage28d = 0.0)
        assertThat(s.strainSpike).isFalse()
        assertThat(s.monotonyOk).isTrue()
        assertThat(s.permitsQuality).isTrue()
    }
}
