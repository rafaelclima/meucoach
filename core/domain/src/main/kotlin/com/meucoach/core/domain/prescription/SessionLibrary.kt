package com.meucoach.core.domain.prescription

import com.meucoach.core.model.Experience

/** Papel da sessão dentro da semana; a fiação agenda dias e durações. */
enum class SessionRole {
    EASY_RUN,
    LONG_RUN,
    QUALITY_MAIN,
    QUALITY_SECOND,
    STRIDES,
}

/** Natureza do estímulo forte; iniciante só conhece [STRIDES_ONLY] (ADR 0043 §3.2). */
enum class QualityKind {
    STRIDES_ONLY,
    CONTINUOUS,
    INTERVALS,
}

/**
 * Como a fiação resolve a duração: pelos dimensionadores vigentes
 * (disponibilidade, teto de longão, piso de qualidade) ou pela micro-dose
 * fixa de 25 min (`R-PLAN-05/10`). A biblioteca nunca inventa minutos.
 */
enum class DurationPolicy {
    EASY_SIZED,
    LONG_SIZED,
    QUALITY_SIZED,
    MICRO_DOSE_25,
}

/**
 * Estrutura de sessão por (fase, nível), sem pace e sem minutos inventados.
 *
 * Títulos e descrições reutilizam o vocabulário do gerador ("Longão",
 * "Strides curtos", "Ritmo forte"/"Intervalos"); paces e durações derivam da
 * calibração e da disponibilidade na fiação (fatia 3). Toda estrutura cita
 * `R-PER-02`.
 */
data class SessionTemplate(
    val role: SessionRole,
    val type: WorkoutType,
    val title: String,
    val qualityKind: QualityKind?,
    val durationPolicy: DurationPolicy,
    val blurb: String,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
) {
    init {
        require(title.isNotBlank()) { "Template title must not be blank" }
        require(blurb.isNotBlank()) { "Template blurb must not be blank" }
        require(ruleIds.isNotEmpty()) { "Template must cite at least one rule" }
        require(evidenceIds.isNotEmpty()) { "Template must cite at least one evidence" }
        require((type == WorkoutType.QUALITY) == (qualityKind != null)) {
            "Only quality sessions carry a quality kind"
        }
    }
}

/**
 * Biblioteca de sessões por fase e nível (ADR 0043 §3.2, fatia 2).
 *
 * Pura e determinística: mesma fase + mesmo nível, mesmas estruturas. A trava
 * de iniciante vive aqui — `BEGINNER` nunca recebe [QualityKind.CONTINUOUS]
 * nem [QualityKind.INTERVALS]; a saída continua sendo a graduação por
 * evidência (ADR 0042). `TAPER` só carrega a micro-dose, `RECOVERY` é só fácil
 * e `BASE` não tem qualidade principal.
 */
object SessionLibrary {
    fun templates(phase: MesoPhase, level: Experience): List<SessionTemplate> = when (phase) {
        MesoPhase.RECOVERY -> listOf(easy(recovery = true))

        MesoPhase.TAPER -> listOf(easy(recovery = false), long(), stridesMicroDose())

        MesoPhase.BASE -> listOf(easy(recovery = false), long(), stridesBase())

        MesoPhase.BUILD -> buildList {
            add(easy(recovery = false))
            add(long())
            when (level) {
                Experience.BEGINNER -> add(stridesBase())

                Experience.INTERMEDIATE -> {
                    add(qualityMain(QualityKind.CONTINUOUS, level))
                    add(qualitySecond(QualityKind.CONTINUOUS))
                }

                Experience.ADVANCED -> {
                    add(qualityMain(QualityKind.INTERVALS, level))
                    add(qualitySecond(QualityKind.INTERVALS))
                }
            }
        }

        MesoPhase.PEAK -> buildList {
            add(easy(recovery = false))
            add(long())
            when (level) {
                Experience.BEGINNER -> add(stridesBase())
                Experience.INTERMEDIATE -> add(qualityMain(QualityKind.CONTINUOUS, level))
                Experience.ADVANCED -> add(qualityMain(QualityKind.INTERVALS, level))
            }
        }
    }

    private fun easy(recovery: Boolean): SessionTemplate = SessionTemplate(
        role = SessionRole.EASY_RUN,
        type = WorkoutType.EASY,
        title = if (recovery) "Recuperação" else "Fácil e conversável",
        qualityKind = null,
        durationPolicy = DurationPolicy.EASY_SIZED,
        blurb = if (recovery) {
            "Recuperação após a prova: sessão curta e leve, sem treino forte."
        } else {
            "Base aeróbica conversável; sem treino forte neste dia."
        },
        ruleIds = if (recovery) listOf(RULE_ID, "R-RACE-04") else listOf(RULE_ID),
        evidenceIds = listOf("EV-SEILER-2010"),
    )

    private fun long(): SessionTemplate = SessionTemplate(
        role = SessionRole.LONG_RUN,
        type = WorkoutType.LONG,
        title = "Longão",
        qualityKind = null,
        durationPolicy = DurationPolicy.LONG_SIZED,
        blurb = "Longão em ritmo fácil, 25–30% do volume semanal. Iniciante ≤ 150 min.",
        ruleIds = listOf(RULE_ID, "R-PLAN-03"),
        evidenceIds = listOf("EV-PFITZINGER-2008", "EV-DANIELS-2014"),
    )

    private fun stridesBase(): SessionTemplate = SessionTemplate(
        role = SessionRole.STRIDES,
        type = WorkoutType.QUALITY,
        title = "Strides curtos",
        qualityKind = QualityKind.STRIDES_ONLY,
        durationPolicy = DurationPolicy.MICRO_DOSE_25,
        blurb = "Estímulo neuromuscular curto, sem fadiga.",
        ruleIds = listOf(RULE_ID),
        evidenceIds = listOf("EV-SEILER-2010", "EV-DANIELS-2014"),
    )

    private fun stridesMicroDose(): SessionTemplate = SessionTemplate(
        role = SessionRole.STRIDES,
        type = WorkoutType.QUALITY,
        title = "Strides curtos",
        qualityKind = QualityKind.STRIDES_ONLY,
        durationPolicy = DurationPolicy.MICRO_DOSE_25,
        blurb = "Semana de prova: micro-dose para manter o estímulo, sem fadiga.",
        ruleIds = listOf(RULE_ID, "R-PLAN-05"),
        evidenceIds = listOf("EV-BOSQUET-2007", "EV-MUJIKA-2003"),
    )

    private fun qualityMain(kind: QualityKind, level: Experience): SessionTemplate = SessionTemplate(
        role = SessionRole.QUALITY_MAIN,
        type = WorkoutType.QUALITY,
        title = if (level == Experience.ADVANCED) "Intervalos" else "Ritmo forte",
        qualityKind = kind,
        durationPolicy = DurationPolicy.QUALITY_SIZED,
        blurb = if (kind == QualityKind.INTERVALS) {
            "Repetições fortes com recuperação leve entre elas."
        } else {
            "Ritmo forte sustentado com aquecimento e volta à calma."
        },
        ruleIds = listOf(RULE_ID, "R-PLAN-02"),
        evidenceIds = listOf("EV-DANIELS-2014", "EV-SEILER-2010", "EV-FOSTER-2001"),
    )

    private fun qualitySecond(kind: QualityKind): SessionTemplate = qualityMain(kind, levelFor(kind))
        .copy(role = SessionRole.QUALITY_SECOND)

    private fun levelFor(kind: QualityKind): Experience =
        if (kind == QualityKind.INTERVALS) Experience.ADVANCED else Experience.INTERMEDIATE

    private const val RULE_ID = "R-PER-02"
}
