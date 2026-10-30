package com.meucoach.app.ui.profile

import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.GoalType
import com.meucoach.core.model.Sex
import com.meucoach.core.model.TrainingGoal
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ProfileLabelsTest {
    private val today = LocalDate.of(2026, 10, 30)

    @Test
    fun `formats age from the birth date`() {
        assertThat(ageLabel(profile(), today)).isEqualTo("38 anos")
    }

    @Test
    fun `formats target date and time`() {
        assertThat(targetDateLabel(profile())).isEqualTo("14/03/2027")
        assertThat(targetTimeLabel(1_800)).isEqualTo("30:00")
        assertThat(targetTimeLabel(5_400)).isEqualTo("1:30:00")
        assertThat(targetTimeLabel(null)).isEqualTo("Sem tempo alvo")
    }

    @Test
    fun `formats weekly availability`() {
        assertThat(availabilityLabel(profile())).isEqualTo("seg, qua, sáb · 3 h/semana")
    }

    @Test
    fun `handles a profile without a goal or availability`() {
        val empty = profile().copy(
            goal = null,
            weeklyAvailability = WeeklyAvailability(),
        )

        assertThat(goalLabel(empty)).isEqualTo("Saúde geral")
        assertThat(targetDateLabel(empty)).isEqualTo("Sem data")
        assertThat(availabilityLabel(empty)).isEqualTo("Não informada")
    }

    @Test
    fun `maps enum labels`() {
        assertThat(sexLabel(Sex.FEMALE)).isEqualTo("Feminino")
        assertThat(sexLabel(Sex.UNSPECIFIED)).isEqualTo("Prefiro não dizer")
        assertThat(goalLabel(GoalType.MARATHON)).isEqualTo("Maratona")
        assertThat(availabilityDayLabel(DayOfWeek.MONDAY)).isEqualTo("Segunda-feira")
    }

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(
            mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 50, DayOfWeek.SATURDAY to 90),
        ),
        goal = TrainingGoal(
            type = GoalType.FIVE_K,
            targetDate = LocalDate.of(2027, 3, 14),
            targetDistanceM = 5_000.0,
            targetTimeSec = 1_800,
        ),
    )
}
