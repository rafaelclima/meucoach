package com.meucoach.core.domain.replan

import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Experience
import com.meucoach.core.model.GoalType
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
import com.meucoach.core.model.TrainingGoal
import com.meucoach.core.model.Units
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class PlanningFingerprintTest {
    private val monday = LocalDate.of(2026, 11, 2)

    @Test
    fun `same planning inputs produce the same version and hash`() {
        val first = PlanningFingerprints.of(profile())
        val second = PlanningFingerprints.of(profile())

        assertThat(first.version).isEqualTo(PlanningFingerprints.VERSION)
        assertThat(first.hash).isEqualTo(second.hash)
        assertThat(first.snapshot).isEqualTo(second.snapshot)
    }

    @Test
    fun `experience pace lthr and treadmill change the fingerprint`() {
        val base = PlanningFingerprints.of(profile())

        assertThat(PlanningFingerprints.of(profile(experience = Experience.ADVANCED)).hash)
            .isNotEqualTo(base.hash)
        assertThat(PlanningFingerprints.of(profile(thresholdSecPerKm = 330)).hash)
            .isNotEqualTo(base.hash)
        assertThat(PlanningFingerprints.of(profile(lthr = 170)).hash)
            .isNotEqualTo(base.hash)
        assertThat(PlanningFingerprints.of(profile(treadmill = true)).hash)
            .isNotEqualTo(base.hash)
    }

    @Test
    fun `presentation and anthropometric fields stay out of the fingerprint`() {
        val base = PlanningFingerprints.of(profile())

        val changed = profile().copy(
            name = "Outro nome",
            avatarPath = "/tmp/avatar.png",
            weightKg = 92.0,
            heightCm = 190.0,
            sex = Sex.FEMALE,
            city = "Recife",
            latitude = -8.05,
            longitude = -34.9,
            units = Units.METRIC,
        )

        assertThat(PlanningFingerprints.of(changed).hash).isEqualTo(base.hash)
    }

    @Test
    fun `goal type distance and date change the fingerprint but target time does not`() {
        val base = PlanningFingerprints.of(profile(goal = goal()))
        val changedTime = PlanningFingerprints.of(profile(goal = goal(targetTimeSec = 2_400)))
        val changedDistance = PlanningFingerprints.of(profile(goal = goal(distanceM = 21_097.5)))
        val changedDate = PlanningFingerprints.of(profile(goal = goal(date = monday.plusDays(1))))
        val changedType = PlanningFingerprints.of(profile(goal = goal(type = GoalType.HALF_MARATHON)))

        assertThat(changedTime.hash).isEqualTo(base.hash)
        assertThat(changedDistance.hash).isNotEqualTo(base.hash)
        assertThat(changedDate.hash).isNotEqualTo(base.hash)
        assertThat(changedType.hash).isNotEqualTo(base.hash)
    }

    @Test
    fun `general fitness and custom goals have no planning projection`() {
        assertThat(PlanningGoal.from(null)).isNull()
        assertThat(PlanningGoal.from(TrainingGoal(type = GoalType.GENERAL_FITNESS))).isNull()
        assertThat(PlanningGoal.from(TrainingGoal(type = GoalType.CUSTOM))).isNull()
        assertThat(
            PlanningFingerprints.of(
                profile(goal = TrainingGoal(type = GoalType.GENERAL_FITNESS)),
            ).hash,
        ).isEqualTo(PlanningFingerprints.of(profile(goal = null)).hash)
    }

    @Test
    fun `null is distinct from a present value`() {
        val calibrated = PlanningFingerprints.of(profile())
        val uncalibrated = PlanningFingerprints.of(profile(thresholdSecPerKm = null, lthr = null))

        assertThat(uncalibrated.hash).isNotEqualTo(calibrated.hash)
        assertThat(uncalibrated.snapshot.thresholdPaceSecondsPerKm).isNull()
    }

    @Test
    fun `timezone changes only the temporal context hash`() {
        val saoPaulo = profile(timezone = "America/Sao_Paulo")
        val recife = saoPaulo.copy(timezone = "America/Recife")

        assertThat(PlanningFingerprints.of(recife).hash)
            .isEqualTo(PlanningFingerprints.of(saoPaulo).hash)
        assertThat(PlanningFingerprints.temporalContextHash(recife))
            .isNotEqualTo(PlanningFingerprints.temporalContextHash(saoPaulo))
        assertThat(
            PlanningFingerprints.temporalContextHash(ZoneId.of("America/Recife")),
        ).isEqualTo(PlanningFingerprints.temporalContextHash(recife))
    }

    @Test
    fun `snapshot contains only the planning allowlist`() {
        val snapshot = PlanningFingerprints.of(profile(goal = goal())).snapshot

        assertThat(snapshot.experience).isEqualTo(Experience.INTERMEDIATE)
        assertThat(snapshot.thresholdPaceSecondsPerKm).isEqualTo(360)
        assertThat(snapshot.lactateThresholdHeartRate).isEqualTo(180)
        assertThat(snapshot.treadmillAccess).isFalse()
        assertThat(snapshot.goal).isEqualTo(
            PlanningGoal(
                type = GoalType.TEN_K,
                targetDistanceM = 10_000.0,
                targetDate = monday.plusDays(20),
            ),
        )
    }

    private fun profile(
        experience: Experience = Experience.INTERMEDIATE,
        thresholdSecPerKm: Int? = 360,
        lthr: Int? = 180,
        treadmill: Boolean = false,
        timezone: String = "America/Sao_Paulo",
        goal: TrainingGoal? = null,
    ) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        heightCm = 178.0,
        weightKg = 74.0,
        thresholdPace = thresholdSecPerKm?.let(Pace::fromSecondsPerKm),
        lactateThresholdHeartRate = lthr,
        experience = experience,
        timezone = timezone,
        weeklyAvailability = WeeklyAvailability(mapOf(DayOfWeek.MONDAY to 40)),
        goal = goal,
        treadmillAccess = treadmill,
    )

    private fun goal(
        type: GoalType = GoalType.TEN_K,
        distanceM: Double = 10_000.0,
        date: LocalDate = monday.plusDays(20),
        targetTimeSec: Int? = null,
    ) = TrainingGoal(
        type = type,
        targetDate = date,
        targetDistanceM = distanceM,
        targetTimeSec = targetTimeSec,
    )
}
