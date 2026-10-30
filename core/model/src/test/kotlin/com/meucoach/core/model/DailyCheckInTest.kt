package com.meucoach.core.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.LocalDate

class DailyCheckInTest {
    @Test
    fun `calculates the Hooper index`() {
        val checkIn = checkIn(sleepQuality = 4, fatigue = 2, soreness = 1, stress = 2)

        assertThat(checkIn.hooperSum).isEqualTo(7)
    }

    @Test
    fun `rejects scores outside one to five`() {
        assertThatThrownBy { checkIn(sleepQuality = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { checkIn(fatigue = 6) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { checkIn(soreness = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { checkIn(stress = 6) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun checkIn(sleepQuality: Int = 3, fatigue: Int = 3, soreness: Int = 3, stress: Int = 3) = DailyCheckIn(
        date = LocalDate.of(2026, 10, 30),
        sleepQuality = sleepQuality,
        fatigue = fatigue,
        soreness = soreness,
        stress = stress,
    )
}
