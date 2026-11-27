package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.calibration.CalibrationPerformance
import com.meucoach.core.domain.calibration.CalibrationTier
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.ReadinessLevel
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Calibração que sustenta uma graduação (ADR 0042 §3.2).
 *
 * Tier A vale por data (prova ou TT recente); Tier B vale pela confirmação
 * explícita do teste de 30 min, nunca por inferência silenciosa. Ausência de
 * tier ou Tier C nunca aprova.
 */
data class LevelCalibration(
    val tier: CalibrationTier?,
    val calibratedAt: LocalDate?,
    val tierBConfirmed: Boolean = false,
)

/**
 * Entradas puras da avaliação de graduação (ADR 0042 §3.2).
 *
 * [recentWeeks] carrega até 6 semanas persistidas (com `createdOn`, status e
 * evidência); [strongRuns] são as corridas fortes reais do período (planejadas
 * ou não), usadas para o longão; [efforts] são os esforços sustentados com FC do
 * período (os mesmos do contador Tier B); [illnessOrInjuryDates] marca os dias
 * com `ILLNESS_OR_INJURY` do check-in; [ctlLast4] e [monotonyLast4] são as séries
 * semanais objetivas para o `INTERMEDIATE → ADVANCED`.
 */
data class LevelGraduationInput(
    val profile: AthleteProfile,
    val recentWeeks: List<WeeklyPlan>,
    val strongRuns: List<StrongRun> = emptyList(),
    val efforts: List<CalibrationPerformance> = emptyList(),
    val calibration: LevelCalibration,
    val readiness: ReadinessResult,
    val illnessOrInjuryDates: Set<LocalDate> = emptySet(),
    val ctlLast4: List<Double> = emptyList(),
    /** CTL só conta quando vem de carga objetiva (TRIMP/rTSS), nunca só sRPE. */
    val objectiveCtl: Boolean = false,
    val monotonyLast4: List<Double?> = emptyList(),
    /** Prova ou TT mais recente concluído, quando houver. */
    val lastRaceOrTimeTrialDate: LocalDate? = null,
    /** Verdadeiro só para um TT de 30 min válido e recente, confirmado pelo atleta. */
    val hasValidTimeTrial30Min: Boolean = false,
    val today: LocalDate,
)

sealed interface LevelGraduationResult {
    data class Stay(val reason: String, val missing: List<String>) : LevelGraduationResult {
        init {
            require(reason.isNotBlank()) { "Stay reason must not be blank" }
        }
    }

    data class SuggestUpgrade(
        val from: Experience,
        val to: Experience,
        val reasons: List<String>,
        val ruleIds: List<String>,
        val evidenceIds: List<String>,
        val confidence: DataConfidence,
        val inputsHash: String,
    ) : LevelGraduationResult {
        init {
            require(from != to) { "Graduation must change the level" }
            require(reasons.isNotEmpty()) { "Suggestion must explain its reasons" }
            require(ruleIds.isNotEmpty()) { "Suggestion must cite at least one rule" }
            require(evidenceIds.isNotEmpty()) { "Suggestion must cite at least one evidence" }
            require(inputsHash.isNotBlank()) { "Suggestion must carry its inputs hash" }
        }
    }
}

/**
 * Graduação de nível por evidência demonstrada (ADR 0042, `R-LVL-01/02`).
 *
 * Puro e determinístico: avalia o perfil vigente contra até 6 semanas de
 * histórico e devolve `Stay` ou `SuggestUpgrade`. Nunca auto-aplica — a subida
 * de nível exige aceite explícito (padrão T4/ADR 0031) — e nunca rebaixa:
 * regressão só sugere revisão pelo mesmo card, sem downgrade automático.
 *
 * Convenções de produto (confiança máxima `MEDIUM`, `LIMITED`):
 * - semana forte = `strongCompletionRatio ≥ 0,80` (só `ACTIVITY`/`GUIDED`);
 *   `MANUAL`/`LEGACY_UNVERIFIED` nunca contam como evidência forte;
 * - esforço válido = 20–35 min com FC (pace deriva de distância/duração),
 *   recente por 56 dias, como o contador Tier B;
 * - longão conta pelo `StrongRun.durationMin` real (planejado ou não), dentro da
 *   janela de 6 semanas; a duração planejada não basta (`R-PLAN-01 v2`/`R-LVL-03`);
 * - doença/lesão vale por check-in, prontidão ou sessão `CLOSED` por
 *   `ILLNESS_OR_INJURY` nos últimos 14 dias;
 * - CTL estável/crescente = último valor ≥ primeiro em 4 semanas objetivas;
 * - monotonia média de 4 semanas < 2,0 (`EV-FOSTER-2001`); nula bloqueia.
 */
object LevelGraduationEngine {
    const val ENGINE_VERSION = "level-graduation-v1"

    const val MISSING_STRONG_WEEKS = "strong_weeks"
    const val MISSING_EFFORTS = "efforts"
    const val MISSING_CALIBRATION = "calibration"
    const val MISSING_ILLNESS = "illness"
    const val MISSING_READINESS = "readiness"
    const val MISSING_LONG_RUN = "long_run"
    const val MISSING_CTL = "ctl"
    const val MISSING_LONG_RUN_90 = "long_run_90"
    const val MISSING_RACE = "race"
    const val MISSING_MONOTONY = "monotony"

    fun evaluate(input: LevelGraduationInput): LevelGraduationResult {
        if (input.profile.experience == Experience.ADVANCED) {
            return LevelGraduationResult.Stay(
                reason = "Você já está no nível Avançado; não há próximo nível a sugerir.",
                missing = emptyList(),
            )
        }
        val window = recentWindow(input.recentWeeks, input.today)
        val base = evaluateBase(input, window)
        if (input.profile.experience == Experience.BEGINNER) {
            return if (base.missing.isEmpty()) {
                suggest(
                    input = input,
                    from = Experience.BEGINNER,
                    to = Experience.INTERMEDIATE,
                    ruleId = "R-LVL-01",
                    evidenceIds = listOf("EV-SEILER-2010", "EV-DANIELS-2014", "EV-FRIEL-2018"),
                    reasons = base.reasons,
                )
            } else {
                stay(base.missing, base)
            }
        }
        val missing = base.missing + evaluateAdvanced(input, window)
        return if (missing.isEmpty()) {
            suggest(
                input = input,
                from = Experience.INTERMEDIATE,
                to = Experience.ADVANCED,
                ruleId = "R-LVL-02",
                evidenceIds = listOf("EV-SEILER-2010", "EV-DANIELS-2014", "EV-FOSTER-2001"),
                reasons = base.reasons + advancedReasons(input),
            )
        } else {
            stay(missing, base)
        }
    }

    private data class BaseEvaluation(
        val missing: List<String>,
        val reasons: List<String>,
        val strongWeeks: Int,
        val effortCount: Int,
    )

    private fun evaluateBase(input: LevelGraduationInput, window: List<WeeklyPlan>): BaseEvaluation {
        val strongWeeks = window.count { (it.strongCompletionRatio() ?: 0.0) >= STRONG_WEEK_MIN }
        val efforts = validEfforts(input.efforts, input.today)
        val calibrationOk = isCalibrationValid(input.calibration, input.today)
        val illness = hasRecentIllness(input, input.today)
        val readinessRed = input.readiness.level == ReadinessLevel.RED
        val longRun = hasLongRun(input, window, LONG_RUN_60_MIN)
        val missing = buildList {
            if (strongWeeks < MIN_STRONG_WEEKS) add(MISSING_STRONG_WEEKS)
            if (efforts.size < MIN_EFFORTS) add(MISSING_EFFORTS)
            if (!calibrationOk) add(MISSING_CALIBRATION)
            if (illness) add(MISSING_ILLNESS)
            if (readinessRed) add(MISSING_READINESS)
            if (!longRun) add(MISSING_LONG_RUN)
        }
        val reasons = buildList {
            add("$strongWeeks de 6 semanas com pelo menos 80% dos treinos confirmados por uma corrida")
            add("${efforts.size} esforços de 20 a 35 minutos com frequência cardíaca e ritmo")
            add(calibrationReason(input.calibration))
            add("Sem doença ou lesão nos últimos 14 dias e prontidão atual ${readinessLabel(input.readiness)}")
            add("Longão real de pelo menos 60 minutos concluído")
        }
        return BaseEvaluation(missing, reasons, strongWeeks, efforts.size)
    }

    private fun evaluateAdvanced(input: LevelGraduationInput, window: List<WeeklyPlan>): List<String> = buildList {
        if (!isCtlStableOrGrowing(input)) add(MISSING_CTL)
        if (!hasLongRun(input, window, LONG_RUN_90_MIN)) add(MISSING_LONG_RUN_90)
        if (!hasRaceEvidence(input)) add(MISSING_RACE)
        if (!isMonotonyOk(input.monotonyLast4)) add(MISSING_MONOTONY)
    }

    private fun advancedReasons(input: LevelGraduationInput): List<String> = buildList {
        add("Carga das últimas 4 semanas estável ou subindo, com dados de FC ou ritmo")
        add("Longão real de pelo menos 90 minutos concluído")
        add(raceReason(input))
        add("Variedade média das últimas 4 semanas abaixo de 2,0 (dias com esforço muito parecido)")
    }

    private fun stay(missing: List<String>, base: BaseEvaluation): LevelGraduationResult.Stay {
        val fragments = missing.map { missingFragment(it, base) }
        return LevelGraduationResult.Stay(
            reason = "Ainda não há base para subir de nível: ${fragments.joinToString("; ")}.",
            missing = missing,
        )
    }

    private fun suggest(
        input: LevelGraduationInput,
        from: Experience,
        to: Experience,
        ruleId: String,
        evidenceIds: List<String>,
        reasons: List<String>,
    ): LevelGraduationResult.SuggestUpgrade = LevelGraduationResult.SuggestUpgrade(
        from = from,
        to = to,
        reasons = reasons,
        ruleIds = listOf(ruleId),
        evidenceIds = evidenceIds,
        confidence = DataConfidence.MEDIUM,
        inputsHash = inputsHash(input),
    )

    private fun recentWindow(recentWeeks: List<WeeklyPlan>, today: LocalDate): List<WeeklyPlan> = recentWeeks
        .distinctBy { it.weekStart }
        .filter { !it.weekStart.isAfter(today) }
        .sortedByDescending { it.weekStart }
        .take(WINDOW_WEEKS)

    private fun validEfforts(efforts: List<CalibrationPerformance>, today: LocalDate): List<CalibrationPerformance> =
        efforts.filter { effort ->
            effort.durationSec in MIN_EFFORT_SEC..MAX_EFFORT_SEC &&
                effort.averageHeartRate != null &&
                isRecent(effort.date, today, RECENT_DAYS)
        }

    private fun isCalibrationValid(calibration: LevelCalibration, today: LocalDate): Boolean {
        if (calibration.tier == CalibrationTier.A) {
            val calibratedAt = calibration.calibratedAt ?: return false
            return isRecent(calibratedAt, today, CALIBRATION_RECENT_DAYS)
        }
        return calibration.tier == CalibrationTier.B && calibration.tierBConfirmed
    }

    private fun hasRecentIllness(input: LevelGraduationInput, today: LocalDate): Boolean {
        if (ReadinessFlag.ILLNESS_OR_INJURY in input.readiness.flags) return true
        val start = today.minusDays(ILLNESS_WINDOW_DAYS - 1)
        if (input.illnessOrInjuryDates.any { !it.isAfter(today) && !it.isBefore(start) }) return true
        return input.recentWeeks.any { plan ->
            plan.sessions.any { session ->
                session.status == SessionStatus.CLOSED &&
                    session.closureReason == SessionClosureReason.ILLNESS_OR_INJURY &&
                    !session.date.isAfter(today) &&
                    !session.date.isBefore(start)
            }
        }
    }

    private fun hasLongRun(input: LevelGraduationInput, window: List<WeeklyPlan>, minMinutes: Int): Boolean {
        val windowStart = window.minOfOrNull { it.weekStart }
            ?: input.today.minusWeeks(WINDOW_WEEKS.toLong())
        return input.strongRuns.any { run ->
            run.durationMin >= minMinutes && !run.date.isBefore(windowStart) && !run.date.isAfter(input.today)
        }
    }

    private fun isCtlStableOrGrowing(input: LevelGraduationInput): Boolean {
        if (!input.objectiveCtl) return false
        val series = input.ctlLast4.takeLast(CTL_WEEKS)
        if (series.size < CTL_WEEKS) return false
        if (series.any { !it.isFinite() || it < 0.0 }) return false
        return series.last() >= series.first()
    }

    private fun isMonotonyOk(monotonyLast4: List<Double?>): Boolean {
        val series = monotonyLast4.takeLast(MONOTONY_WEEKS)
        if (series.size < MONOTONY_WEEKS) return false
        if (series.any { it == null || !it.isFinite() || it < 0.0 }) return false
        return series.filterNotNull().average() < MONOTONY_MAX
    }

    private fun hasRaceEvidence(input: LevelGraduationInput): Boolean {
        val raceDate = input.lastRaceOrTimeTrialDate
        if (raceDate != null && isRecent(raceDate, input.today, RECENT_DAYS)) return true
        return input.hasValidTimeTrial30Min
    }

    private fun isRecent(date: LocalDate, today: LocalDate, maxDays: Long): Boolean {
        if (date.isAfter(today)) return false
        return ChronoUnit.DAYS.between(date, today) <= maxDays
    }

    private fun calibrationReason(calibration: LevelCalibration): String = when (calibration.tier) {
        CalibrationTier.A -> "Calibração por prova recente de ${calibration.calibratedAt?.format(DATE_LABEL)}"
        CalibrationTier.B -> "Calibração por treinos recentes"
        else -> "Calibração válida"
    }

    private fun raceReason(input: LevelGraduationInput): String {
        val raceDate = input.lastRaceOrTimeTrialDate
        if (raceDate != null && isRecent(raceDate, input.today, RECENT_DAYS)) {
            return "Prova ou teste de tempo concluído em ${raceDate.format(DATE_LABEL)}"
        }
        return "Teste de 30 minutos válido"
    }

    private fun readinessLabel(readiness: ReadinessResult): String = when (readiness.level) {
        ReadinessLevel.GREEN -> "alta"
        ReadinessLevel.AMBER -> "moderada"
        ReadinessLevel.RED -> "baixa"
    }

    private fun missingFragment(missing: String, base: BaseEvaluation): String = when (missing) {
        MISSING_STRONG_WEEKS ->
            "consistência (${base.strongWeeks} de $MIN_STRONG_WEEKS semanas com 80% confirmado por uma corrida)"

        MISSING_EFFORTS -> "esforços com FC e ritmo (${base.effortCount} de $MIN_EFFORTS de 20–35 min)"

        MISSING_CALIBRATION -> "calibração por prova recente ou por treinos recentes"

        MISSING_ILLNESS -> "14 dias sem doença ou lesão"

        MISSING_READINESS -> "prontidão atual fora do vermelho"

        MISSING_LONG_RUN -> "longão real de pelo menos $LONG_RUN_60_MIN min"

        MISSING_CTL -> "carga das últimas $CTL_WEEKS semanas estável ou subindo, com dados de FC ou ritmo"

        MISSING_LONG_RUN_90 -> "longão real de pelo menos $LONG_RUN_90_MIN min"

        MISSING_RACE -> "prova ou teste de 30 min recente (até $RECENT_DAYS dias)"

        MISSING_MONOTONY ->
            "variedade média de $MONOTONY_WEEKS semanas abaixo de $MONOTONY_MAX " +
                "(dias com esforço muito parecido)"

        else -> missing
    }

    fun inputsHash(input: LevelGraduationInput): String = sha256(canonical(input))

    private fun canonical(input: LevelGraduationInput): String = buildString {
        append("engine=").append(ENGINE_VERSION)
        append("|profile=").append(input.profile.id).append(',').append(input.profile.experience.name)
        append("|today=").append(input.today)
        append("|weeks=").append(
            input.recentWeeks.distinctBy { it.weekStart }.sortedBy { it.weekStart }.joinToString("|") {
                "${it.weekStart}:${it.createdOn}:${it.sessions.sortedBy { session -> session.date }.joinToString(",") {
                    listOf(
                        it.date.toString(),
                        it.type.name,
                        it.title,
                        it.durationMin?.toString(),
                        it.status.name,
                        it.completionEvidence?.name,
                        it.closureReason?.name,
                        it.activityId,
                    ).joinToString(";")
                }}"
            },
        )
        append("|efforts=").append(
            input.efforts.sortedWith(compareBy({ it.date }, { it.durationSec })).joinToString("|") {
                "${it.date},${it.durationSec},${it.distanceM},${it.averageHeartRate}"
            },
        )
        append("|strongRuns=").append(
            input.strongRuns
                .sortedWith(compareBy({ it.date }, { it.durationMin }, { it.source.name }))
                .joinToString("|") { "${it.date},${it.durationMin},${it.source.name},${it.plannedSessionId}" },
        )
        append("|calibration=").append(
            "${input.calibration.tier?.name},${input.calibration.calibratedAt},${input.calibration.tierBConfirmed}",
        )
        append("|readiness=").append(
            listOf(
                input.readiness.score.toString(),
                input.readiness.level.name,
                input.readiness.confidence.name,
                input.readiness.components.entries.sortedBy { it.key.name }
                    .joinToString("|") { "${it.key.name}=${it.value}" },
                input.readiness.missing.map { it.name }.sorted().joinToString("|"),
                input.readiness.flags.map { it.name }.sorted().joinToString("|"),
            ).joinToString(","),
        )
        append("|illness=").append(input.illnessOrInjuryDates.sorted().joinToString(","))
        append("|ctl=").append(input.ctlLast4.joinToString(",")).append(',').append(input.objectiveCtl)
        append("|monotony=").append(input.monotonyLast4.joinToString(","))
        append("|race=").append(input.lastRaceOrTimeTrialDate).append(',').append(input.hasValidTimeTrial30Min)
    }

    private const val WINDOW_WEEKS = 6
    private const val MIN_STRONG_WEEKS = 4
    private const val STRONG_WEEK_MIN = 0.80
    private const val MIN_EFFORTS = 3
    private const val MIN_EFFORT_SEC = 20 * 60
    private const val MAX_EFFORT_SEC = 35 * 60
    private const val RECENT_DAYS = 56L
    private const val CALIBRATION_RECENT_DAYS = 56L
    private const val ILLNESS_WINDOW_DAYS = 14L
    private const val LONG_RUN_60_MIN = 60
    private const val LONG_RUN_90_MIN = 90
    private const val CTL_WEEKS = 4
    private const val MONOTONY_WEEKS = 4
    private const val MONOTONY_MAX = 2.0

    private val DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    val digits = "0123456789abcdef"
    val hex = CharArray(digest.size * 2)
    digest.forEachIndexed { index, byte ->
        val unsigned = byte.toInt() and 0xFF
        hex[index * 2] = digits[unsigned ushr 4]
        hex[index * 2 + 1] = digits[unsigned and 0x0F]
    }
    return String(hex)
}
