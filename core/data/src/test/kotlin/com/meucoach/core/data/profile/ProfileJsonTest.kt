package com.meucoach.core.data.profile

import com.meucoach.core.model.GoalType
import com.meucoach.core.model.TrainingGoal
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileJsonTest {
    @Test
    fun `round-trips weekly availability`() {
        val availability = WeeklyAvailability(
            mapOf(
                DayOfWeek.SATURDAY to 90,
                DayOfWeek.MONDAY to 40,
                DayOfWeek.WEDNESDAY to 50,
            ),
        )

        val encoded = ProfileJson.encodeAvailability(availability)
        val decoded = ProfileJson.decodeAvailability(encoded)

        assertThat(decoded).isEqualTo(availability)
        assertThat(encoded).contains("\"MONDAY\":40")
    }

    @Test
    fun `ignores unknown days and malformed availability`() {
        assertThat(ProfileJson.decodeAvailability("{\"FUNDAY\":30,\"MONDAY\":40}"))
            .isEqualTo(WeeklyAvailability(mapOf(DayOfWeek.MONDAY to 40)))
        assertThat(ProfileJson.decodeAvailability("not-json")).isEqualTo(WeeklyAvailability())
        assertThat(ProfileJson.decodeAvailability("{\"MONDAY\":0}")).isEqualTo(WeeklyAvailability())
    }

    @Test
    fun `round-trips a training goal`() {
        val goal = TrainingGoal(
            type = GoalType.HALF_MARATHON,
            targetDate = LocalDate.of(2027, 8, 15),
            targetDistanceM = 21_097.5,
            targetTimeSec = 7_200,
        )

        assertThat(ProfileJson.decodeGoal(ProfileJson.encodeGoal(goal))).isEqualTo(goal)
    }

    @Test
    fun `handles absent and malformed goals`() {
        assertThat(ProfileJson.encodeGoal(null)).isNull()
        assertThat(ProfileJson.decodeGoal(null)).isNull()
        assertThat(ProfileJson.decodeGoal("{\"type\":\"ULTRA\"}")).isNull()
        assertThat(ProfileJson.decodeGoal("not-json")).isNull()
    }

    @Test
    fun `round-trips a general fitness goal without targets`() {
        val goal = TrainingGoal()

        assertThat(ProfileJson.decodeGoal(ProfileJson.encodeGoal(goal))).isEqualTo(goal)
    }
}
