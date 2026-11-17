package com.meucoach.core.domain.prescription

import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.RaceDistance
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Intenção de uma semana dentro da preparação (ADR 0043 §3.1).
 *
 * [blockIndex] é a posição 1-based do bloco de fase a partir do início da
 * preparação (`BASE` = 1 …); [weeksToRace] conta as semanas até a semana da
 * prova e é `null` no caminho geral.
 *
 * [weekInPhase] é 0-based dentro da fase e alimenta a rotação determinística do
 * catálogo (ADR 0047 §2.7); [phaseWeeks] é a duração total da fase na tabela.
 */
data class MacroWeekIntent(
    val weekStart: LocalDate,
    val phase: MesoPhase,
    val blockIndex: Int,
    val weeksToRace: Int?,
    val weekInPhase: Int = 0,
    val phaseWeeks: Int = 1,
)

/**
 * Preparação resolvida pelo macro-planejador (ADR 0043, `R-PER-01`).
 *
 * Com prova dentro do horizonte, [weeks] carrega as intenções da semana atual
 * até a semana da prova; sem prova (ou prova além do horizonte/passada),
 * [isGeneral] vale e [weeks] é vazio — o gerador segue pelo mesociclo do
 * histórico (ADR 0041), nunca inventando preparação.
 */
data class MacroPlan(
    val weeks: List<MacroWeekIntent>,
    val isGeneral: Boolean,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
    val confidence: DataConfidence,
    val inputsHash: String,
)

/**
 * Macro-planejador puro da periodização por nível (ADR 0043, fatia 1).
 *
 * Distribui blocos até a prova pela tabela aprovada (5k/10k 8 semanas, meia 12,
 * maratona 16), com o taper reutilizado de [RacePlan.taperWindowDays] e `PEAK`
 * estável antes dele. O posicionamento é pela distância até a prova, então um
 * início tardio recebe fases coerentes com o que resta — as primeiras semanas
 * pendem para `BASE` por conservadorismo. Determinístico: mesmos inputs,
 * mesma preparação.
 */
object MacroPlanResolver {
    const val ENGINE_VERSION = "macro-plan-v1"

    fun resolve(race: RacePlan?, today: LocalDate): MacroPlan {
        val weekStart = weekStart(today)
        val horizon = race?.let(::horizonWeeks)
        val raceWeek = race?.let { weekStart(it.targetDate) }
        if (race == null || horizon == null || raceWeek == null || raceWeek.isBefore(weekStart)) {
            return general(today)
        }
        val weeksToRaceWeek = ChronoUnit.WEEKS.between(weekStart, raceWeek).toInt()
        if (weeksToRaceWeek >= horizon) {
            return general(today)
        }
        // A tabela é ancorada no início da preparação (semana da prova menos o
        // horizonte): a semana atual continua de onde parou, então todo bloco
        // é alcançável mesmo com horizonte parcial.
        val prepStart = raceWeek.minusWeeks((horizon - 1).toLong())
        val table = buildTable(race, horizon)
        val count = weeksToRaceWeek + 1
        val intents = (0 until count).map { index ->
            val intentWeek = weekStart.plusWeeks(index.toLong())
            val position = ChronoUnit.WEEKS.between(prepStart, intentWeek).toInt()
            val phase = table[position]
            MacroWeekIntent(
                weekStart = intentWeek,
                phase = phase,
                blockIndex = 0,
                weeksToRace = weeksToRaceWeek - index,
                weekInPhase = phasePosition(table, position),
                phaseWeeks = phaseLength(table, position),
            )
        }
        return MacroPlan(
            weeks = withBlockIndexes(intents),
            isGeneral = false,
            ruleIds = listOf(RULE_ID),
            evidenceIds = EVIDENCE_IDS,
            confidence = DataConfidence.MEDIUM,
            inputsHash = inputsHash(race, today),
        )
    }

    private fun general(today: LocalDate): MacroPlan = MacroPlan(
        weeks = emptyList(),
        isGeneral = true,
        ruleIds = listOf(RULE_ID),
        evidenceIds = EVIDENCE_IDS,
        confidence = DataConfidence.MEDIUM,
        inputsHash = inputsHash(race = null, today = today),
    )

    private fun horizonWeeks(race: RacePlan): Int = when (race.distance) {
        RaceDistance.FIVE_K, RaceDistance.TEN_K -> SHORT_HORIZON_WEEKS
        RaceDistance.HALF_MARATHON -> HALF_HORIZON_WEEKS
        RaceDistance.MARATHON -> MARATHON_HORIZON_WEEKS
    }

    private fun buildTable(race: RacePlan, horizon: Int): List<MesoPhase> {
        val taperWeeks = race.taperWindowDays / DAYS_PER_WEEK
        val peakWeeks = if (race.distance == RaceDistance.FIVE_K || race.distance == RaceDistance.TEN_K) {
            SHORT_PEAK_WEEKS
        } else {
            LONG_PEAK_WEEKS
        }
        val baseBuild = horizon - taperWeeks - peakWeeks
        val baseCount = (baseBuild + 1) / 2
        return List(baseCount) { MesoPhase.BASE } +
            List(baseBuild - baseCount) { MesoPhase.BUILD } +
            List(peakWeeks) { MesoPhase.PEAK } +
            List(taperWeeks) { MesoPhase.TAPER }
    }

    /** 0-based position of [position] inside its contiguous phase run. */
    private fun phasePosition(table: List<MesoPhase>, position: Int): Int {
        var start = position
        while (start > 0 && table[start - 1] == table[position]) start--
        return position - start
    }

    /** Number of weeks the phase at [position] occupies in the macro table. */
    private fun phaseLength(table: List<MesoPhase>, position: Int): Int {
        val phase = table[position]
        var start = position
        var end = position
        while (start > 0 && table[start - 1] == phase) start--
        while (end < table.lastIndex && table[end + 1] == phase) end++
        return end - start + 1
    }

    private fun withBlockIndexes(intents: List<MacroWeekIntent>): List<MacroWeekIntent> {
        var block = 0
        var last: MesoPhase? = null
        return intents.map { intent ->
            if (intent.phase != last) {
                block += 1
                last = intent.phase
            }
            intent.copy(blockIndex = block)
        }
    }

    private fun inputsHash(race: RacePlan?, today: LocalDate): String {
        val canonical = buildString {
            append("engine=").append(ENGINE_VERSION)
            append("|race=").append(race?.let { "${it.eventId},${it.distance.name},${it.targetDate}" } ?: "none")
            append("|today=").append(today)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        val digits = "0123456789abcdef"
        val hex = CharArray(digest.size * 2)
        digest.forEachIndexed { index, byte ->
            val unsigned = byte.toInt() and 0xFF
            hex[index * 2] = digits[unsigned ushr 4]
            hex[index * 2 + 1] = digits[unsigned and 0x0F]
        }
        return String(hex)
    }

    private fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private const val RULE_ID = "R-PER-01"
    private val EVIDENCE_IDS = listOf("EV-ISSURIN-2010", "EV-BOSQUET-2007", "EV-MUJIKA-2003")
    private const val DAYS_PER_WEEK = 7
    private const val SHORT_HORIZON_WEEKS = 8
    private const val HALF_HORIZON_WEEKS = 12
    private const val MARATHON_HORIZON_WEEKS = 16
    private const val SHORT_PEAK_WEEKS = 1
    private const val LONG_PEAK_WEEKS = 2
}
