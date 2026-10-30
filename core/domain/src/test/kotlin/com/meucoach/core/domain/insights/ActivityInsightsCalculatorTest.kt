package com.meucoach.core.domain.insights

import com.meucoach.core.model.ActivityLap
import com.meucoach.core.model.DataConfidence
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ActivityInsightsCalculatorTest {
    private val calculator = ActivityInsightsCalculator()

    @Test
    fun `detects a negative split`() {
        val insight = calculator.calculate(
            listOf(
                lap(index = 0, durationSec = 360, distanceM = 1_000.0),
                lap(index = 1, durationSec = 330, distanceM = 1_000.0),
            ),
        ).single()

        assertThat(insight.ruleId).isEqualTo(ActivityInsightRules.PACING)
        assertThat(insight.evidenceIds).containsExactly("EV-ABBISS-2008", "EV-DANIELS-2014")
        assertThat(insight.confidence).isEqualTo(DataConfidence.MEDIUM)
        assertThat(insight.tone).isEqualTo(ActivityInsightTone.POSITIVE)
        assertThat(insight.message).contains("segunda metade foi 8,3% mais rápida")
    }

    @Test
    fun `recognizes an even pace`() {
        val insight = calculator.calculate(
            listOf(
                lap(index = 0, durationSec = 360, distanceM = 1_000.0),
                lap(index = 1, durationSec = 366, distanceM = 1_000.0),
            ),
        ).single()

        assertThat(insight.tone).isEqualTo(ActivityInsightTone.POSITIVE)
        assertThat(insight.message).contains("Ritmo constante")
    }

    @Test
    fun `flags a fading second half`() {
        val insight = calculator.calculate(
            listOf(
                lap(index = 0, durationSec = 360, distanceM = 1_000.0),
                lap(index = 1, durationSec = 400, distanceM = 1_000.0),
            ),
        ).single()

        assertThat(insight.tone).isEqualTo(ActivityInsightTone.WARNING)
        assertThat(insight.message).contains("O ritmo caiu 11,1%")
    }

    @Test
    fun `adds a heart rate drift warning when pace is stable`() {
        val insights = calculator.calculate(
            listOf(
                lap(index = 0, durationSec = 360, distanceM = 1_000.0, heartRate = 150),
                lap(index = 1, durationSec = 360, distanceM = 1_000.0, heartRate = 160),
            ),
        )

        val drift = insights.single { it.ruleId == ActivityInsightRules.HEART_RATE_DRIFT }
        assertThat(drift.evidenceIds).containsExactly("EV-FRIEL-2018")
        assertThat(drift.confidence).isEqualTo(DataConfidence.LOW)
        assertThat(drift.tone).isEqualTo(ActivityInsightTone.WARNING)
        assertThat(drift.message).contains("FC subiu 6,7%")
    }

    @Test
    fun `does not report drift for a small heart rate change`() {
        val insights = calculator.calculate(
            listOf(
                lap(index = 0, durationSec = 360, distanceM = 1_000.0, heartRate = 150),
                lap(index = 1, durationSec = 360, distanceM = 1_000.0, heartRate = 152),
            ),
        )

        assertThat(insights.map { it.ruleId }).containsExactly(ActivityInsightRules.PACING)
    }

    @Test
    fun `skips drift when the pace also faded`() {
        val insights = calculator.calculate(
            listOf(
                lap(index = 0, durationSec = 360, distanceM = 1_000.0, heartRate = 150),
                lap(index = 1, durationSec = 400, distanceM = 1_000.0, heartRate = 170),
            ),
        )

        assertThat(insights.map { it.ruleId }).containsExactly(ActivityInsightRules.PACING)
    }

    @Test
    fun `returns no insight without at least two laps`() {
        assertThat(calculator.calculate(emptyList())).isEmpty()
        assertThat(
            calculator.calculate(listOf(lap(index = 0, durationSec = 360, distanceM = 1_000.0))),
        ).isEmpty()
    }

    @Test
    fun `returns no insight when a half has no distance`() {
        assertThat(
            calculator.calculate(
                listOf(
                    lap(index = 0, durationSec = 360, distanceM = 1_000.0),
                    lap(index = 1, durationSec = 60, distanceM = 0.0),
                ),
            ),
        ).isEmpty()
    }

    @Test
    fun `handles an odd number of laps`() {
        val insights = calculator.calculate(
            listOf(
                lap(index = 0, durationSec = 360, distanceM = 1_000.0),
                lap(index = 1, durationSec = 360, distanceM = 1_000.0),
                lap(index = 2, durationSec = 360, distanceM = 1_000.0),
            ),
        )

        assertThat(insights.single().ruleId).isEqualTo(ActivityInsightRules.PACING)
        assertThat(insights.single().tone).isEqualTo(ActivityInsightTone.POSITIVE)
    }

    private fun lap(index: Int, durationSec: Int, distanceM: Double, heartRate: Int? = null) = ActivityLap(
        index = index,
        durationSec = durationSec,
        distanceM = distanceM,
        averageHeartRate = heartRate,
    )
}
