package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Regression scenarios prepared for cycle 1 (C1-02/C1-03/C1-04).
 *
 * The executable tests lock invariants that must survive the prescription
 * pipeline. Each scenario is enabled with the corresponding C1 gate, so the
 * contract never depends on prose alone.
 */
class Cycle1RegressionTest {
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 2)
    private val thursday = monday.plusDays(3)

    @Test
    fun `plans only the configured availability days`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.SATURDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(plan.sessions.map { it.date.dayOfWeek })
            .containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY)
        assertThat(plan.sessions).allMatch { it.date in monday..monday.plusDays(6) }
    }

    @Test
    fun `never places quality on consecutive days or next to the long run`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.FRIDAY to 40,
                        DayOfWeek.SUNDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                previousWeek = previousWeek(completed = 4, total = 4),
            ),
        )

        val qualityDays = plan.sessions.filter { it.type == WorkoutType.QUALITY }.map { it.date.dayOfWeek }
        assertThat(plan.qualityCount).isBetween(1, 2)
        assertThat(qualityDays).doesNotContain(DayOfWeek.SUNDAY)
        assertThat(qualityDays.zipWithNext().none { (first, second) -> adjacent(first, second) }).isTrue()
        assertThat(
            plan.sessions.none {
                it.type == WorkoutType.QUALITY && adjacent(it.date.dayOfWeek, DayOfWeek.SUNDAY)
            },
        ).isTrue()
    }

    @Test
    fun `does not create quality when readiness is red`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.SATURDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.RED),
                load = load(acwr = 1.0),
            ),
        )

        assertThat(plan.qualityCount).isZero()
        assertThat(plan.sessions.none { it.type == WorkoutType.QUALITY }).isTrue()
    }

    @Test
    fun `completion ignores future sessions and rest days`() {
        val plan = WeeklyPlan(
            weekStart = monday,
            sessions = listOf(
                session(monday, SessionStatus.DONE),
                session(monday.plusDays(1), SessionStatus.PLANNED, WorkoutType.REST),
                session(thursday, SessionStatus.PLANNED),
            ),
            weeklyMinutes = 60,
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Plano.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
        )

        val completion = PlanCompletionCalculator().calculate(
            plans = listOf(plan),
            today = thursday.minusDays(1),
            weeks = 4,
        )

        assertThat(completion.weeks.last().planned).isEqualTo(1)
        assertThat(completion.weeks.last().done).isEqualTo(1)
    }

    @Test
    fun `quality is blocked when acute chronic load is unknown`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.FRIDAY to 40,
                        DayOfWeek.SATURDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = null,
            ),
        )

        assertThat(plan.qualityCount).isZero()
    }

    @Test
    fun `quality is blocked when load coverage is insufficient`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.FRIDAY to 40,
                        DayOfWeek.SATURDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = null, daysWithLoad = 2, missingDays = 26),
            ),
        )

        assertThat(plan.qualityCount).isZero()
    }

    @Test
    fun `plan created midweek does not include days before today`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.SATURDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                weekStart = thursday,
                today = thursday,
            ),
        )

        assertThat(plan.sessions.map { it.date }).allMatch { !it.isBefore(thursday) }
        assertThat(plan.sessions.map { it.date.dayOfWeek })
            .doesNotContain(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        assertThat(plan.createdOn).isEqualTo(thursday)
    }

    @Test
    fun `a done session cannot exist without evidence`() {
        assertThatThrownBy {
            PlannedSession(
                date = monday,
                type = WorkoutType.EASY,
                title = "Fácil",
                durationMin = 30,
                rationale = "Base.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.DONE,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("evidence")
    }

    @Test
    fun `legacy completion does not unlock progression`() {
        val plan = generator.generate(
            input(
                profile = profile(
                    experience = Experience.INTERMEDIATE,
                    availability = mapOf(
                        DayOfWeek.MONDAY to 40,
                        DayOfWeek.WEDNESDAY to 40,
                        DayOfWeek.FRIDAY to 40,
                        DayOfWeek.SUNDAY to 90,
                    ),
                ),
                readiness = readiness(ReadinessLevel.GREEN),
                load = load(acwr = 1.0),
                previousWeek = previousWeek(completed = 4, total = 4, strong = false),
            ),
        )

        assertThat(plan.qualityCount).isEqualTo(1)
        assertThat(plan.rationale).contains("Sem progressão")
    }

    private fun input(
        profile: AthleteProfile,
        readiness: ReadinessResult,
        load: LoadSnapshot? = null,
        weekStart: LocalDate = monday,
        today: LocalDate = weekStart,
        previousWeek: WeeklyPlan? = null,
        weekNumberInBlock: Int = 1,
    ) = WeeklyPlanInput(
        profile = profile,
        readiness = readiness,
        load = load,
        weekStart = weekStart,
        today = today,
        previousWeek = previousWeek,
        weekNumberInBlock = weekNumberInBlock,
    )

    private fun readiness(
        level: ReadinessLevel,
        flags: Set<ReadinessFlag> = emptySet(),
        confidence: DataConfidence = DataConfidence.HIGH,
    ) = ReadinessResult(
        score = when (level) {
            ReadinessLevel.GREEN -> 80
            ReadinessLevel.AMBER -> 55
            ReadinessLevel.RED -> 20
        },
        level = level,
        confidence = confidence,
        components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        missing = emptySet(),
        flags = flags,
    )

    private fun profile(experience: Experience, availability: Map<DayOfWeek, Int>) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(360),
        experience = experience,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(availability),
    )

    private fun load(
        acwr: Double?,
        daysWithLoad: Int = 12,
        missingDays: Int = 28 - daysWithLoad,
        sourceFresh: Boolean = true,
    ) = LoadSnapshot(
        daily = emptyList(),
        ctlSeries = emptyList(),
        atlSeries = emptyList(),
        ctl = 0.0,
        atl = 0.0,
        tsb = 0.0,
        acuteAverage7d = 0.0,
        chronicAverage28d = 0.0,
        acwr = acwr,
        monotony = null,
        missingDays = missingDays,
        coverage = coverage(daysWithLoad = daysWithLoad, sourceFresh = sourceFresh),
    )

    private fun coverage(daysWithLoad: Int, sourceFresh: Boolean = true): LoadCoverage {
        val daysInWindow = 28
        val daysWithoutActivity = if (sourceFresh) daysInWindow - daysWithLoad else 0
        val daysWithoutData = if (sourceFresh) 0 else daysInWindow - daysWithLoad
        return LoadCoverage(
            daysInWindow = daysInWindow,
            daysWithLoad = daysWithLoad,
            daysWithoutActivity = daysWithoutActivity,
            daysWithoutData = daysWithoutData,
            lastActivityAt = if (daysWithLoad > 0) java.time.Instant.parse("2026-10-30T10:00:00Z") else null,
            sourceFresh = sourceFresh,
        )
    }

    private fun previousWeek(completed: Int, total: Int, strong: Boolean = true): WeeklyPlan {
        val previousMonday = monday.minusWeeks(1)
        val sessions = (0 until total).map { index ->
            session(
                date = previousMonday.plusDays(index.toLong()),
                status = if (index < completed) SessionStatus.DONE else SessionStatus.PLANNED,
                activityId = if (index < completed && strong) "previous-$index" else null,
            )
        }
        return WeeklyPlan(
            weekStart = previousMonday,
            sessions = sessions,
            weeklyMinutes = sessions.sumOf { it.durationMin ?: 0 },
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Semana anterior.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
        )
    }

    private fun session(
        date: LocalDate,
        status: SessionStatus,
        type: WorkoutType = WorkoutType.EASY,
        activityId: String? = null,
    ) = PlannedSession(
        date = date,
        type = type,
        title = if (type == WorkoutType.REST) "Descanso" else "Fácil",
        durationMin = if (type == WorkoutType.REST) null else 30,
        rationale = "Base.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = status,
        completionEvidence = when {
            status != SessionStatus.DONE -> null
            activityId != null -> CompletionEvidence.ACTIVITY
            else -> CompletionEvidence.LEGACY_UNVERIFIED
        },
        activityId = activityId,
        closureReason = if (status == SessionStatus.CLOSED) SessionClosureReason.USER_SKIPPED else null,
        closedAt = if (status == SessionStatus.CLOSED) java.time.Instant.EPOCH else null,
    )

    private fun adjacent(first: DayOfWeek, second: DayOfWeek): Boolean {
        val difference = kotlin.math.abs(first.value - second.value)
        return difference == 1 || difference == 6
    }
}
