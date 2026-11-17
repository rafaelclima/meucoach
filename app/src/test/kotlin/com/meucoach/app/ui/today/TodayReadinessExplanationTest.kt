package com.meucoach.app.ui.today

import com.meucoach.core.domain.readiness.HrvMetrics
import com.meucoach.core.domain.readiness.ReadinessCalculator
import com.meucoach.core.domain.readiness.ReadinessInputs
import com.meucoach.core.domain.readiness.ReadinessSignals
import com.meucoach.core.domain.readiness.RestingHeartRateMetrics
import com.meucoach.core.domain.readiness.SleepMetrics
import com.meucoach.core.domain.readiness.SubjectiveCheckIn
import com.meucoach.core.model.DailyCheckIn
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * C4-32 (FR-406, ADR 0049 §2.1): the readiness "Por quê?" names the check-in
 * requirement. Without a check-in it cites R-READY-06 and prompts the
 * check-in; with one, neither appears.
 */
class TodayReadinessExplanationTest {
    private val today = LocalDate.of(2026, 11, 14)
    private val inputs = ReadinessInputs(
        sleep = SleepMetrics(450),
        hrv = HrvMetrics(
            rmssd7dMs = 60.0,
            baseline60dMs = 55.0,
            meanLog7d = 4.0943445622221,
            meanLog60d = 4.007333185232471,
            sdLnMs = 0.4,
            validNights = 7,
        ),
        restingHeartRate = RestingHeartRateMetrics(average7dBpm = 56.0, baseline30dBpm = 58.0),
        signals = ReadinessSignals(),
    )

    @Test
    fun `missing check-in cites R-READY-06 and prompts the check-in`() {
        val result = ReadinessCalculator.calculate(null, inputs.sleep, inputs.hrv, inputs.restingHeartRate)

        val explanation = buildReadinessExplanation(checkIn = null, inputs = inputs, result = result)

        assertThat(explanation.missingData).contains("Check-in de hoje")
        assertThat(explanation.rules.map { it.id }).contains("R-READY-06")
        assertThat(explanation.rationale).contains("Faça o check-in de hoje para liberar o treino forte.")
    }

    @Test
    fun `present check-in omits R-READY-06 and the prompt`() {
        val result = ReadinessCalculator.calculate(
            SubjectiveCheckIn(4, 2, 2, 2),
            inputs.sleep,
            inputs.hrv,
            inputs.restingHeartRate,
        )

        val explanation = buildReadinessExplanation(
            checkIn = DailyCheckIn(today, 4, 2, 2, 2),
            inputs = inputs,
            result = result,
        )

        assertThat(explanation.missingData).doesNotContain("Check-in de hoje")
        assertThat(explanation.rules.map { it.id }).doesNotContain("R-READY-06")
        assertThat(explanation.rationale).doesNotContain("Faça o check-in de hoje para liberar o treino forte.")
    }
}
