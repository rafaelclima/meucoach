package com.meucoach.app.ui.activitydetail

import com.meucoach.core.domain.feedback.ComparisonPlan
import com.meucoach.core.domain.feedback.SessionComparison
import com.meucoach.core.domain.feedback.SessionComparisonCalculator
import com.meucoach.core.domain.prescription.HeartRateTarget
import com.meucoach.core.domain.prescription.PaceTarget
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ActivitySummary
import com.meucoach.core.model.Distance
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant
import kotlin.math.roundToInt

class ActivityComparisonFormatTest {
    @Test
    fun `maps every available metric to a row`() {
        val rows = comparisonRows(comparison())

        assertThat(rows.map { it.label }).containsExactly("Duração", "Distância", "Ritmo", "FC média", "Esforço")
        assertThat(rows.first { it.label == "Duração" }.planned).isEqualTo("30 min")
        assertThat(rows.first { it.label == "Duração" }.actual).isEqualTo("30 min")
        assertThat(rows.first { it.label == "Ritmo" }.planned).isEqualTo("5:50–6:30/km")
        assertThat(rows.first { it.label == "Ritmo" }.actual).isEqualTo("6:00/km")
        assertThat(rows.first { it.label == "FC média" }.planned).isEqualTo("140–155 bpm")
        assertThat(rows.first { it.label == "FC média" }.actual).isEqualTo("150 bpm")
        assertThat(rows.first { it.label == "Esforço" }.actual).isEqualTo("RPE 5")
    }

    @Test
    fun `omits a row when one side is missing`() {
        val rows = comparisonRows(
            comparison(
                plan = ComparisonPlan(
                    title = "Fácil",
                    type = WorkoutType.EASY,
                    durationMin = 30,
                    targets = PrescriptionTargets(),
                ),
                activity = activity(heartRate = null, averagePace = null, distanceM = 0.0),
                rpe = null,
            ),
        )

        assertThat(rows.map { it.label }).containsExactly("Duração")
    }

    @Test
    fun `shows the reported effort even without an RPE target`() {
        val rows = comparisonRows(
            comparison(
                plan = ComparisonPlan(
                    title = "Fácil",
                    type = WorkoutType.EASY,
                    durationMin = 30,
                    targets = PrescriptionTargets(),
                ),
                rpe = 7,
            ),
        )

        val effort = rows.first { it.label == "Esforço" }
        assertThat(effort.planned).isEqualTo("—")
        assertThat(effort.actual).isEqualTo("RPE 7")
    }

    @Test
    fun `describes differences without judging them`() {
        val notes = comparisonNotes(
            comparison(
                activity = activity(durationSec = 2_400, distanceM = 6_200.0, heartRate = 168),
                rpe = 8,
            ),
        )

        assertThat(notes).containsExactly(
            "Duração acima do planejado (133%)",
            "Distância acima do planejado (113%)",
            "FC média acima da faixa-alvo",
            "Esforço percebido acima do previsto",
        )
        assertThat(notes.joinToString(" ")).doesNotContain("bom", "ruim", "falhou")
    }

    @Test
    fun `summarises a run inside every target`() {
        assertThat(comparisonSummary(comparison())).isEqualTo("Tudo dentro do planejado.")
        assertThat(comparisonNotes(comparison())).isEmpty()
    }

    @Test
    fun `asks for more data instead of judging an empty comparison`() {
        val empty = comparison(
            plan = ComparisonPlan(title = "Fácil", type = WorkoutType.EASY, durationMin = null),
            activity = activity(heartRate = null, averagePace = null, distanceM = 0.0),
            rpe = null,
        )

        assertThat(comparisonSummary(empty)).isEqualTo("Sem alvos suficientes para comparar.")
    }

    @Test
    fun `summarises a free run with a dash for every missing value`() {
        val freeRun = comparison(
            plan = null,
            activity = activity(heartRate = null, averagePace = null, distanceM = 0.0),
            rpe = null,
        )

        assertThat(freeRunSummary(freeRun)).isEqualTo(
            "Duração 30 min · Distância — · Ritmo — · FC média —",
        )
        assertThat(comparisonSummary(freeRun)).isNull()
        assertThat(comparisonNotes(freeRun)).isEmpty()
    }

    @Test
    fun `shows the actual values of a free run when they exist`() {
        val freeRun = comparison(
            plan = null,
            activity = activity(durationSec = 1_740, distanceM = 5_200.0),
            rpe = null,
        )

        assertThat(freeRunSummary(freeRun)).isEqualTo(
            "Duração 29 min · Distância 5,20 km · Ritmo 5:35/km · FC média 150 bpm",
        )
    }

    @Test
    fun `adds the perceived effort to a free run when it was reported`() {
        val freeRun = comparison(plan = null, activity = activity(), rpe = 7)

        assertThat(freeRunSummary(freeRun)).isEqualTo(
            "Duração 30 min · Distância 5,00 km · Ritmo 6:00/km · FC média 150 bpm · Esforço RPE 7",
        )
    }

    private fun comparison(
        plan: ComparisonPlan? = ComparisonPlan(
            title = "Fácil 30 min",
            type = WorkoutType.EASY,
            durationMin = 30,
            targets = PrescriptionTargets(
                pace = PaceTarget(fast = Pace.fromSecondsPerKm(350), slow = Pace.fromSecondsPerKm(390)),
                heartRate = HeartRateTarget(140, 155),
                zone = "Z2",
                distanceKm = 5.5,
                rpe = 4,
            ),
        ),
        activity: ActivitySummary = activity(),
        rpe: Int? = 5,
    ): SessionComparison = SessionComparisonCalculator.compare(plan, activity, rpe)

    private fun activity(
        durationSec: Int = 1_800,
        distanceM: Double = 5_000.0,
        heartRate: Int? = 150,
        averagePace: Pace? = Pace.fromSecondsPerKm(
            if (distanceM > 0.0) (durationSec / (distanceM / 1_000.0)).roundToInt() else 0,
        ),
    ) = ActivitySummary(
        id = "activity-1",
        source = ActivitySource.FIT,
        sport = Sport.RUNNING,
        startTime = Instant.parse("2026-10-24T08:17:10Z"),
        durationSec = durationSec,
        distance = Distance(distanceM),
        averageHeartRate = heartRate,
        averagePace = averagePace?.takeIf { distanceM > 0.0 },
    )
}
