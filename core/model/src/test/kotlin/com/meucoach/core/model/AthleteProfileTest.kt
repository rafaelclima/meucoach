package com.meucoach.core.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class AthleteProfileTest {
    @Test
    fun `accepts availability, goal and locale fields`() {
        val profile = profile(
            availability = WeeklyAvailability(
                mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 50, DayOfWeek.SATURDAY to 90),
            ),
            goal = TrainingGoal(
                type = GoalType.FIVE_K,
                targetDate = LocalDate.of(2027, 3, 14),
                targetDistanceM = 5_000.0,
                targetTimeSec = 1_800,
            ),
        )

        assertThat(profile.weeklyAvailability.totalMinutes).isEqualTo(180)
        assertThat(profile.weeklyAvailability.minutesOn(DayOfWeek.MONDAY)).isEqualTo(40)
        assertThat(profile.weeklyAvailability.minutesOn(DayOfWeek.TUESDAY)).isZero()
        assertThat(profile.goal?.type).isEqualTo(GoalType.FIVE_K)
        assertThat(profile.units).isEqualTo(Units.METRIC)
        assertThat(profile.timezone).isEqualTo("America/Sao_Paulo")
    }

    @Test
    fun `defaults to UTC and general fitness without data`() {
        val profile = AthleteProfile(
            id = "athlete-1",
            birthDate = LocalDate.of(1988, 1, 1),
            sex = Sex.MALE,
        )

        assertThat(profile.timezone).isEqualTo("UTC")
        assertThat(profile.units).isEqualTo(Units.METRIC)
        assertThat(profile.weeklyAvailability.totalMinutes).isZero()
        assertThat(profile.goal).isNull()
    }

    @Test
    fun `rejects invalid availability minutes`() {
        assertThatThrownBy { WeeklyAvailability(mapOf(DayOfWeek.MONDAY to 0)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { WeeklyAvailability(mapOf(DayOfWeek.MONDAY to 1_441)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `rejects invalid goal values`() {
        assertThatThrownBy { TrainingGoal(targetDistanceM = 0.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { TrainingGoal(targetDistanceM = Double.NaN) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { TrainingGoal(targetTimeSec = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `rejects invalid locale and location`() {
        assertThatThrownBy { profile(timezone = "Natal/RN") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { profile(city = " ") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { profile(latitude = 91.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { profile(longitude = -181.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun profile(
        timezone: String = "America/Sao_Paulo",
        availability: WeeklyAvailability = WeeklyAvailability(),
        goal: TrainingGoal? = null,
        city: String? = "Natal",
        latitude: Double? = -5.79,
        longitude: Double? = -35.21,
    ) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        heightCm = 174.0,
        weightKg = 97.0,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        experience = Experience.BEGINNER,
        timezone = timezone,
        weeklyAvailability = availability,
        goal = goal,
        city = city,
        latitude = latitude,
        longitude = longitude,
    )
}
