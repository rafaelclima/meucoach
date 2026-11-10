package com.meucoach.core.domain.replan

import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.RacePlan
import com.meucoach.core.domain.prescription.SessionClosureReason
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.SessionSwapper
import com.meucoach.core.domain.prescription.SwapResult
import com.meucoach.core.domain.prescription.WeeklyPlan
import com.meucoach.core.domain.prescription.WeeklyPlanGenerator
import com.meucoach.core.domain.prescription.WeeklyPlanInput
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Pace
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * R-01 contract: pure, deterministic replanning of the current week's future.
 *
 * Decisions approved for this delivery (A1/A2/A5/A6): keep `createdOn`, touch
 * only strictly future sessions, count eligible past `PLANNED` sessions without
 * closing them, and use 2 as the product threshold for T1.
 */
class PlanReplannerTest {
    private val planner = PlanReplanner()
    private val generator = WeeklyPlanGenerator()
    private val monday = LocalDate.of(2026, 11, 2)
    private val tuesday = monday.plusDays(1)
    private val wednesday = monday.plusDays(2)
    private val thursday = monday.plusDays(3)
    private val friday = monday.plusDays(4)
    private val saturday = monday.plusDays(5)
    private val sunday = monday.plusDays(6)

    // --- T1: missed eligible sessions -------------------------------------

    @Test
    fun `suggests a revision when two eligible past sessions were missed`() {
        val current = planOf(
            session(monday, WorkoutType.QUALITY, 40),
            session(wednesday, WorkoutType.EASY, 40),
            session(saturday, WorkoutType.LONG, 34),
        )

        val result = planner.evaluate(input(current, today = thursday))

        val suggested = result as ReplanResult.Suggest
        assertThat(suggested.suggestion.trigger).isEqualTo(ReplanTrigger.MISSED_SESSIONS)
        assertThat(suggested.suggestion.missedTrainableCount).isEqualTo(2)
        assertThat(suggested.suggestion.expectedVersion).isEqualTo(1)
        assertThat(suggested.candidate.createdOn).isEqualTo(monday)
        assertThat(suggested.candidate.sessions.first { it.date == monday }).isEqualTo(current.sessions[0])
        assertThat(suggested.candidate.sessions.first { it.date == wednesday }).isEqualTo(current.sessions[1])
        assertThat(suggested.candidate.sessions.map { it.date })
            .containsExactly(monday, wednesday, saturday)
        assertThat(suggested.candidate.sessions.first { it.date == saturday }.durationMin).isEqualTo(30)
        assertThat(suggested.candidate.weeklyMinutes).isEqualTo(110)
        assertThat(suggested.candidate.ruleIds).contains("R-PLAN-03")
        assertThat(suggested.candidate.ruleIds).doesNotContain("R-PLAN-09")
        assertThat(suggested.suggestion.ruleIds).isNotEmpty
        assertThat(suggested.suggestion.rationale).contains("sem repor")
    }

    @Test
    fun `one missed session stays below the product threshold`() {
        val current = planOf(
            session(monday, WorkoutType.QUALITY, 40),
            session(
                wednesday,
                WorkoutType.EASY,
                40,
                status = SessionStatus.DONE,
                completionEvidence = CompletionEvidence.ACTIVITY,
                activityId = "activity-wed",
            ),
            session(saturday, WorkoutType.LONG, 34),
        )

        val result = planner.evaluate(
            input(
                current = current,
                today = thursday,
                currentAvailabilityHash = PlanReplanner.availabilityHash(
                    profile(defaultAvailability()).weeklyAvailability,
                ),
            ),
        )

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
        assertThat((result as ReplanResult.NoChange).reason).contains("Nenhum desvio")
    }

    @Test
    fun `uses the configured threshold as a product parameter`() {
        val current = planOf(
            session(monday, WorkoutType.QUALITY, 40),
            session(
                wednesday,
                WorkoutType.EASY,
                40,
                status = SessionStatus.DONE,
                completionEvidence = CompletionEvidence.ACTIVITY,
                activityId = "activity-wed",
            ),
            session(saturday, WorkoutType.LONG, 34),
        )

        val result = planner.evaluate(
            input(current, today = thursday, missedSessionThreshold = 1),
        )

        val suggested = result as ReplanResult.Suggest
        assertThat(suggested.suggestion.missedTrainableCount).isEqualTo(1)
    }

    @Test
    fun `does not count missing days before the plan creation date`() {
        val current = planOf(
            session(monday, WorkoutType.QUALITY, 40),
            session(tuesday, WorkoutType.EASY, 40),
            session(wednesday, WorkoutType.EASY, 40),
            session(saturday, WorkoutType.LONG, 34),
            createdOn = wednesday,
        )

        val result = planner.evaluate(input(current, today = thursday))

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
    }

    @Test
    fun `does not count rest days or past dates marked non-trainable`() {
        val current = planOf(
            session(monday, WorkoutType.REST, 0),
            session(wednesday, WorkoutType.EASY, 40),
            session(thursday, WorkoutType.EASY, 40),
            session(saturday, WorkoutType.LONG, 34),
        )

        val result = planner.evaluate(
            input(current, today = friday, nonTrainablePastDates = setOf(thursday)),
        )

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
    }

    @Test
    fun `does not count sessions closed as illness`() {
        val current = planOf(
            session(
                monday,
                WorkoutType.QUALITY,
                40,
                status = SessionStatus.CLOSED,
                closureReason = SessionClosureReason.ILLNESS_OR_INJURY,
            ),
            session(
                wednesday,
                WorkoutType.EASY,
                40,
                status = SessionStatus.CLOSED,
                closureReason = SessionClosureReason.ILLNESS_OR_INJURY,
            ),
            session(saturday, WorkoutType.LONG, 34),
        )

        val result = planner.evaluate(
            input(
                current = current,
                today = friday,
                currentAvailabilityHash = PlanReplanner.availabilityHash(
                    profile(defaultAvailability()).weeklyAvailability,
                ),
            ),
        )

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
        assertThat((result as ReplanResult.NoChange).reason).contains("Nenhum desvio")
    }

    // --- T2: availability -------------------------------------------------

    @Test
    fun `availability change replaces only strictly future planned sessions`() {
        val oldAvailability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.THURSDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val newAvailability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val current = planOf(
            session(monday, WorkoutType.QUALITY, 40),
            session(wednesday, WorkoutType.EASY, 40),
            session(thursday, WorkoutType.EASY, 40),
            session(saturday, WorkoutType.LONG, 51),
        )

        val result = planner.evaluate(
            input(
                current = current,
                today = wednesday,
                trigger = ReplanTrigger.AVAILABILITY_CHANGED,
                profile = profile(newAvailability),
                currentAvailabilityHash = PlanReplanner.availabilityHash(
                    WeeklyAvailability(oldAvailability),
                ),
            ),
        )

        val suggested = result as ReplanResult.Suggest
        assertThat(suggested.suggestion.trigger).isEqualTo(ReplanTrigger.AVAILABILITY_CHANGED)
        assertThat(suggested.suggestion.expectedAvailabilityHash)
            .isEqualTo(PlanReplanner.availabilityHash(WeeklyAvailability(oldAvailability)))
        assertThat(suggested.suggestion.availabilityHash)
            .isEqualTo(PlanReplanner.availabilityHash(WeeklyAvailability(newAvailability)))
        assertThat(suggested.candidate.sessions.first { it.date == monday })
            .isEqualTo(current.sessions.first { it.date == monday })
        assertThat(suggested.candidate.sessions.first { it.date == wednesday })
            .isEqualTo(current.sessions.first { it.date == wednesday })
        assertThat(suggested.candidate.sessions.map { it.date }).doesNotContain(thursday)
        val fridaySession = suggested.candidate.sessions.first { it.date == friday }
        assertThat(fridaySession.type).isEqualTo(WorkoutType.EASY)
        assertThat(suggested.candidate.sessions.first { it.date == saturday }.durationMin).isEqualTo(34)
    }

    @Test
    fun `unknown availability hash only establishes the baseline`() {
        val newAvailability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.THURSDAY to 40)
        val current = planOf(
            session(monday, WorkoutType.EASY, 40),
            session(wednesday, WorkoutType.EASY, 40),
        )

        val result = planner.evaluate(
            input(
                current = current,
                today = tuesday,
                trigger = ReplanTrigger.AVAILABILITY_CHANGED,
                profile = profile(newAvailability),
                currentAvailabilityHash = null,
            ),
        )

        val noChange = result as ReplanResult.NoChange
        assertThat(noChange.reason).contains("referência")
        assertThat(noChange.availabilityHash)
            .isEqualTo(PlanReplanner.availabilityHash(WeeklyAvailability(newAvailability)))
    }

    @Test
    fun `equal availability hash does not trigger a revision`() {
        val availability = mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40, DayOfWeek.SATURDAY to 90)
        val current = planOf(
            session(monday, WorkoutType.EASY, 40),
            session(wednesday, WorkoutType.EASY, 40),
            session(saturday, WorkoutType.LONG, 34),
        )

        val result = planner.evaluate(
            input(
                current = current,
                today = tuesday,
                trigger = ReplanTrigger.AVAILABILITY_CHANGED,
                profile = profile(availability),
                currentAvailabilityHash = PlanReplanner.availabilityHash(
                    WeeklyAvailability(availability),
                ),
            ),
        )

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
        assertThat((result as ReplanResult.NoChange).reason).contains("Nenhum desvio")
    }

    @Test
    fun `does not increase future volume when availability grows`() {
        val oldAvailability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val newAvailability = oldAvailability + (DayOfWeek.THURSDAY to 40)
        val current = planOf(
            session(monday, WorkoutType.QUALITY, 40),
            session(wednesday, WorkoutType.EASY, 40),
            session(saturday, WorkoutType.LONG, 34),
        )

        val result = planner.evaluate(
            input(
                current = current,
                today = wednesday,
                trigger = ReplanTrigger.AVAILABILITY_CHANGED,
                profile = profile(newAvailability),
                currentAvailabilityHash = PlanReplanner.availabilityHash(
                    WeeklyAvailability(oldAvailability),
                ),
            ),
        )

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
        assertThat((result as ReplanResult.NoChange).reason).contains("volume")
    }

    // --- T3 and semantic equality -----------------------------------------

    @Test
    fun `user request replans when the future changes`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val current = generatedPlan(profile(availability), today = monday)

        val result = planner.evaluate(
            input(
                current = current,
                today = monday,
                trigger = ReplanTrigger.USER_REQUEST,
                profile = profile(availability, thresholdPace = 330),
                currentAvailabilityHash = PlanReplanner.availabilityHash(
                    WeeklyAvailability(availability),
                ),
            ),
        )

        val suggested = result as ReplanResult.Suggest
        assertThat(suggested.suggestion.trigger).isEqualTo(ReplanTrigger.USER_REQUEST)
        val currentEasy = current.sessions.first { it.date == wednesday }
        val candidateEasy = suggested.candidate.sessions.first { it.date == wednesday }
        assertThat(candidateEasy.type).isEqualTo(currentEasy.type)
        assertThat(candidateEasy.durationMin).isEqualTo(currentEasy.durationMin)
        assertThat(candidateEasy.targets).isNotEqualTo(currentEasy.targets)
    }

    @Test
    fun `user request detects a rule change in future qualities`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SUNDAY to 90,
        )
        val previousWeek = completedPreviousWeek()
        val current = generatedPlan(
            profile(availability),
            today = monday,
            previousWeek = previousWeek,
        )

        val result = planner.evaluate(
            input(
                current = current,
                today = monday,
                trigger = ReplanTrigger.USER_REQUEST,
                profile = profile(availability, treadmillAccess = true),
                previousWeek = previousWeek,
                currentAvailabilityHash = PlanReplanner.availabilityHash(
                    WeeklyAvailability(availability),
                ),
            ),
        )

        val suggested = result as ReplanResult.Suggest
        val currentQuality = current.sessions.first {
            it.date == wednesday && it.type == WorkoutType.QUALITY
        }
        val candidateQuality = suggested.candidate.sessions.first {
            it.date == wednesday && it.type == WorkoutType.QUALITY
        }
        assertThat(currentQuality.ruleIds).doesNotContain("R-PLAN-08")
        assertThat(candidateQuality.ruleIds).contains("R-PLAN-08")
    }

    @Test
    fun `user request with an identical future returns no change`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val current = generatedPlan(profile(availability), today = monday)

        val result = planner.evaluate(
            input(
                current = current,
                today = monday,
                trigger = ReplanTrigger.USER_REQUEST,
                profile = profile(availability),
                currentAvailabilityHash = PlanReplanner.availabilityHash(
                    WeeklyAvailability(availability),
                ),
            ),
        )

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
        assertThat((result as ReplanResult.NoChange).reason).contains("já está")
    }

    @Test
    fun `a manual swap is replaced by the suggested revision`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val generated = generatedPlan(profile(availability), today = monday)
        val swapped = SessionSwapper.swap(generated, wednesday, saturday, today = monday) as SwapResult.Applied
        assertThat(swapped.plan.sessions.first { it.date == wednesday }.type).isEqualTo(WorkoutType.LONG)

        val result = planner.evaluate(
            input(
                current = swapped.plan,
                today = monday,
                trigger = ReplanTrigger.USER_REQUEST,
                profile = profile(availability),
                currentAvailabilityHash = PlanReplanner.availabilityHash(
                    WeeklyAvailability(availability),
                ),
            ),
        )

        val suggested = result as ReplanResult.Suggest
        assertThat(suggested.candidate.sessions.first { it.date == wednesday }.type)
            .isEqualTo(WorkoutType.EASY)
        assertThat(suggested.candidate.sessions.first { it.date == saturday }.type)
            .isEqualTo(WorkoutType.LONG)
    }

    @Test
    fun `does not move a missed quality into the future`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.THURSDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val current = generatedPlan(profile(availability), today = monday)

        val result = planner.evaluate(
            input(
                current = current,
                today = tuesday,
                profile = profile(availability),
                missedSessionThreshold = 1,
                currentAvailabilityHash = PlanReplanner.availabilityHash(
                    WeeklyAvailability(availability),
                ),
            ),
        )

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
        assertThat((result as ReplanResult.NoChange).reason).contains("treino forte")
    }

    @Test
    fun `does not add a future quality beyond the current future intent`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        // Created when load was not trusted: the week had no quality.
        val current = generatedPlan(profile(availability), today = monday, load = null)

        val result = planner.evaluate(
            input(
                current = current,
                today = tuesday,
                trigger = ReplanTrigger.USER_REQUEST,
                profile = profile(availability),
                load = knownLoad(),
            ),
        )

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
        assertThat((result as ReplanResult.NoChange).reason).contains("treino forte")
    }

    // --- Merged-week guardrails -------------------------------------------

    @Test
    fun `counts completed sessions against the weekly quality limit`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.TUESDAY to 40,
            DayOfWeek.WEDNESDAY to 90,
            DayOfWeek.THURSDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SUNDAY to 40,
        )
        val current = planOf(
            session(
                monday,
                WorkoutType.QUALITY,
                40,
                status = SessionStatus.DONE,
                completionEvidence = CompletionEvidence.ACTIVITY,
                activityId = "activity-mon",
            ),
            session(tuesday, WorkoutType.EASY, 40),
            session(wednesday, WorkoutType.LONG, 69),
            session(thursday, WorkoutType.EASY, 40),
            session(friday, WorkoutType.QUALITY, 40),
            session(sunday, WorkoutType.EASY, 40),
        )

        val result = planner.evaluate(
            input(
                current = current,
                today = tuesday,
                trigger = ReplanTrigger.USER_REQUEST,
                profile = profile(availability),
                previousWeek = completedPreviousWeek(),
            ),
        )

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
        assertThat((result as ReplanResult.NoChange).reason).contains("2 treinos fortes")
    }

    @Test
    fun `rejects a future long run adjacent to a completed quality`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.TUESDAY to 90,
            DayOfWeek.THURSDAY to 40,
            DayOfWeek.SATURDAY to 40,
        )
        val current = planOf(
            session(
                monday,
                WorkoutType.QUALITY,
                40,
                status = SessionStatus.DONE,
                completionEvidence = CompletionEvidence.ACTIVITY,
                activityId = "activity-mon",
            ),
            session(tuesday, WorkoutType.EASY, 40),
            session(thursday, WorkoutType.EASY, 40),
            session(saturday, WorkoutType.EASY, 40),
        )

        val result = planner.evaluate(
            input(
                current = current,
                today = monday,
                trigger = ReplanTrigger.USER_REQUEST,
                profile = profile(availability),
            ),
        )

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
        assertThat((result as ReplanResult.NoChange).reason).contains("nem ao lado")
    }

    @Test
    fun `rejects a future quality adjacent to a completed quality`() {
        val oldAvailability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.TUESDAY to 40,
            DayOfWeek.THURSDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val newAvailability = mapOf(
            DayOfWeek.TUESDAY to 40,
            DayOfWeek.THURSDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val current = planOf(
            session(
                monday,
                WorkoutType.QUALITY,
                40,
                status = SessionStatus.DONE,
                completionEvidence = CompletionEvidence.ACTIVITY,
                activityId = "activity-mon",
            ),
            session(tuesday, WorkoutType.EASY, 40),
            session(thursday, WorkoutType.EASY, 40),
            session(saturday, WorkoutType.LONG, 34),
        )

        val result = planner.evaluate(
            input(
                current = current,
                today = monday,
                trigger = ReplanTrigger.AVAILABILITY_CHANGED,
                profile = profile(newAvailability),
                currentAvailabilityHash = PlanReplanner.availabilityHash(
                    WeeklyAvailability(oldAvailability),
                ),
            ),
        )

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
        assertThat((result as ReplanResult.NoChange).reason).contains("dias consecutivos")
    }

    @Test
    fun `does not schedule a second long run when one was already done`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 90,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val current = planOf(
            session(
                monday,
                WorkoutType.LONG,
                90,
                status = SessionStatus.DONE,
                completionEvidence = CompletionEvidence.ACTIVITY,
                activityId = "activity-mon",
            ),
            session(wednesday, WorkoutType.EASY, 40),
            session(saturday, WorkoutType.EASY, 40),
        )

        val result = planner.evaluate(
            input(
                current = current,
                today = wednesday,
                trigger = ReplanTrigger.USER_REQUEST,
                profile = profile(availability),
            ),
        )

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
        assertThat((result as ReplanResult.NoChange).reason).contains("longão")
    }

    @Test
    fun `protects completed and closed future sessions`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val closed = session(
            friday,
            WorkoutType.EASY,
            40,
            status = SessionStatus.CLOSED,
            closureReason = SessionClosureReason.ILLNESS_OR_INJURY,
        )
        val current = planOf(
            session(monday, WorkoutType.EASY, 40),
            session(wednesday, WorkoutType.EASY, 40),
            closed,
            session(saturday, WorkoutType.LONG, 40),
        )

        val result = planner.evaluate(
            input(
                current = current,
                today = wednesday,
                trigger = ReplanTrigger.USER_REQUEST,
                profile = profile(availability),
            ),
        )

        val suggested = result as ReplanResult.Suggest
        assertThat(suggested.candidate.sessions.first { it.date == friday }).isEqualTo(closed)
        assertThat(suggested.candidate.sessions.first { it.date == saturday }.durationMin)
            .isEqualTo(34)
    }

    @Test
    fun `does not replan a week other than the current one`() {
        val current = planOf(
            session(monday, WorkoutType.EASY, 40),
            createdOn = monday,
        ).copy(weekStart = monday.minusWeeks(1))

        val result = planner.evaluate(input(current, today = monday, trigger = ReplanTrigger.USER_REQUEST))

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
        assertThat((result as ReplanResult.NoChange).reason).contains("semana vigente")
    }

    @Test
    fun `returns no change when there are no future days`() {
        val current = planOf(
            session(monday, WorkoutType.EASY, 40),
            session(saturday, WorkoutType.LONG, 34),
        )

        val result = planner.evaluate(input(current, today = sunday, trigger = ReplanTrigger.USER_REQUEST))

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
        assertThat((result as ReplanResult.NoChange).reason).contains("dias futuros")
    }

    @Test
    fun `returns no change without configured availability`() {
        val current = planOf(
            session(monday, WorkoutType.EASY, 40),
            session(wednesday, WorkoutType.EASY, 40),
        )

        val result = planner.evaluate(
            input(
                current = current,
                today = tuesday,
                trigger = ReplanTrigger.USER_REQUEST,
                profile = profile(emptyMap()),
            ),
        )

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
        assertThat((result as ReplanResult.NoChange).reason).contains("disponibilidade")
    }

    @Test
    fun `returns no change without any planned week`() {
        val current = planOf()

        val result = planner.evaluate(input(current, today = wednesday, trigger = ReplanTrigger.USER_REQUEST))

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
        assertThat((result as ReplanResult.NoChange).reason).contains("dias futuros")
    }

    // --- Determinism and hashes -------------------------------------------

    @Test
    fun `is deterministic and exposes the hashes used by the suggestion`() {
        val current = planOf(
            session(monday, WorkoutType.QUALITY, 40),
            session(wednesday, WorkoutType.EASY, 40),
            session(saturday, WorkoutType.LONG, 34),
        )
        val replanInput = input(current, today = thursday)

        val first = planner.evaluate(replanInput) as ReplanResult.Suggest
        val second = planner.evaluate(replanInput) as ReplanResult.Suggest

        assertThat(second).isEqualTo(first)
        assertThat(first.suggestion.expectedInputsHash).isEqualTo(PlanReplanner.inputsHash(replanInput))
        assertThat(first.suggestion.expectedInputsHash).isNotBlank()
        assertThat(first.suggestion.availabilityHash)
            .isEqualTo(PlanReplanner.availabilityHash(profile(defaultAvailability()).weeklyAvailability))
    }

    @Test
    fun `availability hash is order independent and changes with the minutes`() {
        val ordered = WeeklyAvailability(
            linkedMapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40),
        )
        val shuffled = WeeklyAvailability(
            linkedMapOf(DayOfWeek.WEDNESDAY to 40, DayOfWeek.MONDAY to 40),
        )
        val changed = WeeklyAvailability(
            linkedMapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 45),
        )

        assertThat(PlanReplanner.availabilityHash(ordered))
            .isEqualTo(PlanReplanner.availabilityHash(shuffled))
        assertThat(PlanReplanner.availabilityHash(changed))
            .isNotEqualTo(PlanReplanner.availabilityHash(ordered))
    }

    // --- T4: planning fingerprint and temporal context ---------------------

    @Test
    fun `a changed planning profile suggests a revision and cites R-PLAN-09`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SUNDAY to 90,
        )
        val previousWeek = completedPreviousWeek()
        val original = profile(availability)
        val current = generatedPlan(original, today = monday, previousWeek = previousWeek)
        val changedProfile = profile(availability, treadmillAccess = true)
        val originalFingerprint = PlanningFingerprints.of(original)

        val result = planner.evaluate(
            input(
                current = current,
                today = monday,
                profile = changedProfile,
                previousWeek = previousWeek,
                currentAvailabilityHash = PlanReplanner.availabilityHash(
                    WeeklyAvailability(availability),
                ),
                currentEvaluatedFingerprintVersion = originalFingerprint.version,
                currentEvaluatedFingerprint = originalFingerprint.hash,
                currentTemporalContextHash = PlanningFingerprints.temporalContextHash(original),
            ),
        )

        val suggested = result as ReplanResult.Suggest
        assertThat(suggested.suggestion.trigger).isEqualTo(ReplanTrigger.PROFILE_CHANGED)
        assertThat(suggested.suggestion.causes).contains(ReplanCause.PROFILE_CHANGED)
        assertThat(suggested.suggestion.ruleIds).contains("R-PLAN-09")
        assertThat(suggested.suggestion.expectedFingerprintVersion)
            .isEqualTo(PlanningFingerprints.VERSION)
        assertThat(suggested.suggestion.expectedFingerprint)
            .isEqualTo(PlanningFingerprints.of(changedProfile).hash)
        assertThat(suggested.suggestion.expectedTemporalContextHash)
            .isEqualTo(PlanningFingerprints.temporalContextHash(changedProfile))
        assertThat(suggested.suggestion.profileSnapshot.treadmillAccess).isTrue()
    }

    @Test
    fun `an unchanged fingerprint and temporal context do not trigger T4`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val currentProfile = profile(availability)
        val current = generatedPlan(currentProfile, today = monday)
        val fingerprint = PlanningFingerprints.of(currentProfile)

        val result = planner.evaluate(
            input(
                current = current,
                today = monday,
                profile = currentProfile,
                currentAvailabilityHash = PlanReplanner.availabilityHash(
                    WeeklyAvailability(availability),
                ),
                currentEvaluatedFingerprintVersion = fingerprint.version,
                currentEvaluatedFingerprint = fingerprint.hash,
                currentTemporalContextHash = PlanningFingerprints.temporalContextHash(currentProfile),
            ),
        )

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
        assertThat((result as ReplanResult.NoChange).reason).contains("Nenhum desvio")
    }

    @Test
    fun `a timezone change alone is registered as the temporal context cause`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val currentProfile = profile(availability)
        val current = planOf(
            session(monday, WorkoutType.EASY, 40),
            session(wednesday, WorkoutType.EASY, 40),
            session(saturday, WorkoutType.LONG, 34),
        )
        val fingerprint = PlanningFingerprints.of(currentProfile)

        val result = planner.evaluate(
            input(
                current = current,
                today = monday,
                profile = currentProfile.copy(timezone = "America/Recife"),
                currentAvailabilityHash = PlanReplanner.availabilityHash(
                    WeeklyAvailability(availability),
                ),
                currentEvaluatedFingerprintVersion = fingerprint.version,
                currentEvaluatedFingerprint = fingerprint.hash,
                currentTemporalContextHash = PlanningFingerprints.temporalContextHash(currentProfile),
            ),
        )

        val suggested = result as ReplanResult.Suggest
        assertThat(suggested.suggestion.trigger).isEqualTo(ReplanTrigger.PROFILE_CHANGED)
        assertThat(suggested.suggestion.causes).containsExactly(ReplanCause.TIMEZONE_CONTEXT_CHANGED)
        assertThat(suggested.suggestion.rationale).contains("fuso")
        assertThat(suggested.suggestion.ruleIds).contains("R-PLAN-09")
    }

    @Test
    fun `multiple concurrent causes are all recorded`() {
        val oldAvailability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.THURSDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val newAvailability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.FRIDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val original = profile(oldAvailability)
        val originalFingerprint = PlanningFingerprints.of(original)
        val current = planOf(
            session(monday, WorkoutType.QUALITY, 40),
            session(wednesday, WorkoutType.EASY, 40),
            session(thursday, WorkoutType.EASY, 40),
            session(saturday, WorkoutType.LONG, 51),
        )

        val result = planner.evaluate(
            input(
                current = current,
                today = wednesday,
                profile = profile(newAvailability, treadmillAccess = true),
                currentAvailabilityHash = PlanReplanner.availabilityHash(
                    WeeklyAvailability(oldAvailability),
                ),
                currentEvaluatedFingerprintVersion = originalFingerprint.version,
                currentEvaluatedFingerprint = originalFingerprint.hash,
                currentTemporalContextHash = PlanningFingerprints.temporalContextHash(original),
            ),
        )

        val suggested = result as ReplanResult.Suggest
        assertThat(suggested.suggestion.causes).contains(
            ReplanCause.PROFILE_CHANGED,
            ReplanCause.AVAILABILITY_CHANGED,
        )
        assertThat(suggested.suggestion.rationale).contains("Mais de um motivo")
    }

    @Test
    fun `a legacy checkpoint is not inferred as a profile change`() {
        val availability = mapOf(
            DayOfWeek.MONDAY to 40,
            DayOfWeek.WEDNESDAY to 40,
            DayOfWeek.SATURDAY to 90,
        )
        val currentProfile = profile(availability)
        val current = planOf(
            session(monday, WorkoutType.EASY, 40),
            session(wednesday, WorkoutType.EASY, 40),
            session(saturday, WorkoutType.LONG, 34),
        )

        val result = planner.evaluate(
            input(
                current = current,
                today = monday,
                profile = currentProfile,
                currentAvailabilityHash = PlanReplanner.availabilityHash(
                    WeeklyAvailability(availability),
                ),
                currentEvaluatedFingerprintVersion = null,
                currentEvaluatedFingerprint = null,
                currentTemporalContextHash = null,
            ),
        )

        val noChange = result as ReplanResult.NoChange
        assertThat(noChange.reason).contains("Nenhum desvio")
        assertThat(noChange.checkpoint.fingerprint)
            .isEqualTo(PlanningFingerprints.of(currentProfile).hash)
        assertThat(noChange.checkpoint.temporalContextHash)
            .isEqualTo(PlanningFingerprints.temporalContextHash(currentProfile))
    }

    @Test
    fun `rejects a threshold below one`() {
        val current = planOf(session(monday, WorkoutType.EASY, 40), session(saturday, WorkoutType.LONG, 34))

        assertThatThrownBy {
            input(current, today = thursday, missedSessionThreshold = 0)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    // --- Fixtures ----------------------------------------------------------

    @Test
    fun `never suggests a session on a race date`() {
        val profile = profile(mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40))
        val current = planOf(
            session(monday, WorkoutType.EASY, 40),
            session(monday.plusDays(2), WorkoutType.EASY, 40),
        )

        val result = PlanReplanner().evaluate(
            input(
                current = current,
                today = monday,
                trigger = ReplanTrigger.USER_REQUEST,
                profile = profile,
                eventDates = setOf(monday.plusDays(2)),
            ),
        )

        assertThat(result).isInstanceOf(ReplanResult.Suggest::class.java)
        val candidate = (result as ReplanResult.Suggest).candidate
        assertThat(candidate.sessions.map { it.date }).doesNotContain(monday.plusDays(2))
        assertThat(candidate.sessions.none { it.date.isAfter(monday) }).isTrue()
    }

    @Test
    fun `does not count a past session on a race date as missed`() {
        val profile = profile(defaultAvailability())
        val current = planOf(
            session(monday, WorkoutType.EASY, 40),
            session(monday.plusDays(1), WorkoutType.EASY, 40),
        )

        val result = PlanReplanner().evaluate(
            input(
                current = current,
                today = monday.plusDays(2),
                profile = profile,
                currentAvailabilityHash = PlanReplanner.availabilityHash(profile.weeklyAvailability),
                eventDates = setOf(monday),
            ),
        )

        assertThat(result).isInstanceOf(ReplanResult.NoChange::class.java)
        assertThat((result as ReplanResult.NoChange).reason).contains("Nenhum desvio elegível")
    }

    @Test
    fun `removes a legacy quality inside the race week`() {
        val profile = profile(defaultAvailability())
        val current = planOf(
            session(monday.plusDays(2), WorkoutType.QUALITY, 30),
            session(monday.plusDays(5), WorkoutType.LONG, 90),
        )

        val result = PlanReplanner().evaluate(
            input(
                current = current,
                today = monday,
                trigger = ReplanTrigger.USER_REQUEST,
                profile = profile,
                race = RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(5)),
                eventDates = setOf(monday.plusDays(5)),
            ),
        )

        assertThat(result).isInstanceOf(ReplanResult.Suggest::class.java)
        val candidate = (result as ReplanResult.Suggest).candidate
        assertThat(candidate.sessions.filter { it.type == WorkoutType.QUALITY }).isEmpty()
        assertThat(candidate.sessions.map { it.date }).doesNotContain(monday.plusDays(5))
    }

    @Test
    fun `inputs hash changes when the race or an event date changes`() {
        val profile = profile(defaultAvailability())
        val current = planOf(session(monday, WorkoutType.EASY, 40))

        val base = PlanReplanner.inputsHash(input(current = current, today = monday, profile = profile))
        val race = PlanReplanner.inputsHash(
            input(
                current = current,
                today = monday,
                profile = profile,
                race = RacePlan("race-1", RaceDistance.TEN_K, monday.plusDays(20)),
            ),
        )
        val event = PlanReplanner.inputsHash(
            input(
                current = current,
                today = monday,
                profile = profile,
                eventDates = setOf(monday.plusDays(3)),
            ),
        )

        assertThat(base).isNotEqualTo(race)
        assertThat(base).isNotEqualTo(event)
    }

    private fun input(
        current: WeeklyPlan,
        today: LocalDate,
        trigger: ReplanTrigger = ReplanTrigger.MISSED_SESSIONS,
        profile: AthleteProfile = profile(defaultAvailability()),
        readiness: ReadinessResult = readiness(),
        load: LoadSnapshot? = knownLoad(),
        previousWeek: WeeklyPlan? = null,
        weekNumberInBlock: Int = 1,
        nonTrainablePastDates: Set<LocalDate> = emptySet(),
        missedSessionThreshold: Int = PlanReplanner.PRODUCT_MISSED_SESSION_THRESHOLD,
        currentVersion: Int = 1,
        currentInputsHash: String? = null,
        currentAvailabilityHash: String? = null,
        currentEvaluatedFingerprintVersion: String? = null,
        currentEvaluatedFingerprint: String? = null,
        currentTemporalContextHash: String? = null,
        race: RacePlan? = null,
        eventDates: Set<LocalDate> = emptySet(),
    ) = ReplanInput(
        currentPlan = current,
        currentVersion = currentVersion,
        currentInputsHash = currentInputsHash,
        currentAvailabilityHash = currentAvailabilityHash,
        currentEvaluatedFingerprintVersion = currentEvaluatedFingerprintVersion,
        currentEvaluatedFingerprint = currentEvaluatedFingerprint,
        currentTemporalContextHash = currentTemporalContextHash,
        trigger = trigger,
        profile = profile,
        readiness = readiness,
        load = load,
        previousWeek = previousWeek,
        weekNumberInBlock = weekNumberInBlock,
        nonTrainablePastDates = nonTrainablePastDates,
        race = race,
        eventDates = eventDates,
        missedSessionThreshold = missedSessionThreshold,
        today = today,
    )

    private fun generatedPlan(
        profile: AthleteProfile,
        today: LocalDate,
        load: LoadSnapshot? = knownLoad(),
        previousWeek: WeeklyPlan? = null,
    ) = generator.generate(
        WeeklyPlanInput(
            profile = profile,
            readiness = readiness(),
            load = load,
            weekStart = monday,
            today = today,
            previousWeek = previousWeek,
        ),
    )

    private fun planOf(vararg sessions: PlannedSession, createdOn: LocalDate = monday) = WeeklyPlan(
        weekStart = monday,
        sessions = sessions.toList().sortedBy { it.date },
        weeklyMinutes = sessions.sumOf { it.durationMin ?: 0 },
        longRunMinutes = sessions.firstOrNull { it.type == WorkoutType.LONG }?.durationMin,
        qualityCount = sessions.count { it.type == WorkoutType.QUALITY },
        rationale = "Plano vigente da semana.",
        confidence = DataConfidence.MEDIUM,
        ruleIds = listOf("R-PLAN-02", "R-PLAN-03"),
        evidenceIds = listOf("EV-SEILER-2010"),
        createdOn = createdOn,
    )

    private fun session(
        date: LocalDate,
        type: WorkoutType,
        durationMin: Int,
        title: String = defaultTitle(type),
        status: SessionStatus = SessionStatus.PLANNED,
        completionEvidence: CompletionEvidence? = null,
        activityId: String? = null,
        closureReason: SessionClosureReason? = null,
    ) = PlannedSession(
        date = date,
        type = type,
        title = title,
        durationMin = durationMin,
        rationale = "Conteúdo da sessão.",
        ruleIds = listOf("R-PLAN-02"),
        evidenceIds = listOf("EV-SEILER-2010"),
        targets = PrescriptionTargets(),
        status = status,
        completionEvidence = completionEvidence,
        activityId = activityId,
        closureReason = closureReason,
    )

    private fun defaultTitle(type: WorkoutType): String = when (type) {
        WorkoutType.REST -> "Descanso"
        WorkoutType.EASY -> "Fácil e conversável"
        WorkoutType.LONG -> "Longão"
        WorkoutType.QUALITY -> "Tempo"
    }

    private fun defaultAvailability(): Map<DayOfWeek, Int> = mapOf(
        DayOfWeek.MONDAY to 40,
        DayOfWeek.WEDNESDAY to 40,
        DayOfWeek.SATURDAY to 90,
    )

    private fun completedPreviousWeek(): WeeklyPlan {
        val previousMonday = monday.minusWeeks(1)
        val sessions = (0L..3L).map { offset ->
            PlannedSession(
                date = previousMonday.plusDays(offset),
                type = WorkoutType.EASY,
                title = "Fácil e conversável",
                durationMin = 40,
                rationale = "Base aeróbica.",
                ruleIds = listOf("R-ZONE-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                status = SessionStatus.DONE,
                completionEvidence = CompletionEvidence.ACTIVITY,
                activityId = "previous-$offset",
            )
        }
        return WeeklyPlan(
            weekStart = previousMonday,
            sessions = sessions,
            weeklyMinutes = 160,
            longRunMinutes = null,
            qualityCount = 0,
            rationale = "Semana anterior concluída.",
            confidence = DataConfidence.MEDIUM,
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
        )
    }

    private fun profile(
        availability: Map<DayOfWeek, Int>,
        thresholdPace: Int = 360,
        treadmillAccess: Boolean = false,
    ) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(thresholdPace),
        experience = Experience.INTERMEDIATE,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(availability),
        treadmillAccess = treadmillAccess,
    )

    private fun readiness() = ReadinessResult(
        score = 80,
        level = ReadinessLevel.GREEN,
        confidence = DataConfidence.HIGH,
        components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        missing = emptySet(),
        flags = emptySet<ReadinessFlag>(),
    )

    private fun knownLoad() = LoadSnapshot(
        daily = emptyList(),
        ctlSeries = emptyList(),
        atlSeries = emptyList(),
        ctl = 0.0,
        atl = 0.0,
        tsb = 0.0,
        acuteAverage7d = 0.0,
        chronicAverage28d = 0.0,
        acwr = 1.0,
        monotony = null,
        missingDays = 16,
        coverage = LoadCoverage(
            daysInWindow = 28,
            daysWithLoad = 12,
            daysWithoutActivity = 16,
            daysWithoutData = 0,
            lastActivityAt = Instant.parse("2026-10-30T10:00:00Z"),
            sourceFresh = true,
        ),
    )
}
