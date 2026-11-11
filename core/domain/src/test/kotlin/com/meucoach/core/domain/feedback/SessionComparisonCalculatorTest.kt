package com.meucoach.core.domain.feedback

import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.HeartRateTarget
import com.meucoach.core.domain.prescription.PaceTarget
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Distance
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

class SessionComparisonCalculatorTest {
    @Test
    fun `compares a linked run inside every target`() {
        val comparison = compare(
            plan = plan(),
            activity = activity(durationSec = 2_400, distanceM = 6_400.0, heartRate = 148),
            rpe = 4,
        )

        assertThat(comparison.kind).isEqualTo(SessionComparisonKind.LINKED)
        assertThat(comparison.durationPercentOfPlan).isEqualTo(100)
        assertThat(comparison.durationVerdict).isEqualTo(ComparisonVerdict.WITHIN)
        assertThat(comparison.distancePercentOfPlan).isEqualTo(98)
        assertThat(comparison.distanceVerdict).isEqualTo(ComparisonVerdict.WITHIN)
        assertThat(comparison.paceVerdict).isEqualTo(ComparisonVerdict.WITHIN)
        assertThat(comparison.heartRateVerdict).isEqualTo(ComparisonVerdict.WITHIN)
        assertThat(comparison.rpeVerdict).isEqualTo(ComparisonVerdict.WITHIN)
        assertThat(comparison.confidence).isEqualTo(DataConfidence.HIGH)
        assertThat(comparison.ruleIds).containsExactly(SessionComparisonCalculator.RULE_ID)
        assertThat(comparison.evidenceIds).containsExactly("EV-DANIELS-2014", "EV-FRIEL-2018")
        assertThat(comparison.hasComparableTarget).isTrue()
    }

    @Test
    fun `marks every metric above when the run was longer faster and harder`() {
        val comparison = compare(
            plan = plan(),
            activity = activity(durationSec = 2_700, distanceM = 8_100.0, heartRate = 165),
            rpe = 6,
        )

        assertThat(comparison.durationPercentOfPlan).isEqualTo(113)
        assertThat(comparison.durationVerdict).isEqualTo(ComparisonVerdict.ABOVE)
        assertThat(comparison.distanceVerdict).isEqualTo(ComparisonVerdict.ABOVE)
        assertThat(comparison.paceVerdict).isEqualTo(ComparisonVerdict.ABOVE)
        assertThat(comparison.heartRateVerdict).isEqualTo(ComparisonVerdict.ABOVE)
        assertThat(comparison.rpeVerdict).isEqualTo(ComparisonVerdict.ABOVE)
    }

    @Test
    fun `marks every metric below when the run was shorter slower and lighter`() {
        val comparison = compare(
            plan = plan(),
            activity = activity(durationSec = 1_800, distanceM = 4_300.0, heartRate = 128),
            rpe = 2,
        )

        assertThat(comparison.durationPercentOfPlan).isEqualTo(75)
        assertThat(comparison.durationVerdict).isEqualTo(ComparisonVerdict.BELOW)
        assertThat(comparison.distanceVerdict).isEqualTo(ComparisonVerdict.BELOW)
        assertThat(comparison.paceVerdict).isEqualTo(ComparisonVerdict.BELOW)
        assertThat(comparison.heartRateVerdict).isEqualTo(ComparisonVerdict.BELOW)
        assertThat(comparison.rpeVerdict).isEqualTo(ComparisonVerdict.BELOW)
    }

    @Test
    fun `keeps the ten percent tolerance inclusive`() {
        assertThat(compare(plan = plan(), activity = activity(durationSec = 2_160)).durationVerdict)
            .isEqualTo(ComparisonVerdict.WITHIN)
        assertThat(compare(plan = plan(), activity = activity(durationSec = 2_640)).durationVerdict)
            .isEqualTo(ComparisonVerdict.WITHIN)
        assertThat(compare(plan = plan(), activity = activity(durationSec = 2_652)).durationVerdict)
            .isEqualTo(ComparisonVerdict.ABOVE)
        assertThat(compare(plan = plan(), activity = activity(durationSec = 2_142)).durationVerdict)
            .isEqualTo(ComparisonVerdict.BELOW)
    }

    @Test
    fun `compares heart rate without an upper bound conservatively`() {
        val openEnded = plan(targets = PrescriptionTargets(heartRate = HeartRateTarget(minBpm = 140, maxBpm = null)))

        assertThat(compare(plan = openEnded, activity = activity(heartRate = 150)).heartRateVerdict)
            .isEqualTo(ComparisonVerdict.WITHIN)
        assertThat(compare(plan = openEnded, activity = activity(heartRate = 180)).heartRateVerdict)
            .isEqualTo(ComparisonVerdict.WITHIN)
        assertThat(compare(plan = openEnded, activity = activity(heartRate = 130)).heartRateVerdict)
            .isEqualTo(ComparisonVerdict.BELOW)
    }

    @Test
    fun `never invents a verdict when a side is missing`() {
        val comparison = compare(
            plan = ComparisonPlan(title = "Fácil", type = WorkoutType.EASY, durationMin = null),
            activity = activity(heartRate = null),
            rpe = null,
        )

        assertThat(comparison.kind).isEqualTo(SessionComparisonKind.LINKED)
        assertThat(comparison.durationVerdict).isNull()
        assertThat(comparison.distanceVerdict).isNull()
        assertThat(comparison.paceVerdict).isNull()
        assertThat(comparison.heartRateVerdict).isNull()
        assertThat(comparison.rpeVerdict).isNull()
        assertThat(comparison.confidence).isEqualTo(DataConfidence.LOW)
        assertThat(comparison.hasComparableTarget).isFalse()
    }

    @Test
    fun `derives pace from distance and duration when the file omits it`() {
        val comparison = compare(
            plan = plan(),
            activity = activity(durationSec = 1_800, distanceM = 5_000.0, averagePace = null),
        )

        assertThat(comparison.actualAveragePace).isEqualTo(Pace.fromSecondsPerKm(360))
        assertThat(comparison.paceVerdict).isEqualTo(ComparisonVerdict.WITHIN)
    }

    @Test
    fun `treats a run without a linked plan as free run`() {
        val comparison = SessionComparisonCalculator.compare(
            linkedPlan = null,
            activity = activity(durationSec = 1_800, distanceM = 5_000.0, heartRate = 160),
            rpe = 7,
        )

        assertThat(comparison.kind).isEqualTo(SessionComparisonKind.FREE_RUN)
        assertThat(comparison.plan).isNull()
        assertThat(comparison.actualDurationSec).isEqualTo(1_800)
        assertThat(comparison.actualDistanceM).isEqualTo(5_000.0)
        assertThat(comparison.actualRpe).isEqualTo(7)
        assertThat(comparison.durationVerdict).isNull()
        assertThat(comparison.paceVerdict).isNull()
        assertThat(comparison.heartRateVerdict).isNull()
        assertThat(comparison.ruleIds).isEmpty()
        assertThat(comparison.evidenceIds).isEmpty()
        assertThat(comparison.confidence).isEqualTo(DataConfidence.LOW)
    }

    @Test
    fun `cites the product rule and the pace and zone evidence`() {
        assertThat(SessionComparisonCalculator.RULE_ID).isEqualTo("R-FEEDBACK-01")
        assertThat(SessionComparisonCalculator.DURATION_TOLERANCE_PERCENT).isEqualTo(10)
        assertThat(SessionComparisonCalculator.DISTANCE_TOLERANCE_PERCENT).isEqualTo(10)
        assertThat(SessionComparisonCalculator.RPE_TOLERANCE).isEqualTo(1)
    }

    @Test
    fun `rejects a rest day as a comparison plan`() {
        assertThat(
            ComparisonPlan.from(
                PlannedSession(
                    date = LocalDate.parse("2026-11-10"),
                    type = WorkoutType.REST,
                    title = "Descanso",
                    durationMin = null,
                    rationale = "Descanso",
                    ruleIds = listOf("R-PLAN-02"),
                    evidenceIds = listOf("EV-SEILER-2010"),
                    status = SessionStatus.PLANNED,
                ),
            ),
        ).isNull()

        assertThat(
            ComparisonPlan.from(
                prescription(type = WorkoutType.REST),
            ),
        ).isNull()
    }

    @Test
    fun `builds the plan from the persisted prescription`() {
        val plan = ComparisonPlan.from(prescription())

        assertThat(plan).isNotNull
        assertThat(plan?.title).isEqualTo("Fácil 40 min")
        assertThat(plan?.type).isEqualTo(WorkoutType.EASY)
        assertThat(plan?.durationMin).isEqualTo(40)
        assertThat(plan?.targets?.pace).isEqualTo(paceTarget())
    }

    @Test
    fun `builds the plan from a planned session when no prescription exists`() {
        val plan = ComparisonPlan.from(
            PlannedSession(
                date = LocalDate.parse("2026-11-10"),
                type = WorkoutType.EASY,
                title = "Fácil 40 min",
                durationMin = 40,
                rationale = "Base",
                ruleIds = listOf("R-PLAN-02"),
                evidenceIds = listOf("EV-SEILER-2010"),
                targets = targets(),
            ),
        )

        assertThat(plan?.title).isEqualTo("Fácil 40 min")
        assertThat(plan?.durationMin).isEqualTo(40)
        assertThat(plan?.targets?.heartRate).isEqualTo(HeartRateTarget(140, 155))
    }

    @Test
    fun `rejects a linked comparison without the plan it fulfilled`() {
        assertThatThrownBy {
            SessionComparison(
                kind = SessionComparisonKind.LINKED,
                plan = null,
                actualDurationSec = 1_800,
                actualDistanceM = 5_000.0,
                actualAveragePace = null,
                actualAverageHeartRate = null,
                actualRpe = null,
                durationPercentOfPlan = null,
                durationVerdict = null,
                distancePercentOfPlan = null,
                distanceVerdict = null,
                paceVerdict = null,
                heartRateVerdict = null,
                rpeVerdict = null,
                ruleIds = emptyList(),
                evidenceIds = emptyList(),
                confidence = DataConfidence.LOW,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun compare(plan: ComparisonPlan?, activity: ActivitySummary, rpe: Int? = null): SessionComparison =
        SessionComparisonCalculator.compare(plan, activity, rpe)

    private fun plan(durationMin: Int? = 40, targets: PrescriptionTargets = targets()) =
        ComparisonPlan(title = "Fácil 40 min", type = WorkoutType.EASY, durationMin = durationMin, targets = targets)

    private fun targets() = PrescriptionTargets(
        pace = paceTarget(),
        heartRate = HeartRateTarget(140, 155),
        zone = "Z2",
        distanceKm = 6.5,
        rpe = 4,
    )

    private fun paceTarget() = PaceTarget(
        fast = Pace.fromSecondsPerKm(360),
        slow = Pace.fromSecondsPerKm(390),
    )

    private fun activity(
        durationSec: Int = 2_400,
        distanceM: Double = 7_000.0,
        heartRate: Int? = 148,
        averagePace: Pace? = Pace.fromSecondsPerKm(
            if (distanceM > 0.0) (durationSec / (distanceM / 1_000.0)).roundToInt() else 0,
        ),
    ) = ActivitySummary(
        id = "activity-1",
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-11-10T10:00:00Z"),
        durationSec = durationSec,
        distance = Distance(distanceM),
        averageHeartRate = heartRate,
        averagePace = averagePace,
    )

    private fun prescription(type: WorkoutType = WorkoutType.EASY) = DailyPrescription(
        date = LocalDate.parse("2026-11-10"),
        plannedSessionId = "session-1",
        plannedType = WorkoutType.EASY,
        plannedTitle = "Fácil 40 min",
        plannedDurationMin = 40,
        prescribedType = type,
        title = "Fácil 40 min",
        prescribedDurationMin = if (type == WorkoutType.REST) null else 40,
        targets = if (type == WorkoutType.REST) PrescriptionTargets() else targets(),
        adjustment = PrescriptionAdjustment.KEEP,
        rationale = "Plano mantido",
        ruleIds = listOf("R-PRESC-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        confidence = DataConfidence.HIGH,
        engineVersion = "daily-prescription-v2",
        inputsHash = "hash",
    )
}
