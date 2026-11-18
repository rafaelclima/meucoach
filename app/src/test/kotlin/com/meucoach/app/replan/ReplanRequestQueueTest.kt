package com.meucoach.app.replan

import com.meucoach.core.domain.replan.ReplanIntent
import com.meucoach.core.domain.replan.TemporaryAvailability
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * Regression of the 3.3-A `queuedExplicit` flag: an explicit request that lands
 * while an automatic evaluation is in flight must keep its exact intent.
 */
class ReplanRequestQueueTest {
    private val friday = LocalDate.of(2026, 11, 6)
    private val lessTime = ReplanIntent(temporaryAvailability = listOf(TemporaryAvailability(friday, 20)))
    private val missed = ReplanIntent(requestedMissedDate = friday.minusDays(2))

    @Test
    fun `an explicit intent queued during an evaluation survives with its intent`() {
        val queue = ReplanRequestQueue()

        assertThat(queue.begin()).isTrue()
        queue.enqueue(lessTime)
        assertThat(queue.begin()).isFalse()

        assertThat(queue.finish()).isEqualTo(lessTime)
        assertThat(queue.begin()).isTrue()
    }

    @Test
    fun `the last explicit intent wins`() {
        val queue = ReplanRequestQueue()
        queue.begin()

        queue.enqueue(lessTime)
        queue.enqueue(missed)

        assertThat(queue.finish()).isEqualTo(missed)
    }

    @Test
    fun `an evaluation without a queued request finishes empty`() {
        val queue = ReplanRequestQueue()

        assertThat(queue.begin()).isTrue()

        assertThat(queue.finish()).isNull()
        assertThat(queue.isEvaluating).isFalse()
    }
}
