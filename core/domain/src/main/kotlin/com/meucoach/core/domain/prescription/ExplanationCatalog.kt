package com.meucoach.core.domain.prescription

data class RuleExplanation(val id: String, val title: String)

data class EvidenceExplanation(val id: String, val title: String, val source: String)

/**
 * Human-readable labels for the IDs cited by the deterministic engine.
 * Titles are short paraphrases of docs/SPORTS_SCIENCE.md; the raw ID is always
 * preserved so the explanation remains auditable even for an uncatalogued ID.
 */
object ExplanationCatalog {
    private val rules = mapOf(
        "R-ZONE-01" to RuleExplanation("R-ZONE-01", "Zonas por limiar"),
        "R-DATA-01" to RuleExplanation("R-DATA-01", "Dado ausente não vira zero"),
        "R-READY-03" to RuleExplanation("R-READY-03", "Prontidão limita a intensidade"),
        "R-READY-04" to RuleExplanation("R-READY-04", "Sono curto bloqueia qualidade"),
        "R-LOAD-03" to RuleExplanation("R-LOAD-03", "ACWR é alerta, não meta"),
        "R-PLAN-02" to RuleExplanation("R-PLAN-02", "Máximo de 2 qualidades por semana"),
        "R-PLAN-06" to RuleExplanation("R-PLAN-06", "80% do tempo fácil"),
        "R-PLAN-07" to RuleExplanation("R-PLAN-07", "Ajuste conservador por calor"),
        "R-PLAN-08" to RuleExplanation("R-PLAN-08", "Esteira é válida no calor"),
        "R-CAL-02" to RuleExplanation("R-CAL-02", "Sem calibração, prescrever por RPE"),
        "R-SAFE-01" to RuleExplanation("R-SAFE-01", "Sinal de alerta interrompe o treino"),
    )

    private val evidence = mapOf(
        "EV-SEILER-2010" to EvidenceExplanation(
            "EV-SEILER-2010",
            "Distribuição de intensidade",
            "Seiler, IJSPP 2010",
        ),
        "EV-DANIELS-2014" to EvidenceExplanation(
            "EV-DANIELS-2014",
            "Paces e economia de corrida",
            "Daniels, Daniels' Running Formula, 2014",
        ),
        "EV-SAW-2016" to EvidenceExplanation(
            "EV-SAW-2016",
            "Check-in subjetivo",
            "Saw et al., BJSM 2016",
        ),
        "EV-GABBETT-2016" to EvidenceExplanation(
            "EV-GABBETT-2016",
            "Carga aguda:crônica",
            "Gabbett, BJSM 2016",
        ),
        "EV-ELY-2007" to EvidenceExplanation(
            "EV-ELY-2007",
            "Impacto do clima",
            "Ely et al., MSSE 2007",
        ),
        "EV-FRIEL-2018" to EvidenceExplanation(
            "EV-FRIEL-2018",
            "Zonas por LTHR",
            "Friel, The Triathlete's Training Bible, 2018",
        ),
        "EV-PFITZINGER-2008" to EvidenceExplanation(
            "EV-PFITZINGER-2008",
            "Longão e volume",
            "Pfitzinger & Douglas, Advanced Marathoning, 2008",
        ),
        "EV-BOSQUET-2007" to EvidenceExplanation(
            "EV-BOSQUET-2007",
            "Taper",
            "Bosquet et al., MSSE 2007",
        ),
        "EV-MUJIKA-2003" to EvidenceExplanation(
            "EV-MUJIKA-2003",
            "Taper",
            "Mujika & Padilla, MSSE 2003",
        ),
        "EV-ISSURIN-2010" to EvidenceExplanation(
            "EV-ISSURIN-2010",
            "Periodização em blocos",
            "Issurin, Sports Med 2010",
        ),
    )

    fun rule(id: String): RuleExplanation = rules[id] ?: RuleExplanation(id, id)

    fun evidence(id: String): EvidenceExplanation = evidence[id] ?: EvidenceExplanation(id, id, "")

    fun hasRule(id: String): Boolean = id in rules

    fun hasEvidence(id: String): Boolean = id in evidence
}
