package com.meucoach.core.domain.prescription

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

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
            "R-PRESC-01",
            "R-PRESC-02",
            "R-PRESC-03",
            "R-PRESC-04",
            "R-RACE-01",
            "R-RACE-02",
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
            "EV-SAW-2016",
            "EV-GABBETT-2016",
            "EV-IMPELLIZZERI-2020",
            "EV-ELY-2007",
            "EV-FRIEL-2018",
            "EV-PLEWS-2013",
            "EV-BUCHHEIT-2014",
            "EV-WALSH-2021",
        )

        ids.forEach { id ->
            assertThat(ExplanationCatalog.hasEvidence(id)).describedAs(id).isTrue()
            assertThat(ExplanationCatalog.evidence(id).source).isNotBlank()
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
