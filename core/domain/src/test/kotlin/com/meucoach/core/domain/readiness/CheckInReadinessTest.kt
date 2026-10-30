package com.meucoach.core.domain.readiness

import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ReadinessLevel
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class CheckInReadinessTest {
    @Test
    fun `maps check-in scores and illness`() {
        val checkIn = checkIn(fatigue = 4, illnessOrInjury = true)

        val subjective = checkIn.toSubjectiveCheckIn()

        assertThat(subjective.sleepQuality).isEqualTo(4)
        assertThat(subjective.fatigue).isEqualTo(4)
        assertThat(checkIn.toReadinessSignals().illnessOrInjury).isTrue()
    }

    @Test
    fun `calculates readiness from the check-in alone`() {
        val result = checkIn().toReadinessResult()

        assertThat(result.components.keys).containsExactly(ReadinessComponent.SUBJECTIVE)
        assertThat(result.missing).contains(
            ReadinessComponent.SLEEP,
            ReadinessComponent.HRV,
            ReadinessComponent.RESTING_HEART_RATE,
        )
        assertThat(result.confidence).isEqualTo(DataConfidence.LOW)
        assertThat(result.level).isEqualTo(ReadinessLevel.GREEN)
        assertThat(result.flags).doesNotContain(ReadinessFlag.NO_QUALITY)
    }

    @Test
    fun `illness forces a red day without quality`() {
        val result = checkIn(illnessOrInjury = true).toReadinessResult()

        assertThat(result.level).isEqualTo(ReadinessLevel.RED)
        assertThat(result.score).isLessThanOrEqualTo(29)
        assertThat(result.flags).contains(ReadinessFlag.ILLNESS_OR_INJURY, ReadinessFlag.NO_QUALITY)
    }

    private fun checkIn(fatigue: Int = 2, illnessOrInjury: Boolean = false) = DailyCheckIn(
        date = LocalDate.of(2026, 10, 30),
        sleepQuality = 4,
        fatigue = fatigue,
        soreness = 1,
        stress = 2,
        illnessOrInjury = illnessOrInjury,
    )
}
