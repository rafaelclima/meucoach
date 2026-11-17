package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.calibration.CalibrationCalculator
import com.meucoach.core.domain.calibration.CalibrationInput
import com.meucoach.core.domain.calibration.CalibrationPerformance
import com.meucoach.core.domain.insights.ActivityInsightRules
import com.meucoach.core.domain.insights.ActivityInsightsCalculator
import com.meucoach.core.model.ActivityLap
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Sex
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class ExplanationCatalogTest {
    @Test
    fun `catalogs the rules used by the recommender`() {
        val ids = listOf(
            "R-ZONE-01",
            "R-DATA-01",
            "R-READY-01",
            "R-READY-03",
            "R-READY-04",
            "R-LOAD-03",
            "R-LOAD-04",
            "R-PLAN-02",
            "R-PLAN-03",
            "R-PLAN-04",
            "R-PLAN-05",
            "R-PLAN-06",
            "R-PLAN-07",
            "R-PLAN-08",
            "R-CAL-02",
            "R-SAFE-01",
            "R-PLAN-01",
            "R-PLAN-09",
            "R-PLAN-10",
            "R-PLAN-11",
            "R-PRESC-01",
            "R-PRESC-02",
            "R-PRESC-03",
            "R-PRESC-04",
            "R-RACE-01",
            "R-RACE-02",
            "R-RACE-03",
            "R-LVL-01",
            "R-LVL-02",
            "R-PER-01",
            "R-PER-02",
            "R-PER-03",
            "R-PER-04",
            "R-FEEDBACK-01",
            "R-REVIEW-01",
            "R-RACE-04",
            "R-READY-06",
            "R-ACT-01",
            "R-ACT-02",
            "R-CAL-01",
        )

        ids.forEach { id ->
            assertThat(ExplanationCatalog.hasRule(id)).describedAs(id).isTrue()
            assertThat(ExplanationCatalog.rule(id).title).isNotEqualTo(id)
        }
    }

    @Test
    fun `catalogs the evidence used by the recommender`() {
        val ids = listOf(
            "EV-SEILER-2010",
            "EV-DANIELS-2014",
            "EV-RIEGEL-1981",
            "EV-SAW-2016",
            "EV-GABBETT-2016",
            "EV-IMPELLIZZERI-2020",
            "EV-ELY-2007",
            "EV-FRIEL-2018",
            "EV-PLEWS-2013",
            "EV-BUCHHEIT-2014",
            "EV-WALSH-2021",
            "EV-WATSON-2015",
            "EV-BOSQUET-2007",
            "EV-MUJIKA-2003",
            "EV-HALSON-2014",
            "EV-ISSURIN-2010",
            "EV-PFITZINGER-2008",
            "EV-ABBISS-2008",
            "EV-FOSTER-2001",
        )

        ids.forEach { id ->
            assertThat(ExplanationCatalog.hasEvidence(id)).describedAs(id).isTrue()
            assertThat(ExplanationCatalog.evidence(id).source).isNotBlank()
        }
    }

    @Test
    fun `catalogs the ids emitted by insights and calibration`() {
        val insights = ActivityInsightsCalculator().calculate(
            listOf(
                ActivityLap(index = 0, durationSec = 360, distanceM = 1_000.0, averageHeartRate = 150),
                ActivityLap(index = 1, durationSec = 360, distanceM = 1_000.0, averageHeartRate = 160),
            ),
        )
        assertThat(insights).isNotEmpty()
        insights.forEach { insight ->
            assertThat(ExplanationCatalog.hasRule(insight.ruleId)).describedAs(insight.ruleId).isTrue()
            insight.evidenceIds.forEach { id ->
                assertThat(ExplanationCatalog.hasEvidence(id)).describedAs(id).isTrue()
            }
        }
        assertThat(ExplanationCatalog.hasRule(ActivityInsightRules.PACING)).isTrue()
        assertThat(ExplanationCatalog.hasRule(ActivityInsightRules.HEART_RATE_DRIFT)).isTrue()

        val today = LocalDate.of(2026, 10, 30)
        val profile = AthleteProfile(
            id = "athlete-1",
            birthDate = LocalDate.of(1988, 1, 1),
            sex = Sex.MALE,
            restingHeartRate = 58,
            maxHeartRateObserved = 187,
            timezone = "America/Sao_Paulo",
        )
        val tierA = CalibrationCalculator.calibrate(
            CalibrationInput(
                profile = profile,
                today = today,
                performance = CalibrationPerformance(
                    distanceM = 5_000.0,
                    durationSec = 1_859,
                    date = LocalDate.of(2026, 10, 24),
                    averageHeartRate = 181,
                ),
            ),
        )
        val tierC = CalibrationCalculator.calibrate(CalibrationInput(profile = profile, today = today))
        listOf(tierA, tierC).forEach { result ->
            assertThat(ExplanationCatalog.hasRule(result.ruleId)).describedAs(result.ruleId).isTrue()
            result.evidenceIds.forEach { id ->
                assertThat(ExplanationCatalog.hasEvidence(id)).describedAs(id).isTrue()
            }
        }
    }

    @Test
    fun `keeps the raw id when an entry is not catalogued`() {
        assertThat(ExplanationCatalog.rule("R-UNKNOWN")).isEqualTo(
            RuleExplanation(id = "R-UNKNOWN", title = "R-UNKNOWN"),
        )
        assertThat(ExplanationCatalog.evidence("EV-UNKNOWN")).isEqualTo(
            EvidenceExplanation(id = "EV-UNKNOWN", title = "EV-UNKNOWN", source = ""),
        )
    }
}
