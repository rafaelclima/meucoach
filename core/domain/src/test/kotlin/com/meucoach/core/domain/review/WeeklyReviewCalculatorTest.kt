package com.meucoach.core.domain.review

import com.meucoach.core.domain.prescription.PlanCompletionWeek
import com.meucoach.core.domain.readiness.ReadinessHistoryDay
import com.meucoach.core.domain.trends.WeeklyIntensity
import com.meucoach.core.model.DailyCheckIn
import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ReadinessLevel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.LocalDate

class WeeklyReviewCalculatorTest {
    private val calculator = WeeklyReviewCalculator()
    private val weekStart = LocalDate.of(2026, 11, 2)
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun `summarizes a complete week with high confidence`() {
        val review = calculator.calculate(
            weekStart = weekStart,
            today = today,
            hasPlan = true,
            completion = completionWeek(planned = 4, done = 3, verified = 2, manual = 1, skipped = 1),
            plannedDurationMin = 150,
            executedDurationMin = 160,
            intensity = WeeklyIntensity(weekStart, easySec = 7_200, moderateSec = 1_800, hardSec = 1_800),
            intensitySessions = 4,
            readinessDays = greenDays(5),
            metrics = sleepNights(listOf(450, 460, 440, 430, 470, 455)),
            checkIns = emptyList(),
        )

        assertThat(review.status).isEqualTo(WeeklyReviewStatus.AVAILABLE)
        assertThat(review.weekStart).isEqualTo(weekStart)
        assertThat(review.weekEnd).isEqualTo(today)
        assertThat(review.adherence?.planned).isEqualTo(4)
        assertThat(review.adherence?.done).isEqualTo(3)
        assertThat(review.adherence?.verified).isEqualTo(2)
        assertThat(review.adherence?.manual).isEqualTo(1)
        assertThat(review.adherence?.skipped).isEqualTo(1)
        assertThat(review.adherence?.completionRatio).isEqualTo(0.75)
        assertThat(review.adherence?.verifiedRatio).isEqualTo(0.5)
        assertThat(review.volume?.plannedMin).isEqualTo(150)
        assertThat(review.volume?.executedMin).isEqualTo(160)
        assertThat(review.volume?.percentOfPlan).isEqualTo(107)
        assertThat(review.intensity?.easySec).isEqualTo(7_200)
        assertThat(review.intensity?.easyPercent()).isEqualTo(66.666, within(0.01))
        assertThat(review.intensitySessions).isEqualTo(4)
        assertThat(review.readiness?.measuredDays).isEqualTo(5)
        assertThat(review.readiness?.averageScore).isEqualTo(72)
        assertThat(review.readiness?.redDays).isZero()
        assertThat(review.sleep?.measuredNights).isEqualTo(6)
        assertThat(review.sleep?.averageMinutes).isEqualTo(451)
        assertThat(review.sleep?.shortNights).isZero()
        assertThat(review.confidence).isEqualTo(DataConfidence.HIGH)
        assertThat(review.ruleIds).contains(WeeklyReviewCalculator.RULE_ID)
    }

    @Test
    fun `reports partial adherence as a fact without judging`() {
        val review = review(completion = completionWeek(planned = 4, done = 2, verified = 1, manual = 1, skipped = 2))

        val observation = review.observations.single { it.kind == WeeklyReviewObservationKind.ADHERENCE_PARTIAL }
        assertThat(observation.ruleId).isEqualTo(WeeklyReviewCalculator.RULE_ID)
        assertThat(observation.evidenceIds).isEmpty()
        assertThat(observation.confidence).isEqualTo(DataConfidence.HIGH)
        assertThat(review.observations.map { it.kind }).doesNotContain(WeeklyReviewObservationKind.ADHERENCE_NONE)
    }

    @Test
    fun `reports no sessions done when the plan had sessions`() {
        val review = review(completion = completionWeek(planned = 3, done = 0, verified = 0, manual = 0, skipped = 3))

        assertThat(review.observations.map { it.kind }).contains(WeeklyReviewObservationKind.ADHERENCE_NONE)
        assertThat(review.observations.map { it.kind }).doesNotContain(WeeklyReviewObservationKind.ADHERENCE_PARTIAL)
    }

    @Test
    fun `does not report adherence for a week without planned sessions`() {
        val review = review(completion = completionWeek(planned = 0, done = 0, verified = 0, manual = 0, skipped = 0))

        assertThat(review.status).isEqualTo(WeeklyReviewStatus.AVAILABLE)
        assertThat(review.adherence?.completionRatio).isNull()
        assertThat(review.observations.map { it.kind }).doesNotContain(
            WeeklyReviewObservationKind.ADHERENCE_PARTIAL,
            WeeklyReviewObservationKind.ADHERENCE_NONE,
        )
    }

    @Test
    fun `flags illness first and caps the observations at three`() {
        val review = calculator.calculate(
            weekStart = weekStart,
            today = today,
            hasPlan = true,
            completion = completionWeek(planned = 4, done = 1, verified = 0, manual = 1, skipped = 3),
            plannedDurationMin = 150,
            executedDurationMin = 60,
            intensity = WeeklyIntensity(weekStart, easySec = 600, moderateSec = 1_800, hardSec = 1_800),
            intensitySessions = 4,
            readinessDays = redDays(3),
            metrics = sleepNights(listOf(330, 340, 320, 350)),
            checkIns = listOf(checkIn(weekStart.plusDays(2), illness = true)),
        )

        assertThat(review.observations).hasSize(3)
        assertThat(review.observations.first().kind).isEqualTo(WeeklyReviewObservationKind.ILLNESS_SIGNAL)
        assertThat(review.observations.first().ruleId).isEqualTo("R-SAFE-01")
        assertThat(review.observations.first().evidenceIds).isNotEmpty()
        assertThat(review.observations.map { it.kind }).doesNotContain(WeeklyReviewObservationKind.NO_ALERTS)
    }

    @Test
    fun `flags repeated short sleep with the sleep rules`() {
        val review = review(metrics = sleepNights(listOf(350, 340, 330, 420, 430)))

        val observation = review.observations.single { it.kind == WeeklyReviewObservationKind.SHORT_SLEEP }
        assertThat(observation.ruleId).isEqualTo("R-READY-04")
        assertThat(observation.evidenceIds).containsExactly("EV-WALSH-2021", "EV-WATSON-2015")
        assertThat(observation.confidence).isEqualTo(DataConfidence.MEDIUM)
        assertThat(review.sleep?.shortNights).isEqualTo(3)
    }

    @Test
    fun `does not flag two short nights`() {
        val review = review(metrics = sleepNights(listOf(350, 340, 420, 430, 440)))

        assertThat(review.observations.map { it.kind }).doesNotContain(WeeklyReviewObservationKind.SHORT_SLEEP)
        assertThat(review.sleep?.shortNights).isEqualTo(2)
    }

    @Test
    fun `flags repeated red readiness`() {
        val review = review(readinessDays = redDays(3))

        val observation = review.observations.single { it.kind == WeeklyReviewObservationKind.LOW_READINESS }
        assertThat(observation.ruleId).isEqualTo("R-READY-03")
        assertThat(observation.evidenceIds).containsExactly("EV-SAW-2016")
        assertThat(review.readiness?.redDays).isEqualTo(3)
    }

    @Test
    fun `does not flag readiness with fewer than three red days`() {
        val review = review(readinessDays = redDays(2))

        assertThat(review.observations.map { it.kind }).doesNotContain(WeeklyReviewObservationKind.LOW_READINESS)
    }

    @Test
    fun `flags intensity above the easy target`() {
        val review = review(
            intensity = WeeklyIntensity(weekStart, easySec = 3_600, moderateSec = 1_800, hardSec = 1_800),
            intensitySessions = 4,
        )

        val observation = review.observations.single {
            it.kind == WeeklyReviewObservationKind.INTENSITY_ABOVE_EASY_TARGET
        }
        assertThat(observation.ruleId).isEqualTo("R-PLAN-06")
        assertThat(observation.evidenceIds).containsExactly("EV-SEILER-2010")
    }

    @Test
    fun `does not judge intensity with fewer than three sessions`() {
        val review = review(
            intensity = WeeklyIntensity(weekStart, easySec = 600, moderateSec = 1_800, hardSec = 1_800),
            intensitySessions = 2,
        )

        assertThat(review.observations.map { it.kind }).doesNotContain(
            WeeklyReviewObservationKind.INTENSITY_ABOVE_EASY_TARGET,
        )
    }

    @Test
    fun `reports volume below the plan`() {
        val review = review(plannedDurationMin = 200, executedDurationMin = 100)

        val observation = review.observations.single { it.kind == WeeklyReviewObservationKind.VOLUME_BELOW_PLAN }
        assertThat(observation.ruleId).isEqualTo(WeeklyReviewCalculator.RULE_ID)
        assertThat(observation.evidenceIds).isEmpty()
        assertThat(review.volume?.percentOfPlan).isEqualTo(50)
    }

    @Test
    fun `reports volume above the plan`() {
        val review = review(plannedDurationMin = 100, executedDurationMin = 140)

        assertThat(review.volume?.percentOfPlan).isEqualTo(140)
        assertThat(review.observations.map { it.kind }).contains(WeeklyReviewObservationKind.VOLUME_ABOVE_PLAN)
    }

    @Test
    fun `keeps volume inside the tolerance band out of the observations`() {
        val review = review(plannedDurationMin = 100, executedDurationMin = 95)

        assertThat(review.observations.map { it.kind }).doesNotContain(
            WeeklyReviewObservationKind.VOLUME_BELOW_PLAN,
            WeeklyReviewObservationKind.VOLUME_ABOVE_PLAN,
        )
    }

    @Test
    fun `reports a week without alerts when everything is inside the expected range`() {
        val review = review(
            completion = completionWeek(planned = 3, done = 3, verified = 2, manual = 1, skipped = 0),
            plannedDurationMin = 100,
            executedDurationMin = 100,
            intensity = WeeklyIntensity(weekStart, easySec = 7_200, moderateSec = 900, hardSec = 900),
            intensitySessions = 4,
            readinessDays = greenDays(5),
            metrics = sleepNights(listOf(450, 460, 440, 430, 470)),
        )

        val observation = review.observations.single()
        assertThat(observation.kind).isEqualTo(WeeklyReviewObservationKind.NO_ALERTS)
        assertThat(observation.ruleId).isEqualTo(WeeklyReviewCalculator.RULE_ID)
        assertThat(review.confidence).isEqualTo(DataConfidence.HIGH)
    }

    @Test
    fun `returns an honest empty state without a plan but keeps executed facts`() {
        val review = calculator.calculate(
            weekStart = weekStart,
            today = today,
            hasPlan = false,
            plannedDurationMin = null,
            executedDurationMin = 95,
            intensity = null,
            readinessDays = emptyList(),
            metrics = emptyList(),
            checkIns = emptyList(),
        )

        assertThat(review.status).isEqualTo(WeeklyReviewStatus.EMPTY_NO_PLAN)
        assertThat(review.adherence).isNull()
        assertThat(review.volume?.executedMin).isEqualTo(95)
        assertThat(review.volume?.percentOfPlan).isNull()
        assertThat(review.observations).isEmpty()
        assertThat(review.confidence).isEqualTo(DataConfidence.LOW)
        assertThat(review.ruleIds).contains(WeeklyReviewCalculator.RULE_ID)
    }

    @Test
    fun `returns an empty state when there is nothing to consolidate`() {
        val review = calculator.calculate(weekStart = weekStart, today = today)

        assertThat(review.status).isEqualTo(WeeklyReviewStatus.EMPTY_NO_DATA)
        assertThat(review.adherence).isNull()
        assertThat(review.volume).isNull()
        assertThat(review.intensity).isNull()
        assertThat(review.readiness).isNull()
        assertThat(review.sleep).isNull()
        assertThat(review.observations).isEmpty()
        assertThat(review.confidence).isEqualTo(DataConfidence.LOW)
    }

    @Test
    fun `filters readiness sleep and check-ins outside the week`() {
        val review = calculator.calculate(
            weekStart = weekStart,
            today = today,
            hasPlan = true,
            completion = completionWeek(),
            readinessDays = greenDays(5) + listOf(
                readinessDay(weekStart.minusDays(1)),
                readinessDay(today.plusDays(1)),
            ),
            metrics = sleepNights(listOf(400, 400, 400, 400, 400)) + listOf(
                metrics(weekStart.minusDays(1), 300),
                metrics(today.plusDays(1), 300),
            ),
            checkIns = listOf(
                checkIn(weekStart.minusDays(1), illness = true),
                checkIn(today.plusDays(1), illness = true),
            ),
        )

        assertThat(review.readiness?.measuredDays).isEqualTo(5)
        assertThat(review.sleep?.measuredNights).isEqualTo(5)
        assertThat(review.observations.map { it.kind }).doesNotContain(WeeklyReviewObservationKind.ILLNESS_SIGNAL)
    }

    @Test
    fun `ignores days after today in the current week`() {
        val wednesday = weekStart.plusDays(2)
        val review = calculator.calculate(
            weekStart = weekStart,
            today = wednesday,
            hasPlan = true,
            completion = completionWeek(planned = 2, done = 2, verified = 2, manual = 0, skipped = 0),
            readinessDays = greenDays(3) + listOf(readinessDay(weekStart.plusDays(4))),
            metrics = sleepNights(listOf(400, 400, 400)) + listOf(metrics(weekStart.plusDays(5), 300)),
        )

        assertThat(review.readiness?.measuredDays).isEqualTo(3)
        assertThat(review.sleep?.measuredNights).isEqualTo(3)
    }

    @Test
    fun `deduplicates repeated daily metrics by date`() {
        val review = calculator.calculate(
            weekStart = weekStart,
            today = today,
            hasPlan = true,
            completion = completionWeek(),
            metrics = listOf(
                metrics(weekStart, 400),
                metrics(weekStart, 300),
                metrics(weekStart.plusDays(1), 400),
            ),
        )

        assertThat(review.sleep?.measuredNights).isEqualTo(2)
        assertThat(review.sleep?.averageMinutes).isEqualTo(350)
    }

    @Test
    fun `is deterministic for the same input`() {
        val first = review(readinessDays = redDays(3), metrics = sleepNights(listOf(350, 340, 330)))
        val second = review(readinessDays = redDays(3), metrics = sleepNights(listOf(350, 340, 330)))

        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `rejects a week that does not start on Monday`() {
        assertThatThrownBy {
            calculator.calculate(weekStart = weekStart.plusDays(1), today = today)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Monday")
    }

    @Test
    fun `rejects a review before the week starts`() {
        assertThatThrownBy {
            calculator.calculate(weekStart = weekStart, today = weekStart.minusDays(1))
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("before")
    }

    @Test
    fun `rejects a completion from another week`() {
        assertThatThrownBy {
            calculator.calculate(
                weekStart = weekStart,
                today = today,
                hasPlan = true,
                completion = PlanCompletionWeek(
                    weekStart = weekStart.minusWeeks(1),
                    planned = 1,
                    done = 1,
                    verified = 1,
                    manual = 0,
                    legacy = 0,
                    skipped = 0,
                ),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("week")
    }

    @Test
    fun `rejects a plan without completion`() {
        assertThatThrownBy {
            calculator.calculate(weekStart = weekStart, today = today, hasPlan = true)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("completion")
    }

    @Test
    fun `rejects invalid volumes and intensity pairing`() {
        assertThatThrownBy {
            calculator.calculate(weekStart = weekStart, today = today, plannedDurationMin = 0)
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            calculator.calculate(weekStart = weekStart, today = today, executedDurationMin = 0)
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            calculator.calculate(
                weekStart = weekStart,
                today = today,
                intensity = WeeklyIntensity(weekStart, 600, 600, 600),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("sessions")

        assertThatThrownBy {
            calculator.calculate(weekStart = weekStart, today = today, intensitySessions = 2)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("intensity")

        assertThatThrownBy {
            calculator.calculate(
                weekStart = weekStart,
                today = today,
                intensity = WeeklyIntensity(weekStart.minusWeeks(1), 600, 600, 600),
                intensitySessions = 1,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("week")
    }

    @Test
    fun `rejects a completion with planned sessions when the plan is absent`() {
        assertThatThrownBy {
            calculator.calculate(
                weekStart = weekStart,
                today = today,
                hasPlan = false,
                completion = completionWeek(planned = 1, done = 1, verified = 1, manual = 0, skipped = 0),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("plan")
    }

    private fun review(
        completion: PlanCompletionWeek = completionWeek(),
        plannedDurationMin: Int? = 150,
        executedDurationMin: Int? = 150,
        intensity: WeeklyIntensity? = null,
        intensitySessions: Int = 0,
        readinessDays: List<ReadinessHistoryDay> = emptyList(),
        metrics: List<DailyMetrics> = emptyList(),
        checkIns: List<DailyCheckIn> = emptyList(),
    ) = calculator.calculate(
        weekStart = weekStart,
        today = today,
        hasPlan = true,
        completion = completion,
        plannedDurationMin = plannedDurationMin,
        executedDurationMin = executedDurationMin,
        intensity = intensity,
        intensitySessions = intensitySessions,
        readinessDays = readinessDays,
        metrics = metrics,
        checkIns = checkIns,
    )

    private fun completionWeek(
        planned: Int = 3,
        done: Int = 2,
        verified: Int = 1,
        manual: Int = 1,
        legacy: Int = 0,
        skipped: Int = 1,
    ) = PlanCompletionWeek(
        weekStart = weekStart,
        planned = planned,
        done = done,
        verified = verified,
        manual = manual,
        legacy = legacy,
        skipped = skipped,
    )

    private fun greenDays(count: Int): List<ReadinessHistoryDay> = (0 until count).map {
        readinessDay(weekStart.plusDays(it.toLong()))
    }

    private fun redDays(count: Int): List<ReadinessHistoryDay> = (0 until count).map {
        readinessDay(weekStart.plusDays(it.toLong()), level = ReadinessLevel.RED, score = 30)
    }

    private fun readinessDay(date: LocalDate, level: ReadinessLevel = ReadinessLevel.GREEN, score: Int = 72) =
        ReadinessHistoryDay(
            date = date,
            score = score,
            level = level,
            confidence = DataConfidence.HIGH,
            hasCheckIn = true,
        )

    private fun sleepNights(minutes: List<Int>): List<DailyMetrics> =
        minutes.mapIndexed { index, value -> metrics(weekStart.plusDays(index.toLong()), value) }

    private fun metrics(date: LocalDate, sleepMin: Int) = DailyMetrics(date = date, sleepDurationMin = sleepMin)

    private fun checkIn(date: LocalDate, illness: Boolean) = DailyCheckIn(
        date = date,
        sleepQuality = 3,
        fatigue = 2,
        soreness = 1,
        stress = 2,
        illnessOrInjury = illness,
    )

    private fun within(value: Double) = org.assertj.core.data.Offset.offset(value)
}
