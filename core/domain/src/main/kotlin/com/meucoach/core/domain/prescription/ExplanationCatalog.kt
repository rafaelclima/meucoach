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
        "R-ZONE-01" to RuleExplanation("R-ZONE-01", "Zonas pelo limite de esforço"),
        "R-DATA-01" to RuleExplanation("R-DATA-01", "Dado ausente não vira zero"),
        "R-READY-01" to RuleExplanation(
            "R-READY-01",
            "Prontidão combina check-in, sono, variabilidade e FC de repouso",
        ),
        "R-READY-03" to RuleExplanation("R-READY-03", "Prontidão limita a intensidade"),
        "R-READY-04" to RuleExplanation("R-READY-04", "Sono curto bloqueia treino forte"),
        "R-LOAD-03" to RuleExplanation("R-LOAD-03", "Carga subindo rápido é alerta, não meta"),
        "R-PLAN-02" to RuleExplanation("R-PLAN-02", "No máximo 2 treinos fortes por semana"),
        "R-PLAN-03" to RuleExplanation("R-PLAN-03", "Longão entre 25–30% do volume semanal"),
        "R-PLAN-04" to RuleExplanation("R-PLAN-04", "Semana de descarga a cada 4 semanas"),
        "R-PLAN-05" to RuleExplanation("R-PLAN-05", "Redução de volume antes da prova"),
        "R-PLAN-06" to RuleExplanation("R-PLAN-06", "80% do tempo em ritmo fácil"),
        "R-PLAN-07" to RuleExplanation("R-PLAN-07", "Calor pede ajuste conservador"),
        "R-PLAN-08" to RuleExplanation("R-PLAN-08", "Esteira vale como opção no calor"),
        "R-CAL-02" to RuleExplanation("R-CAL-02", "Sem calibração, prescrever pelo esforço percebido"),
        "R-SAFE-01" to RuleExplanation("R-SAFE-01", "Sinal de alerta interrompe o treino"),
        "R-PLAN-01" to RuleExplanation("R-PLAN-01", "Disponibilidade define os dias de treino"),
        "R-PLAN-09" to RuleExplanation("R-PLAN-09", "Mudança de perfil gera revisão sugerida, nunca automática"),
        "R-PLAN-10" to RuleExplanation(
            "R-PLAN-10",
            "Sem treino forte na semana da prova e corte conservador antes dela",
        ),
        "R-PRESC-01" to RuleExplanation("R-PRESC-01", "A prescrição do dia mantém o plano"),
        "R-PRESC-02" to RuleExplanation("R-PRESC-02", "Ajuste do dia reduz, nunca aumenta"),
        "R-PRESC-03" to RuleExplanation("R-PRESC-03", "Uma decisão por dia, a mesma em todas as telas"),
        "R-PRESC-04" to RuleExplanation("R-PRESC-04", "Iniciante não recebe treino forte no dia"),
        "R-RACE-01" to RuleExplanation(
            "R-RACE-01",
            "Prova concluída por corrida compatível, com correção",
        ),
        "R-RACE-02" to RuleExplanation(
            "R-RACE-02",
            "Ritmo e passagens de referência a partir da meta",
        ),
        "R-RACE-03" to RuleExplanation(
            "R-RACE-03",
            "Previsão de tempo por VDOT, com faixa e checagem de Riegel",
        ),
        "R-RACE-04" to RuleExplanation(
            "R-RACE-04",
            "Recuperação após a prova, sem treino forte e sem longão",
        ),
        "R-FEEDBACK-01" to RuleExplanation(
            "R-FEEDBACK-01",
            "Planejado × executado descreve a diferença, sem julgar",
        ),
        "R-REVIEW-01" to RuleExplanation(
            "R-REVIEW-01",
            "A semana é lida como fato, com dado ausente como ausente",
        ),
    )

    private val evidence = mapOf(
        "EV-SEILER-2010" to EvidenceExplanation(
            "EV-SEILER-2010",
            "Distribuição de intensidade",
            "Seiler, IJSPP 2010",
        ),
        "EV-DANIELS-2014" to EvidenceExplanation(
            "EV-DANIELS-2014",
            "Ritmos e economia de corrida",
            "Daniels, Daniels' Running Formula, 2014",
        ),
        "EV-RIEGEL-1981" to EvidenceExplanation(
            "EV-RIEGEL-1981",
            "Predição de prova",
            "Riegel, American Scientist 1981",
        ),
        "EV-SAW-2016" to EvidenceExplanation(
            "EV-SAW-2016",
            "Check-in subjetivo",
            "Saw et al., BJSM 2016",
        ),
        "EV-GABBETT-2016" to EvidenceExplanation(
            "EV-GABBETT-2016",
            "Carga recente vs. acumulada",
            "Gabbett, BJSM 2016",
        ),
        "EV-IMPELLIZZERI-2020" to EvidenceExplanation(
            "EV-IMPELLIZZERI-2020",
            "Limitações do ACWR",
            "Impellizzeri et al., Sports Med 2020",
        ),
        "EV-ELY-2007" to EvidenceExplanation(
            "EV-ELY-2007",
            "Impacto do clima",
            "Ely et al., MSSE 2007",
        ),
        "EV-FRIEL-2018" to EvidenceExplanation(
            "EV-FRIEL-2018",
            "Zonas pelo limite de esforço",
            "Friel, The Triathlete's Training Bible, 2018",
        ),
        "EV-PFITZINGER-2008" to EvidenceExplanation(
            "EV-PFITZINGER-2008",
            "Longão e volume",
            "Pfitzinger & Douglas, Advanced Marathoning, 2008",
        ),
        "EV-BOSQUET-2007" to EvidenceExplanation(
            "EV-BOSQUET-2007",
            "Redução antes da prova",
            "Bosquet et al., MSSE 2007",
        ),
        "EV-MUJIKA-2003" to EvidenceExplanation(
            "EV-MUJIKA-2003",
            "Redução antes da prova",
            "Mujika & Padilla, MSSE 2003",
        ),
        "EV-ISSURIN-2010" to EvidenceExplanation(
            "EV-ISSURIN-2010",
            "Periodização em blocos",
            "Issurin, Sports Med 2010",
        ),
        "EV-PLEWS-2013" to EvidenceExplanation(
            "EV-PLEWS-2013",
            "Variabilidade cardíaca",
            "Plews et al., Sports Med 2013",
        ),
        "EV-BUCHHEIT-2014" to EvidenceExplanation(
            "EV-BUCHHEIT-2014",
            "FC de repouso e variabilidade",
            "Buchheit, Front Physiol 2014",
        ),
        "EV-WALSH-2021" to EvidenceExplanation(
            "EV-WALSH-2021",
            "Sono e recuperação",
            "Walsh et al., BJSM 2021",
        ),
        "EV-WATSON-2015" to EvidenceExplanation(
            "EV-WATSON-2015",
            "Sono recomendado",
            "Watson et al., Sleep 2015",
        ),
    )

    fun rule(id: String): RuleExplanation = rules[id] ?: RuleExplanation(id, id)

    fun evidence(id: String): EvidenceExplanation = evidence[id] ?: EvidenceExplanation(id, id, "")

    fun hasRule(id: String): Boolean = id in rules

    fun hasEvidence(id: String): Boolean = id in evidence
}
