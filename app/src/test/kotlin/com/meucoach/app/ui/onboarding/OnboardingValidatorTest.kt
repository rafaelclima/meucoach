package com.meucoach.app.ui.onboarding

import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Experience
import com.meucoach.core.model.GoalType
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
import com.meucoach.core.model.TrainingGoal
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class OnboardingValidatorTest {
    private val today = LocalDate.of(2026, 10, 30)

    @Test
    fun `builds a complete profile from a valid form`() {
        val profile = OnboardingValidator.toProfile(form(), id = "athlete-1", today = today)

        assertThat(profile.id).isEqualTo("athlete-1")
        assertThat(profile.birthDate).isEqualTo(LocalDate.of(1988, 1, 1))
        assertThat(profile.heightCm).isEqualTo(174.0)
        assertThat(profile.weightKg).isEqualTo(97.0)
        assertThat(profile.restingHeartRate).isEqualTo(58)
        assertThat(profile.maxHeartRateObserved).isEqualTo(187)
        assertThat(profile.weeklyAvailability.totalMinutes).isEqualTo(180)
        assertThat(profile.goal).isEqualTo(
            TrainingGoal(
                type = GoalType.FIVE_K,
                targetDate = LocalDate.of(2027, 3, 14),
                targetDistanceM = 5_000.0,
                targetTimeSec = 1_800,
            ),
        )
    }

    @Test
    fun `accepts a manual lactate threshold heart rate`() {
        val profile = OnboardingValidator.toProfile(
            form().copy(lactateThresholdHeartRate = "178"),
            id = "athlete-1",
            today = today,
        )

        assertThat(profile.lactateThresholdHeartRate).isEqualTo(178)
        assertThat(OnboardingValidator.fromProfile(profile).lactateThresholdHeartRate).isEqualTo("178")
    }

    @Test
    fun `rejects a lactate threshold outside the heart rate range or inconsistent with the profile`() {
        assertInvalid(
            2,
            form().copy(lactateThresholdHeartRate = "90"),
            OnboardingField.LACTATE_THRESHOLD_HEART_RATE,
        )
        assertInvalid(
            2,
            form().copy(lactateThresholdHeartRate = "231"),
            OnboardingField.LACTATE_THRESHOLD_HEART_RATE,
        )
        assertInvalid(
            2,
            form().copy(restingHeartRate = "100", lactateThresholdHeartRate = "100"),
            OnboardingField.LACTATE_THRESHOLD_HEART_RATE,
        )
        assertInvalid(
            2,
            form().copy(maxHeartRateObserved = "170", lactateThresholdHeartRate = "180"),
            OnboardingField.LACTATE_THRESHOLD_HEART_RATE,
        )
    }

    @Test
    fun `round-trips editing without losing calibration or location`() {
        val existing = AthleteProfile(
            id = "athlete-1",
            birthDate = LocalDate.of(1988, 1, 1),
            sex = Sex.MALE,
            heightCm = 174.0,
            weightKg = 97.0,
            restingHeartRate = 58,
            maxHeartRateObserved = 187,
            lactateThresholdHeartRate = 180,
            thresholdPace = Pace.fromSecondsPerKm(364),
            vdot = 31.0,
            experience = Experience.BEGINNER,
            timezone = "America/Sao_Paulo",
            weeklyAvailability = WeeklyAvailability(mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.SATURDAY to 90)),
            goal = TrainingGoal(
                type = GoalType.HALF_MARATHON,
                targetDate = LocalDate.of(2027, 8, 15),
                targetDistanceM = 21_097.5,
                targetTimeSec = 7_200,
            ),
            city = "Natal",
            latitude = -5.79,
            longitude = -35.21,
        )

        val rebuilt = OnboardingValidator.toProfile(
            form = OnboardingValidator.fromProfile(existing),
            id = existing.id,
            existing = existing,
            today = today,
        )

        assertThat(rebuilt).isEqualTo(existing)
    }

    @Test
    fun `round-trips treadmill access through the form`() {
        val profile = OnboardingValidator.toProfile(
            form = form().copy(treadmillAccess = true),
            id = "athlete-1",
            today = today,
        )

        assertThat(profile.treadmillAccess).isTrue()
        assertThat(OnboardingValidator.fromProfile(profile).treadmillAccess).isTrue()
    }

    @Test
    fun `rejects invalid personal data`() {
        assertInvalid(0, form().copy(birthDate = "30/10/2026"), OnboardingField.BIRTH_DATE)
        assertInvalid(0, form().copy(birthDate = "31/02/1990"), OnboardingField.BIRTH_DATE)
        assertInvalid(0, form().copy(birthDate = "01/01/1900"), OnboardingField.BIRTH_DATE)
        assertInvalid(0, form().copy(heightCm = "70"), OnboardingField.HEIGHT)
        assertInvalid(0, form().copy(heightCm = "alto"), OnboardingField.HEIGHT)
        assertInvalid(0, form().copy(weightKg = "500"), OnboardingField.WEIGHT)
        assertInvalid(0, form().copy(weightKg = "pesado"), OnboardingField.WEIGHT)
    }

    @Test
    fun `requires a future race date and valid target time`() {
        assertInvalid(1, form().copy(targetDate = "01/01/2020"), OnboardingField.TARGET_DATE)
        assertInvalid(1, form().copy(targetDate = "31/02/2027"), OnboardingField.TARGET_DATE)
        assertInvalid(1, form().copy(targetTime = "trinta"), OnboardingField.TARGET_TIME)
        assertInvalid(1, form().copy(targetTime = "10:99"), OnboardingField.TARGET_TIME)
        assertInvalid(1, form().copy(targetTime = "1:75:00"), OnboardingField.TARGET_TIME)
        assertInvalid(
            1,
            form().copy(availabilityMinutes = mapOf(DayOfWeek.MONDAY to "0")),
            OnboardingField.AVAILABILITY,
        )
        assertInvalid(
            1,
            form().copy(availabilityMinutes = mapOf(DayOfWeek.MONDAY to "2000")),
            OnboardingField.AVAILABILITY,
        )
    }

    @Test
    fun `accepts general fitness without a target date`() {
        val validation = OnboardingValidator.validateStep(
            1,
            form().copy(goalType = GoalType.GENERAL_FITNESS, targetDate = "", targetTime = ""),
            today,
        )

        assertThat(validation.isValid).isTrue()
    }

    @Test
    fun `parses short and long target times`() {
        assertThat(
            OnboardingValidator.toProfile(form().copy(targetTime = "29:30"), "a", today = today)
                .goal?.targetTimeSec,
        ).isEqualTo(1_770)
        assertThat(
            OnboardingValidator.toProfile(form().copy(targetTime = "1:30:00"), "a", today = today)
                .goal?.targetTimeSec,
        ).isEqualTo(5_400)
    }

    @Test
    fun `rejects invalid heart rates and routine fields`() {
        assertInvalid(2, form().copy(restingHeartRate = "20"), OnboardingField.RESTING_HEART_RATE)
        assertInvalid(2, form().copy(maxHeartRateObserved = "90"), OnboardingField.MAX_HEART_RATE)
        assertInvalid(2, form().copy(maxHeartRateObserved = "58"), OnboardingField.MAX_HEART_RATE)
        assertInvalid(2, form().copy(timezone = "Natal/RN"), OnboardingField.TIMEZONE)
        assertInvalid(2, form().copy(city = "A"), OnboardingField.CITY)
    }

    @Test
    fun `accepts a race goal without a target date`() {
        // Without a date there is no taper, but the plan still works (conservative default).
        val profile = OnboardingValidator.toProfile(
            form().copy(targetDate = ""),
            id = "athlete-1",
            today = today,
        )

        assertThat(profile.goal?.type).isEqualTo(GoalType.FIVE_K)
        assertThat(profile.goal?.targetDate).isNull()
    }

    @Test
    fun `aggregates errors from every step`() {
        val validation = OnboardingValidator.validateAll(
            form().copy(
                birthDate = "data",
                targetDate = "32/13/2026",
                restingHeartRate = "10",
            ),
            today,
        )

        assertThat(validation.errors.keys).contains(
            OnboardingField.BIRTH_DATE,
            OnboardingField.TARGET_DATE,
            OnboardingField.RESTING_HEART_RATE,
        )
    }

    @Test
    fun `refuses to build a profile from an invalid form`() {
        assertThatThrownBy {
            OnboardingValidator.toProfile(form().copy(birthDate = ""), "a", today = today)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun assertInvalid(step: Int, form: OnboardingForm, field: OnboardingField) {
        assertThat(OnboardingValidator.validateStep(step, form, today).errors)
            .containsKey(field)
    }

    private fun form() = OnboardingForm(
        birthDate = "01/01/1988",
        sex = Sex.MALE,
        heightCm = "174",
        weightKg = "97",
        experience = Experience.BEGINNER,
        goalType = GoalType.FIVE_K,
        targetDate = "14/03/2027",
        targetTime = "30:00",
        availabilityMinutes = mapOf(
            DayOfWeek.MONDAY to "40",
            DayOfWeek.WEDNESDAY to "50",
            DayOfWeek.SATURDAY to "90",
        ),
        restingHeartRate = "58",
        maxHeartRateObserved = "187",
        timezone = "America/Sao_Paulo",
        city = "Natal",
    )
}
