package com.meucoach.app.story

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class StoryEtaEstimatorTest {
    @Test
    fun `first sample has no estimate`() {
        val estimator = StoryEtaEstimator()

        assertThat(estimator.remainingSec(0.25f, atMs = 0)).isNull()
    }

    @Test
    fun `estimates the remaining time from the smoothed completion rate`() {
        val estimator = StoryEtaEstimator()

        estimator.remainingSec(0.25f, atMs = 0)
        // 0.25 progress in 0.5 s → rate 0.5/s → remaining (1 - 0.5)/0.5 = 1 s.
        assertThat(estimator.remainingSec(0.5f, atMs = 500)).isEqualTo(1)
    }

    @Test
    fun `eta never increases on the way to the end`() {
        val estimator = StoryEtaEstimator()

        estimator.remainingSec(0.5f, atMs = 0)
        val first = requireNotNull(estimator.remainingSec(0.9f, atMs = 4_000))
        val last = requireNotNull(estimator.remainingSec(0.95f, atMs = 4_500))

        assertThat(first).isGreaterThanOrEqualTo(last)
    }

    @Test
    fun `a stalled sample keeps the last rate instead of crashing`() {
        val estimator = StoryEtaEstimator()

        estimator.remainingSec(0.25f, atMs = 0)
        val before = estimator.remainingSec(0.5f, atMs = 500)
        val stalled = estimator.remainingSec(0.5f, atMs = 2_000)

        assertThat(stalled).isEqualTo(before)
    }

    @Test
    fun `an implausible progress burst is clamped`() {
        val estimator = StoryEtaEstimator()

        estimator.remainingSec(0.25f, atMs = 0)
        val eta = estimator.remainingSec(0.99f, atMs = 1)

        // Without the clamp a 1 ms sample would project an instant finish.
        assertThat(eta).isBetween(1, 600)
    }

    @Test
    fun `a frozen video never projects an estimate`() {
        val estimator = StoryEtaEstimator()

        assertThat(estimator.remainingSec(0.0f, atMs = 0)).isNull()
        assertThat(estimator.remainingSec(0.0f, atMs = 5_000)).isNull()
    }

    @Test
    fun `the last sample always ends at zero`() {
        val estimator = StoryEtaEstimator()

        estimator.remainingSec(0.5f, atMs = 0)

        assertThat(estimator.remainingSec(1f, atMs = 1_000)).isEqualTo(0)
    }

    @Test
    fun `eta is capped at the configured maximum`() {
        val estimator = StoryEtaEstimator(maxEtaSec = 60)

        assertThat(estimator.remainingSec(0.2f, atMs = 0)).isNull()
        // Rate 0.002/s would project ~400 s; capped to 60.
        assertThat(estimator.remainingSec(0.201f, atMs = 500)).isEqualTo(60)
    }
}
