package com.meucoach.core.domain.story

import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.zones.HeartRateZoneCalculator
import com.meucoach.core.domain.zones.HrZoneDistribution
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.HeartRateZone
import kotlin.math.roundToInt

/** One-word judgement shown on a km card and at the closing frame. */
enum class StoryVerdictTone {
    ON_TARGET,
    TOO_HARD,
    TOO_EASY,
}

/** Average effort of one kilometer, as measured by the story splits. */
data class StoryKmEffort(val km: Int, val averageHeartRateBpm: Int?) {
    init {
        require(km > 0) { "Split kilometer must be positive" }
    }
}

/** Per-km stamp. A null tone means the km had no usable heart rate. */
data class StoryKmStamp(val km: Int, val tone: StoryVerdictTone?)

/**
 * Deterministic reading of one run against the prescribed session.
 *
 * The story never invents a target: when there is no planned session, no LTHR
 * calibration or no heart rate, [tone] stays null and the text says so. The
 * verdict is based on the same zones and 80/20 reasoning as the engine
 * (`R-ZONE-01`, `R-PLAN-06`), not on an opinion.
 */
data class StoryVerdict(
    val tone: StoryVerdictTone?,
    val headline: String,
    val proof: String?,
    val coachLine: String,
    val onTargetPercent: Int?,
    val targetZones: List<HeartRateZone>,
    val stamps: List<StoryKmStamp>,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
    val confidence: DataConfidence,
) {
    init {
        require(headline.isNotBlank()) { "Verdict headline must not be blank" }
        require(coachLine.isNotBlank()) { "Verdict coach line must not be blank" }
        require(tone != null || onTargetPercent == null) {
            "A verdict without a tone must not report a percentage"
        }
    }
}

/**
 * Turns the prescribed session plus measured heart-rate zones into the story
 * verdict: one stamp per km, one closing headline and one coach sentence.
 *
 * Easy days target Z2 (`R-ZONE-01`); long runs accept Z1–Z2; quality days
 * target Z4–Z5. A session counts as on target when at least
 * [ON_TARGET_MIN_PERCENT] of the measured time sits inside the target zones.
 * Below that, the side with more time decides between "strong" and "light" —
 * never "wrong" (`PRODUCT.md` §5.5). Ties prefer the gentler reading.
 */
object StoryVerdictCalculator {
    const val ON_TARGET_MIN_PERCENT = 70

    private val EASY_ZONES = listOf(HeartRateZone.Z2)
    private val LONG_ZONES = listOf(HeartRateZone.Z1, HeartRateZone.Z2)
    private val QUALITY_ZONES = listOf(HeartRateZone.Z4, HeartRateZone.Z5)

    private val VERDICT_RULES = listOf("R-ZONE-01", "R-PLAN-06")
    private val VERDICT_EVIDENCE = listOf("EV-FRIEL-2018", "EV-SEILER-2010")

    fun calculate(
        session: PlannedSession?,
        distribution: HrZoneDistribution?,
        lthrBpm: Int?,
        efforts: List<StoryKmEffort> = emptyList(),
        nextSession: PlannedSession? = null,
    ): StoryVerdict {
        val targetZones = session?.let { targetZones(it.type) }.orEmpty()
        if (session == null || lthrBpm == null || targetZones.isEmpty()) {
            return noTarget()
        }

        val stamps = efforts.map { effort ->
            StoryKmStamp(
                km = effort.km,
                tone = effort.averageHeartRateBpm?.let { bpm ->
                    stampFor(bpm = bpm, lthrBpm = lthrBpm, targetZones = targetZones)
                },
            )
        }
        val measuredTone = distribution?.let { toneFromDistribution(it, targetZones) }
            ?: toneFromStamps(stamps)
        if (measuredTone == null) {
            return noHeartRate(targetZones = targetZones, stamps = stamps)
        }

        val percent = distribution?.let { percentInZones(it, targetZones) }
            ?: percentFromStamps(stamps)
        val headline = headlineFor(session.type, measuredTone)
        val nextStep = nextStep(session, nextSession)
        val coachLine = if (nextStep == null) "$headline." else "$headline. $nextStep"
        return StoryVerdict(
            tone = measuredTone,
            headline = headline,
            proof = percent?.let { "$it% no alvo" },
            coachLine = coachLine,
            onTargetPercent = percent,
            targetZones = targetZones,
            stamps = stamps,
            ruleIds = VERDICT_RULES,
            evidenceIds = VERDICT_EVIDENCE,
            confidence = if (distribution != null) DataConfidence.HIGH else DataConfidence.MEDIUM,
        )
    }

    /** Zones the prescribed session asks for. Empty means "no target". */
    fun targetZones(type: WorkoutType): List<HeartRateZone> = when (type) {
        WorkoutType.EASY -> EASY_ZONES
        WorkoutType.LONG -> LONG_ZONES
        WorkoutType.QUALITY -> QUALITY_ZONES
        WorkoutType.REST -> emptyList()
    }

    private fun stampFor(bpm: Int, lthrBpm: Int, targetZones: List<HeartRateZone>): StoryVerdictTone {
        val zone = HeartRateZoneCalculator.zoneFor(bpm, lthrBpm)
        val min = targetZones.minOf { it.number }
        val max = targetZones.maxOf { it.number }
        return when {
            zone.number in min..max -> StoryVerdictTone.ON_TARGET
            zone.number < min -> StoryVerdictTone.TOO_EASY
            else -> StoryVerdictTone.TOO_HARD
        }
    }

    private fun toneFromDistribution(
        distribution: HrZoneDistribution,
        targetZones: List<HeartRateZone>,
    ): StoryVerdictTone {
        val percent = percentInZones(distribution, targetZones)
        if (percent >= ON_TARGET_MIN_PERCENT) return StoryVerdictTone.ON_TARGET
        val max = targetZones.maxOf { it.number }
        val min = targetZones.minOf { it.number }
        val above = distribution.zones
            .filter { it.zone.number > max }
            .sumOf { it.durationSec }
        val below = distribution.zones
            .filter { it.zone.number < min }
            .sumOf { it.durationSec }
        return if (above > below) StoryVerdictTone.TOO_HARD else StoryVerdictTone.TOO_EASY
    }

    private fun toneFromStamps(stamps: List<StoryKmStamp>): StoryVerdictTone? {
        val measured = stamps.mapNotNull { it.tone }
        if (measured.isEmpty()) return null
        val onTarget = measured.count { it == StoryVerdictTone.ON_TARGET }
        if (onTarget * 100 / measured.size >= ON_TARGET_MIN_PERCENT) return StoryVerdictTone.ON_TARGET
        val hard = measured.count { it == StoryVerdictTone.TOO_HARD }
        val easy = measured.count { it == StoryVerdictTone.TOO_EASY }
        return if (hard > easy) StoryVerdictTone.TOO_HARD else StoryVerdictTone.TOO_EASY
    }

    private fun percentFromStamps(stamps: List<StoryKmStamp>): Int? {
        val measured = stamps.mapNotNull { it.tone }
        if (measured.isEmpty()) return null
        val onTarget = measured.count { it == StoryVerdictTone.ON_TARGET }
        return (onTarget * 100.0 / measured.size).roundToInt()
    }

    private fun percentInZones(distribution: HrZoneDistribution, targetZones: List<HeartRateZone>): Int =
        (targetZones.sumOf { distribution.durationOf(it) } * 100.0 / distribution.totalSec).roundToInt()

    private fun headlineFor(type: WorkoutType, tone: StoryVerdictTone): String = when (tone) {
        StoryVerdictTone.ON_TARGET -> when (type) {
            WorkoutType.EASY -> "Fácil bem feito"
            WorkoutType.LONG -> "Longão bem feito"
            WorkoutType.QUALITY -> "Qualidade completa"
            WorkoutType.REST -> "Treino feito"
        }

        StoryVerdictTone.TOO_HARD -> when (type) {
            WorkoutType.EASY -> "Forte demais hoje"
            WorkoutType.LONG -> "Longão forte demais"
            WorkoutType.QUALITY -> "Qualidade acima do alvo"
            WorkoutType.REST -> "Forte demais hoje"
        }

        StoryVerdictTone.TOO_EASY -> when (type) {
            WorkoutType.EASY -> "Leve demais hoje"
            WorkoutType.LONG -> "Longão leve demais"
            WorkoutType.QUALITY -> "Qualidade abaixo do alvo"
            WorkoutType.REST -> "Leve demais hoje"
        }
    }

    /**
     * Next step from the stored week. "Amanhã" only when the next session is
     * literally the following day; otherwise the line says "Próximo".
     */
    private fun nextStep(current: PlannedSession, next: PlannedSession?): String? {
        if (next == null) return null
        val prefix = if (next.date == current.date.plusDays(1)) "Amanhã" else "Próximo"
        val body = when (next.type) {
            WorkoutType.REST -> "descanso"
            WorkoutType.EASY -> next.durationMin?.let { "$it min leve" } ?: "treino leve"
            WorkoutType.LONG -> next.durationMin?.let { "longão de $it min" } ?: "longão"
            WorkoutType.QUALITY -> next.durationMin?.let { "qualidade de $it min" } ?: "qualidade"
        }
        return "$prefix: $body."
    }

    private fun noTarget() = StoryVerdict(
        tone = null,
        headline = "Corrida feita",
        proof = null,
        coachLine = "Cada corrida conta.",
        onTargetPercent = null,
        targetZones = emptyList(),
        stamps = emptyList(),
        ruleIds = emptyList(),
        evidenceIds = emptyList(),
        confidence = DataConfidence.LOW,
    )

    private fun noHeartRate(targetZones: List<HeartRateZone>, stamps: List<StoryKmStamp>) = StoryVerdict(
        tone = null,
        headline = "Corrida feita",
        proof = null,
        coachLine = "Cada corrida conta.",
        onTargetPercent = null,
        targetZones = targetZones,
        stamps = stamps,
        ruleIds = emptyList(),
        evidenceIds = emptyList(),
        confidence = DataConfidence.LOW,
    )
}
