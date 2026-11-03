package com.meucoach.core.domain.story

import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.zones.HrZoneDistribution
import com.meucoach.core.domain.zones.HrZoneDuration
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.HeartRateZone
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class StoryVerdictCalculatorTest {
    @Test
    fun `marks an easy run inside Z2 as on target`() {
        val verdict = StoryVerdictCalculator.calculate(
            session = session(WorkoutType.EASY),
            distribution = distribution(z1 = 100, z2 = 900, z3 = 0, z4 = 0, z5 = 0),
            lthrBpm = LTHR,
            efforts = listOf(
                StoryKmEffort(km = 1, averageHeartRateBpm = 150),
                StoryKmEffort(km = 2, averageHeartRateBpm = 152),
            ),
            nextSession = session(WorkoutType.EASY, date = DATE.plusDays(1), durationMin = 40),
        )

        assertThat(verdict.tone).isEqualTo(StoryVerdictTone.ON_TARGET)
        assertThat(verdict.headline).isEqualTo("Fácil bem feito")
        assertThat(verdict.proof).isEqualTo("90% no alvo")
        assertThat(verdict.coachLine).isEqualTo("Fácil bem feito. Amanhã: 40 min leve.")
        assertThat(verdict.onTargetPercent).isEqualTo(90)
        assertThat(verdict.targetZones).containsExactly(HeartRateZone.Z2)
        assertThat(verdict.stamps.map { it.tone }).containsOnly(StoryVerdictTone.ON_TARGET)
        assertThat(verdict.confidence).isEqualTo(DataConfidence.HIGH)
        assertThat(verdict.ruleIds).containsExactly("R-ZONE-01", "R-PLAN-06")
        assertThat(verdict.evidenceIds).containsExactly("EV-FRIEL-2018", "EV-SEILER-2010")
    }

    @Test
    fun `flags a strong easy day without blaming the runner`() {
        val verdict = StoryVerdictCalculator.calculate(
            session = session(WorkoutType.EASY),
            distribution = distribution(z1 = 60, z2 = 140, z3 = 300, z4 = 400, z5 = 100),
            lthrBpm = LTHR,
        )

        assertThat(verdict.tone).isEqualTo(StoryVerdictTone.TOO_HARD)
        assertThat(verdict.headline).isEqualTo("Forte demais hoje")
        assertThat(verdict.proof).isEqualTo("14% no alvo")
        assertThat(verdict.coachLine).startsWith("Forte demais hoje.")
        assertThat(verdict.coachLine.lowercase()).doesNotContain("errado", "culpa", "falhou")
    }

    @Test
    fun `flags a light easy day when most time stays below Z2`() {
        val verdict = StoryVerdictCalculator.calculate(
            session = session(WorkoutType.EASY),
            distribution = distribution(z1 = 800, z2 = 200, z3 = 0, z4 = 0, z5 = 0),
            lthrBpm = LTHR,
        )

        assertThat(verdict.tone).isEqualTo(StoryVerdictTone.TOO_EASY)
        assertThat(verdict.headline).isEqualTo("Leve demais hoje")
        assertThat(verdict.proof).isEqualTo("20% no alvo")
    }

    @Test
    fun `stamps kilometers above, on and below the target zone`() {
        val verdict = StoryVerdictCalculator.calculate(
            session = session(WorkoutType.EASY),
            distribution = null,
            lthrBpm = LTHR,
            efforts = listOf(
                StoryKmEffort(km = 1, averageHeartRateBpm = 140),
                StoryKmEffort(km = 2, averageHeartRateBpm = 150),
                StoryKmEffort(km = 3, averageHeartRateBpm = 170),
                StoryKmEffort(km = 4, averageHeartRateBpm = null),
            ),
        )

        assertThat(verdict.stamps.map { it.km }).containsExactly(1, 2, 3, 4)
        assertThat(verdict.stamps.map { it.tone }).containsExactly(
            StoryVerdictTone.TOO_EASY,
            StoryVerdictTone.ON_TARGET,
            StoryVerdictTone.TOO_HARD,
            null,
        )
        assertThat(verdict.onTargetPercent).isEqualTo(33)
        assertThat(verdict.tone).isEqualTo(StoryVerdictTone.TOO_EASY)
        assertThat(verdict.confidence).isEqualTo(DataConfidence.MEDIUM)
    }

    @Test
    fun `uses a long run target of Z1 to Z2`() {
        val verdict = StoryVerdictCalculator.calculate(
            session = session(WorkoutType.LONG),
            distribution = distribution(z1 = 400, z2 = 600, z3 = 0, z4 = 0, z5 = 0),
            lthrBpm = LTHR,
        )

        assertThat(verdict.targetZones).containsExactly(HeartRateZone.Z1, HeartRateZone.Z2)
        assertThat(verdict.tone).isEqualTo(StoryVerdictTone.ON_TARGET)
        assertThat(verdict.headline).isEqualTo("Longão bem feito")
    }

    @Test
    fun `uses Z4 and Z5 as the quality target`() {
        val verdict = StoryVerdictCalculator.calculate(
            session = session(WorkoutType.QUALITY),
            distribution = distribution(z1 = 0, z2 = 100, z3 = 100, z4 = 700, z5 = 100),
            lthrBpm = LTHR,
        )

        assertThat(verdict.targetZones).containsExactly(HeartRateZone.Z4, HeartRateZone.Z5)
        assertThat(verdict.tone).isEqualTo(StoryVerdictTone.ON_TARGET)
        assertThat(verdict.headline).isEqualTo("Qualidade completa")
    }

    @Test
    fun `falls back honestly without a planned session`() {
        val verdict = StoryVerdictCalculator.calculate(
            session = null,
            distribution = distribution(z1 = 200, z2 = 800, z3 = 0, z4 = 0, z5 = 0),
            lthrBpm = LTHR,
        )

        assertThat(verdict.tone).isNull()
        assertThat(verdict.headline).isEqualTo("Corrida feita")
        assertThat(verdict.coachLine).isEqualTo("Cada corrida conta.")
        assertThat(verdict.proof).isNull()
        assertThat(verdict.onTargetPercent).isNull()
        assertThat(verdict.stamps).isEmpty()
        assertThat(verdict.ruleIds).isEmpty()
        assertThat(verdict.evidenceIds).isEmpty()
        assertThat(verdict.confidence).isEqualTo(DataConfidence.LOW)
        assertThat(listOfNotNull(verdict.headline, verdict.proof, verdict.coachLine).joinToString(" ").lowercase())
            .doesNotContain("plano", "calibra", "lthr", "alvo", "sem fc")
    }

    @Test
    fun `falls back honestly without calibration`() {
        val verdict = StoryVerdictCalculator.calculate(
            session = session(WorkoutType.EASY),
            distribution = null,
            lthrBpm = null,
        )

        assertThat(verdict.tone).isNull()
        assertThat(verdict.coachLine).isEqualTo("Cada corrida conta.")
        assertThat(verdict.targetZones).isEmpty()
    }

    @Test
    fun `does not invent a verdict without heart rate`() {
        val verdict = StoryVerdictCalculator.calculate(
            session = session(WorkoutType.EASY),
            distribution = null,
            lthrBpm = LTHR,
            efforts = listOf(
                StoryKmEffort(km = 1, averageHeartRateBpm = null),
                StoryKmEffort(km = 2, averageHeartRateBpm = null),
            ),
        )

        assertThat(verdict.tone).isNull()
        assertThat(verdict.headline).isEqualTo("Corrida feita")
        assertThat(verdict.proof).isNull()
        assertThat(verdict.targetZones).containsExactly(HeartRateZone.Z2)
        assertThat(verdict.stamps.map { it.tone }).containsOnlyNulls()
    }

    @Test
    fun `does not judge a rest day`() {
        val verdict = StoryVerdictCalculator.calculate(
            session = session(WorkoutType.REST),
            distribution = distribution(z1 = 0, z2 = 1000, z3 = 0, z4 = 0, z5 = 0),
            lthrBpm = LTHR,
        )

        assertThat(verdict.tone).isNull()
        assertThat(verdict.targetZones).isEmpty()
        assertThat(verdict.coachLine).isEqualTo("Cada corrida conta.")
    }

    @Test
    fun `flags a mostly hard run from the km stamps when the distribution is missing`() {
        val verdict = StoryVerdictCalculator.calculate(
            session = session(WorkoutType.EASY),
            distribution = null,
            lthrBpm = LTHR,
            efforts = listOf(
                StoryKmEffort(km = 1, averageHeartRateBpm = 170),
                StoryKmEffort(km = 2, averageHeartRateBpm = 172),
                StoryKmEffort(km = 3, averageHeartRateBpm = 140),
            ),
        )

        assertThat(verdict.tone).isEqualTo(StoryVerdictTone.TOO_HARD)
        assertThat(verdict.headline).isEqualTo("Forte demais hoje")
        assertThat(verdict.onTargetPercent).isEqualTo(0)
    }

    @Test
    fun `uses the quality headline when the quality session stays below target`() {
        val verdict = StoryVerdictCalculator.calculate(
            session = session(WorkoutType.QUALITY),
            distribution = distribution(z1 = 200, z2 = 500, z3 = 300, z4 = 0, z5 = 0),
            lthrBpm = LTHR,
        )

        assertThat(verdict.tone).isEqualTo(StoryVerdictTone.TOO_EASY)
        assertThat(verdict.headline).isEqualTo("Qualidade abaixo do alvo")
        assertThat(verdict.proof).isEqualTo("0% no alvo")
    }

    @Test
    fun `keeps the rule and evidence identifiers out of the visible text`() {
        val verdict = StoryVerdictCalculator.calculate(
            session = session(WorkoutType.QUALITY),
            distribution = distribution(z1 = 0, z2 = 100, z3 = 100, z4 = 800, z5 = 0),
            lthrBpm = LTHR,
        )

        val visible = listOfNotNull(verdict.headline, verdict.proof, verdict.coachLine).joinToString(" ")
        assertThat(visible).doesNotContain("R-ZONE", "R-PLAN", "EV-")
    }

    @Test
    fun `says next instead of tomorrow for a later session`() {
        val verdict = StoryVerdictCalculator.calculate(
            session = session(WorkoutType.EASY),
            distribution = distribution(z1 = 0, z2 = 1000, z3 = 0, z4 = 0, z5 = 0),
            lthrBpm = LTHR,
            nextSession = session(WorkoutType.LONG, date = DATE.plusDays(3), durationMin = 60),
        )

        assertThat(verdict.coachLine).isEqualTo("Fácil bem feito. Próximo: longão de 60 min.")
    }

    private fun session(type: WorkoutType, date: LocalDate = DATE, durationMin: Int? = 40) = PlannedSession(
        date = date,
        type = type,
        title = "Sessão de teste",
        durationMin = durationMin,
        rationale = "Teste determinístico.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-FRIEL-2018"),
        status = SessionStatus.DONE,
    )

    /** Allocates the remainder to Z1 so the distribution always adds up. */
    private fun distribution(z1: Int, z2: Int, z3: Int, z4: Int, z5: Int): HrZoneDistribution = HrZoneDistribution(
        zones = listOf(
            HrZoneDuration(HeartRateZone.Z1, z1),
            HrZoneDuration(HeartRateZone.Z2, z2),
            HrZoneDuration(HeartRateZone.Z3, z3),
            HrZoneDuration(HeartRateZone.Z4, z4),
            HrZoneDuration(HeartRateZone.Z5, z5),
        ),
        totalSec = z1 + z2 + z3 + z4 + z5,
    )

    private companion object {
        const val LTHR = 180
        val DATE: LocalDate = LocalDate.parse("2026-10-24")
    }
}
