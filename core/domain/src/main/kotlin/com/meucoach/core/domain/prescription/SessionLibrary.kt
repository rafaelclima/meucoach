package com.meucoach.core.domain.prescription

import com.meucoach.core.model.Experience
import com.meucoach.core.model.RaceDistance

/** Papel da sessão dentro da semana; a fiação agenda dias e durações. */
enum class SessionRole {
    EASY_RUN,
    LONG_RUN,
    QUALITY_MAIN,
    QUALITY_SECOND,
    STRIDES,

    /**
     * Surtos curtos no fim de um fácil do iniciante, após 4 semanas
     * consistentes (ADR 0047 §2.2). É `EASY`, não conta como qualidade e não
     * usa [QualityKind].
     */
    INTRO_FARTLEK,
}

/** Natureza do estímulo forte; o catálogo decide o que cada fase/nível vê (ADR 0047). */
enum class QualityKind {
    STRIDES_ONLY,
    CONTINUOUS,
    INTERVALS,
    FARTLEK,
    HILLS,
    RACE_PACE,
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
 * Estrutura de sessão por (fase, nível, distância), sem pace e sem minutos
 * inventados.
 *
 * Títulos e descrições reutilizam o vocabulário do gerador ("Longão",
 * "Acelerações curtas", "Ritmo forte"/"Intervalado"); paces e durações derivam da
 * calibração e da disponibilidade na fiação (fatia 3). Toda estrutura cita
 * `R-PER-02`.
 *
 * [structure] is the canonical executable shape. Quality templates must carry
 * one; the generator (C4-17) sizes repetitions and recoveries to the real
 * duration. Plain easy and long templates stay continuous and keep it `null`;
 * strides and the intro fartlek carry their own short shape.
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
    val structure: WorkoutStructure? = null,
) {
    init {
        require(title.isNotBlank()) { "Template title must not be blank" }
        require(blurb.isNotBlank()) { "Template blurb must not be blank" }
        require(ruleIds.isNotEmpty()) { "Template must cite at least one rule" }
        require(evidenceIds.isNotEmpty()) { "Template must cite at least one evidence" }
        require((type == WorkoutType.QUALITY) == (qualityKind != null)) {
            "Only quality sessions carry a quality kind"
        }
        require(type != WorkoutType.QUALITY || structure != null) {
            "Quality templates require an executable structure"
        }
    }
}

/**
 * Biblioteca de sessões por fase, nível e distância (ADR 0043 §3.2, ADR 0047).
 *
 * Pura e determinística: mesma fase + nível + distância, mesmas estruturas.
 * A trava de iniciante vive aqui — `BEGINNER` nunca recebe qualidade
 * estruturada; `TAPER` só carrega a micro-dose de strides, `RECOVERY` é só
 * fácil e `BASE` só expõe o fartlek leve para intermediário/avançado (a
 * segunda metade da fase é decidida pela rotação, C4-16).
 *
 * [qualityTemplates] and [easyTemplates] are the catalog: they never invent a
 * duration and expose every eligible stimulus with its executable structure.
 * The generator (C4-17) consumes the rotation directly; there is no legacy
 * one-main/one-second projection anymore.
 */
object SessionLibrary {
    /**
     * Easy sessions eligible in [phase]. [includeIntroFartlek] only matters
     * for a beginner after four consistent weeks (ADR 0047 §2.2); it never
     * turns the intro fartlek into a quality session.
     */
    fun easyTemplates(
        phase: MesoPhase,
        level: Experience,
        includeIntroFartlek: Boolean = false,
    ): List<SessionTemplate> = when (phase) {
        MesoPhase.RECOVERY -> listOf(easy(recovery = true))

        MesoPhase.TAPER -> buildList {
            add(easy(recovery = false))
            // ADR 0069: the beginner keeps one EASY strides micro-dose in the
            // taper; intermediate/advanced keep the QUALITY micro-dose.
            if (level == Experience.BEGINNER) add(stridesBase())
        }

        MesoPhase.BASE, MesoPhase.BUILD, MesoPhase.PEAK -> buildList {
            add(easy(recovery = false))
            add(stridesBase())
            if (level == Experience.BEGINNER && includeIntroFartlek) add(introFartlek())
        }
    }

    /**
     * Full quality matrix for [phase], [level] and race [distance].
     *
     * `distance` only changes `PEAK`: the session receives race-pace work for
     * the event distance, and advanced adds a second specific stimulus. Without
     * a distance the `PEAK` quality stays generic and conservative; the
     * generator only enters `PEAK` with a race, so this is a caller-safety
     * fallback.
     */
    fun qualityTemplates(phase: MesoPhase, level: Experience, distance: RaceDistance? = null): List<SessionTemplate> =
        when (phase) {
            MesoPhase.RECOVERY -> emptyList()

            MesoPhase.TAPER -> listOf(stridesMicroDose())

            MesoPhase.BASE -> when (level) {
                Experience.BEGINNER -> emptyList()
                Experience.INTERMEDIATE, Experience.ADVANCED -> listOf(lightFartlek())
            }

            MesoPhase.BUILD -> buildTemplates(level)

            MesoPhase.PEAK -> peakTemplates(level, distance)
        }

    /**
     * Deterministic weekly rotation over the eligible quality matrix (ADR 0047
     * §2.7). [weekInPhase] is the 0-based position inside the current phase and
     * comes from the plan/mesocycle, never from the clock.
     *
     * Consecutive weeks never repeat the main stimulus when the catalog carries
     * more than one kind. With [maxQualities] = 2 the rotation adds a second
     * template with a kind different from the main one, when one exists; the
     * day still decides whether to keep it (ADR 0044).
     *
     * The light `BASE` fartlek is only eligible in the second half of the
     * phase. Without [phaseWeeks] the rotation stays conservative and exposes
     * no `BASE` quality: the caller must know how long the phase is.
     */
    fun qualityRotation(
        phase: MesoPhase,
        level: Experience,
        distance: RaceDistance? = null,
        weekInPhase: Int,
        maxQualities: Int = 1,
        phaseWeeks: Int? = null,
    ): List<SessionTemplate> {
        require(phaseWeeks == null || phaseWeeks > 0) { "phaseWeeks must be positive" }
        require(phaseWeeks == null || weekInPhase < phaseWeeks) {
            "weekInPhase must be inside the phase"
        }
        val catalog = qualityTemplates(phase, level, distance)
        val eligible = if (phase == MesoPhase.BASE && !isSecondHalfOfPhase(weekInPhase, phaseWeeks)) {
            emptyList()
        } else {
            catalog
        }
        return rotateQualities(eligible, weekInPhase, maxQualities)
    }

    /**
     * Rotation core over an already filtered catalog, exposed internally so the
     * catalog tests can prove the distinct-kind guarantee with synthetic
     * duplicates. Roles are assigned here: main first, second when allowed.
     */
    internal fun rotateQualities(
        templates: List<SessionTemplate>,
        weekInPhase: Int,
        maxQualities: Int,
    ): List<SessionTemplate> {
        require(weekInPhase >= 0) { "weekInPhase must not be negative" }
        require(maxQualities in 1..MAX_QUALITIES_PER_WEEK) {
            "maxQualities must be between 1 and $MAX_QUALITIES_PER_WEEK"
        }
        if (templates.isEmpty()) return emptyList()
        val mainIndex = weekInPhase % templates.size
        val main = templates[mainIndex]
        val selected = mutableListOf(main.copy(role = SessionRole.QUALITY_MAIN))
        if (maxQualities < 2) return selected
        val second = distinctKindAfter(templates, mainIndex, main.qualityKind) ?: return selected
        selected += second.copy(role = SessionRole.QUALITY_SECOND)
        return selected
    }

    private fun distinctKindAfter(
        templates: List<SessionTemplate>,
        mainIndex: Int,
        mainKind: QualityKind?,
    ): SessionTemplate? {
        for (offset in 1 until templates.size) {
            val candidate = templates[(mainIndex + offset) % templates.size]
            if (candidate.qualityKind != mainKind) return candidate
        }
        return null
    }

    /** 0-based [weekInPhase] belongs to the later half; odd lengths round up. */
    private fun isSecondHalfOfPhase(weekInPhase: Int, phaseWeeks: Int?): Boolean {
        if (phaseWeeks == null) return false
        return weekInPhase * 2 >= phaseWeeks
    }

    private fun buildTemplates(level: Experience): List<SessionTemplate> = when (level) {
        Experience.BEGINNER -> emptyList()

        Experience.INTERMEDIATE -> listOf(
            continuous(workMin = 20),
            intervals(repetitions = 5),
            fartlek(repetitions = 8),
            hills(repetitions = 6),
        )

        Experience.ADVANCED -> listOf(
            intervals(repetitions = 6),
            hills(repetitions = 8),
            continuous(workMin = 25),
            fartlek(repetitions = 10),
        )
    }

    private fun peakTemplates(level: Experience, distance: RaceDistance?): List<SessionTemplate> = when (level) {
        Experience.BEGINNER -> emptyList()
        Experience.INTERMEDIATE -> listOf(peakIntermediate(distance))
        Experience.ADVANCED -> peakAdvanced(distance)
    }

    private fun peakIntermediate(distance: RaceDistance?): SessionTemplate =
        if (distance == null) continuous(workMin = 20) else peakRacePace(distance)

    private fun peakAdvanced(distance: RaceDistance?): List<SessionTemplate> = when (distance) {
        null -> listOf(intervals(repetitions = 6))

        RaceDistance.FIVE_K -> listOf(peakRacePace(distance), shortIntervals())

        RaceDistance.TEN_K,
        RaceDistance.HALF_MARATHON,
        RaceDistance.MARATHON,
        -> listOf(peakRacePace(distance), continuous(workMin = 25))
    }

    private fun easy(recovery: Boolean): SessionTemplate = SessionTemplate(
        role = SessionRole.EASY_RUN,
        type = WorkoutType.EASY,
        title = if (recovery) "Recuperação: fácil e curto" else "Rodagem",
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

    private fun stridesBase(): SessionTemplate = SessionTemplate(
        role = SessionRole.STRIDES,
        type = WorkoutType.EASY,
        title = "Acelerações curtas",
        qualityKind = null,
        durationPolicy = DurationPolicy.MICRO_DOSE_25,
        blurb = "Acelerações curtas no fim da corrida leve — não é o treino forte da semana.",
        ruleIds = listOf(RULE_ID),
        evidenceIds = listOf("EV-SEILER-2010", "EV-DANIELS-2014"),
        structure = stridesStructure(note = "Rápido e relaxado; recuperação completa."),
    )

    private fun introFartlek(): SessionTemplate = SessionTemplate(
        role = SessionRole.INTRO_FARTLEK,
        type = WorkoutType.EASY,
        title = "Rodagem com estímulos",
        qualityKind = null,
        durationPolicy = DurationPolicy.EASY_SIZED,
        blurb = "Surtos curtos e controlados no fim da corrida leve, depois de 4 semanas consistentes.",
        ruleIds = listOf(RULE_ID),
        evidenceIds = listOf("EV-SEILER-2010", "EV-DANIELS-2014"),
        structure = structure(
            warmupMin = WARMUP_EASY_MIN,
            blocks = listOf(
                WorkoutBlock(
                    kind = BlockKind.WORK,
                    repetitions = 6,
                    workSec = 30,
                    recoverySec = 90,
                    target = "RPE 7",
                    note = "Confortavelmente forte; não é prova.",
                ),
            ),
            cooldownMin = 10,
            totalMin = 30,
        ),
    )

    private fun stridesMicroDose(): SessionTemplate = SessionTemplate(
        role = SessionRole.STRIDES,
        type = WorkoutType.QUALITY,
        title = "Acelerações curtas",
        qualityKind = QualityKind.STRIDES_ONLY,
        durationPolicy = DurationPolicy.MICRO_DOSE_25,
        blurb = "Semana de prova: dose pequena para manter o estímulo, sem fadiga.",
        ruleIds = listOf(RULE_ID, "R-PER-04", "R-PLAN-05", "R-PLAN-10"),
        evidenceIds = listOf("EV-BOSQUET-2007", "EV-MUJIKA-2003"),
        structure = stridesStructure(note = "Micro-dose de semana de prova; sem fadiga."),
    )

    private fun stridesStructure(note: String): WorkoutStructure = structure(
        warmupMin = WARMUP_EASY_MIN,
        blocks = listOf(
            WorkoutBlock(
                kind = BlockKind.STRIDES,
                repetitions = 6,
                workSec = 20,
                recoverySec = 60,
                target = "RPE 8",
                note = note,
            ),
        ),
        cooldownMin = 9,
        totalMin = 25,
    )

    private fun lightFartlek(): SessionTemplate = SessionTemplate(
        role = SessionRole.QUALITY_MAIN,
        type = WorkoutType.QUALITY,
        title = "Fartlek leve",
        qualityKind = QualityKind.FARTLEK,
        durationPolicy = DurationPolicy.QUALITY_SIZED,
        blurb = "Variação leve no meio do treino, na segunda metade da fase de base. Não é o treino forte.",
        ruleIds = QUALITY_RULES,
        evidenceIds = listOf("EV-SEILER-2010", "EV-DANIELS-2014"),
        structure = structure(
            warmupMin = WARMUP_EASY_MIN,
            blocks = listOf(
                WorkoutBlock(
                    kind = BlockKind.WORK,
                    repetitions = 6,
                    workSec = 30,
                    recoverySec = 90,
                    target = "RPE 7",
                    note = "Confortavelmente forte; dose baixa.",
                ),
            ),
            cooldownMin = 10,
            totalMin = 30,
        ),
    )

    private fun continuous(workMin: Int): SessionTemplate = SessionTemplate(
        role = SessionRole.QUALITY_MAIN,
        type = WorkoutType.QUALITY,
        title = "Ritmo forte",
        qualityKind = QualityKind.CONTINUOUS,
        durationPolicy = DurationPolicy.QUALITY_SIZED,
        blurb = "Ritmo forte sustentado com aquecimento e volta à calma.",
        ruleIds = QUALITY_RULES,
        evidenceIds = QUALITY_EVIDENCE,
        structure = structure(
            warmupMin = 10,
            blocks = listOf(
                WorkoutBlock(
                    kind = BlockKind.WORK,
                    workSec = workMin * SECONDS_PER_MINUTE,
                    target = "T",
                    note = "Ritmo forte sustentado.",
                ),
            ),
            cooldownMin = 5,
            totalMin = workMin + 15,
        ),
    )

    private fun intervals(repetitions: Int): SessionTemplate = SessionTemplate(
        role = SessionRole.QUALITY_MAIN,
        type = WorkoutType.QUALITY,
        title = "Intervalado",
        qualityKind = QualityKind.INTERVALS,
        durationPolicy = DurationPolicy.QUALITY_SIZED,
        blurb = "Repetições fortes com recuperação leve entre elas.",
        ruleIds = QUALITY_RULES,
        evidenceIds = QUALITY_EVIDENCE,
        structure = structure(
            warmupMin = 10,
            blocks = listOf(
                WorkoutBlock(
                    kind = BlockKind.WORK,
                    repetitions = repetitions,
                    workSec = INTERVAL_WORK_SEC,
                    recoverySec = INTERVAL_RECOVERY_SEC,
                    target = "I",
                    note = "Repetições fortes; recuperação leve.",
                ),
            ),
            cooldownMin = 5,
            totalMin = 15 + repetitions * INTERVAL_CYCLE_MIN,
        ),
    )

    private fun fartlek(repetitions: Int): SessionTemplate {
        val totalMin = if (repetitions >= 10) 40 else 35
        return SessionTemplate(
            role = SessionRole.QUALITY_MAIN,
            type = WorkoutType.QUALITY,
            title = "Fartlek",
            qualityKind = QualityKind.FARTLEK,
            durationPolicy = DurationPolicy.QUALITY_SIZED,
            blurb = "Variação de ritmo, guiado pelo quanto você se sente cansado — sem ritmo marcado.",
            ruleIds = QUALITY_RULES,
            evidenceIds = listOf("EV-SEILER-2010", "EV-DANIELS-2014"),
            structure = structure(
                warmupMin = 10,
                blocks = listOf(
                    WorkoutBlock(
                        kind = BlockKind.WORK,
                        repetitions = repetitions,
                        workSec = 60,
                        recoverySec = 60,
                        target = "RPE 8",
                        note = "Forte e fácil alternados pelo esforço.",
                    ),
                ),
                cooldownMin = totalMin - 10 - repetitions * 2,
                totalMin = totalMin,
            ),
        )
    }

    private fun hills(repetitions: Int): SessionTemplate {
        val totalMin = if (repetitions >= 8) 40 else 35
        return SessionTemplate(
            role = SessionRole.QUALITY_MAIN,
            type = WorkoutType.QUALITY,
            title = "Subidas",
            qualityKind = QualityKind.HILLS,
            durationPolicy = DurationPolicy.QUALITY_SIZED,
            blurb = "Subidas curtas e fortes, com recuperação caminhando.",
            ruleIds = QUALITY_RULES,
            evidenceIds = listOf("EV-DANIELS-2014", "EV-SEILER-2010"),
            structure = structure(
                warmupMin = 10,
                blocks = listOf(
                    WorkoutBlock(
                        kind = BlockKind.WORK,
                        repetitions = repetitions,
                        workSec = 45,
                        recoverySec = 120,
                        target = "RPE 8",
                        note = "Subida forte; desça caminhando.",
                    ),
                ),
                cooldownMin = 8,
                totalMin = totalMin,
            ),
        )
    }

    private fun peakRacePace(distance: RaceDistance): SessionTemplate {
        val shape = peakShape(distance)
        return SessionTemplate(
            role = SessionRole.QUALITY_MAIN,
            type = WorkoutType.QUALITY,
            title = "Pace de prova ${distance.raceLabel()}",
            qualityKind = QualityKind.RACE_PACE,
            durationPolicy = DurationPolicy.QUALITY_SIZED,
            blurb = "Blocos no ritmo que você corre hoje, não na meta: a previsão do app manda.",
            ruleIds = QUALITY_RULES,
            evidenceIds = listOf("EV-DANIELS-2014", "EV-FOSTER-2001"),
            structure = structure(
                warmupMin = 10,
                blocks = listOf(
                    WorkoutBlock(
                        kind = BlockKind.RACE_PACE,
                        repetitions = shape.repetitions,
                        workSec = shape.workSec,
                        recoverySec = shape.recoverySec,
                        target = distance.targetLabel(),
                        note = "Pace atual, não a meta.",
                    ),
                ),
                cooldownMin = 5,
                totalMin = 45,
            ),
        )
    }

    private fun shortIntervals(): SessionTemplate = SessionTemplate(
        role = SessionRole.QUALITY_MAIN,
        type = WorkoutType.QUALITY,
        title = "Intervalado",
        qualityKind = QualityKind.INTERVALS,
        durationPolicy = DurationPolicy.QUALITY_SIZED,
        blurb = "Repetições curtas, no estilo dos 5 km.",
        ruleIds = QUALITY_RULES,
        evidenceIds = listOf("EV-DANIELS-2014", "EV-FOSTER-2001"),
        structure = structure(
            warmupMin = 12,
            blocks = listOf(
                WorkoutBlock(
                    kind = BlockKind.WORK,
                    repetitions = 8,
                    workSec = 120,
                    recoverySec = 60,
                    target = "I",
                    note = "Repetições curtas e fortes.",
                ),
            ),
            cooldownMin = 9,
            totalMin = 45,
        ),
    )

    private fun structure(
        warmupMin: Int,
        blocks: List<WorkoutBlock>,
        cooldownMin: Int,
        totalMin: Int,
    ): WorkoutStructure = WorkoutStructure(
        warmupMin = warmupMin,
        blocks = blocks,
        cooldownMin = cooldownMin,
        totalMin = totalMin,
    )

    private data class PeakShape(val repetitions: Int, val workSec: Int, val recoverySec: Int)

    /**
     * Time-based shape for the distance-specific peak. [WorkoutBlock] carries
     * seconds, not meters, and the session ceiling is 45 min; C4-17 converts
     * the current prediction to the real pace and available duration. The
     * product examples (5×1 km, 3×2 km, 2×3 km, 2×5 km) are the intent, not a
     * promise that does not fit the day.
     */
    private fun peakShape(distance: RaceDistance): PeakShape = when (distance) {
        RaceDistance.FIVE_K -> PeakShape(repetitions = 5, workSec = 240, recoverySec = 120)
        RaceDistance.TEN_K -> PeakShape(repetitions = 3, workSec = 480, recoverySec = 120)
        RaceDistance.HALF_MARATHON -> PeakShape(repetitions = 2, workSec = 720, recoverySec = 180)
        RaceDistance.MARATHON -> PeakShape(repetitions = 2, workSec = 660, recoverySec = 240)
    }

    private fun RaceDistance.raceLabel(): String = when (this) {
        RaceDistance.FIVE_K -> "5K"
        RaceDistance.TEN_K -> "10K"
        RaceDistance.HALF_MARATHON -> "meia maratona"
        RaceDistance.MARATHON -> "maratona"
    }

    private fun RaceDistance.targetLabel(): String = when (this) {
        RaceDistance.FIVE_K -> "GOAL_5K"
        RaceDistance.TEN_K -> "GOAL_10K"
        RaceDistance.HALF_MARATHON -> "GOAL_HALF"
        RaceDistance.MARATHON -> "GOAL_MARATHON"
    }

    private const val RULE_ID = "R-PER-02"
    private const val WARMUP_EASY_MIN = 8
    private const val SECONDS_PER_MINUTE = 60
    private const val INTERVAL_WORK_SEC = 180
    private const val INTERVAL_RECOVERY_SEC = 120
    private const val INTERVAL_CYCLE_MIN = 5
    private const val MAX_QUALITIES_PER_WEEK = 2
    private val QUALITY_RULES = listOf(RULE_ID, "R-PER-04", "R-PLAN-02")
    private val QUALITY_EVIDENCE = listOf("EV-DANIELS-2014", "EV-SEILER-2010", "EV-FOSTER-2001")
}
