package com.meucoach.core.domain.prescription

import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.domain.zones.HeartRateZoneCalculator
import com.meucoach.core.domain.zones.PaceZoneCalculator
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.HeartRateZone
import com.meucoach.core.model.HeatLevel
import com.meucoach.core.model.Pace
import com.meucoach.core.model.PaceZone
import com.meucoach.core.model.ReadinessLevel
import java.time.LocalDate
import kotlin.math.roundToInt

enum class WorkoutType {
    REST,
    EASY,
    LONG,
    QUALITY,
}

data class PaceTarget(val fast: Pace, val slow: Pace) {
    init {
        require(fast.secondsPerKm <= slow.secondsPerKm) { "Fast pace must be <= slow pace" }
    }
}

data class HeartRateTarget(val minBpm: Int, val maxBpm: Int?) {
    init {
        require(minBpm >= 0) { "Minimum heart rate must not be negative" }
        require(maxBpm == null || maxBpm >= minBpm) { "Maximum heart rate must be >= minimum" }
    }
}

data class WorkoutRecommendation(
    val type: WorkoutType,
    val title: String,
    val durationMin: Int?,
    val distanceKm: Double?,
    val pace: PaceTarget?,
    val heartRate: HeartRateTarget?,
    val rationale: String,
    val ruleIds: List<String>,
    val evidenceIds: List<String>,
    val confidence: DataConfidence,
) {
    init {
        require(title.isNotBlank()) { "Workout title must not be blank" }
        require(ruleIds.isNotEmpty()) { "Recommendation must cite at least one rule" }
        require(evidenceIds.isNotEmpty()) { "Recommendation must cite at least one evidence" }
        require(rationale.isNotBlank()) { "Recommendation must have a rationale" }
    }
}

data class PrescriptionInput(
    val profile: AthleteProfile,
    val readiness: ReadinessResult,
    val load: LoadSnapshot? = null,
    val today: LocalDate,
    val heatLevel: HeatLevel = HeatLevel.NONE,
)

/**
 * Conservative day-level prescription.
 *
 * Safety and readiness override everything. Without calibration the app
 * prescribes easy running by RPE instead of inventing pace or HR targets.
 */
class WorkoutRecommender {
    fun recommend(input: PrescriptionInput): WorkoutRecommendation {
        val readiness = input.readiness
        val illness = ReadinessFlag.ILLNESS_OR_INJURY in readiness.flags
        if (illness || readiness.level == ReadinessLevel.RED) {
            return restRecommendation(illness)
        }

        val available = input.profile.weeklyAvailability
        val minutes = available.minutesOn(input.today.dayOfWeek)
        if (available.minutesByDay.isNotEmpty() && minutes == 0) {
            return WorkoutRecommendation(
                type = WorkoutType.REST,
                title = "Descanso",
                durationMin = null,
                distanceKm = null,
                pace = null,
                heartRate = null,
                rationale = "Hoje não há disponibilidade registrada. Descansar também é treino.",
                ruleIds = listOf("R-PLAN-01", "R-DATA-01"),
                evidenceIds = listOf("EV-SEILER-2010"),
                confidence = DataConfidence.HIGH,
            )
        }

        val acwrHigh = (input.load?.acwr ?: 0.0) > ACWR_ALERT
        val shortSleep = ReadinessFlag.SHORT_SLEEP in readiness.flags
        val noQuality = ReadinessFlag.NO_QUALITY in readiness.flags
        val conservative = readiness.level != ReadinessLevel.GREEN || noQuality || shortSleep || acwrHigh

        val duration = minutes.takeIf { it > 0 }?.coerceIn(MIN_EASY_MIN, MAX_EASY_MIN) ?: DEFAULT_EASY_MIN
        val availabilityMissing = available.minutesByDay.isEmpty()
        val heat = input.heatLevel == HeatLevel.HOT
        val paceTarget = easyPace(input.profile)?.let { target ->
            if (heat) target.slowerBy(HEAT_PACE_FACTOR) else target
        }
        val heartRateTarget = easyHeartRate(input.profile)
        val calibrationConfidence = when {
            paceTarget != null && heartRateTarget != null -> DataConfidence.HIGH
            paceTarget != null || heartRateTarget != null -> DataConfidence.MEDIUM
            else -> DataConfidence.LOW
        }
        val confidence = combine(readiness.confidence, calibrationConfidence)
            .let { base -> if (availabilityMissing) downgrade(base) else base }
            .let { base -> if (heat) downgrade(base) else base }

        val rules = buildList {
            add("R-ZONE-01")
            if (availabilityMissing) add("R-DATA-01")
            if (readiness.level == ReadinessLevel.AMBER || noQuality) add("R-READY-03")
            if (shortSleep) add("R-READY-04")
            if (acwrHigh) add("R-LOAD-03")
            if (heat) add("R-PLAN-07")
            if (heat && input.profile.treadmillAccess) add("R-PLAN-08")
            if (paceTarget == null && heartRateTarget == null) add("R-CAL-02")
        }
        val evidence = buildList {
            add("EV-SEILER-2010")
            add("EV-DANIELS-2014")
            if (shortSleep || readiness.level == ReadinessLevel.AMBER) add("EV-SAW-2016")
            if (acwrHigh) add("EV-GABBETT-2016")
            if (heat) add("EV-ELY-2007")
            if (paceTarget == null && heartRateTarget == null) add("EV-FRIEL-2018")
        }
        val rationale = when {
            shortSleep -> "Sono abaixo de 6 h: hoje é fácil, sem qualidade. " + easyHint(paceTarget, heartRateTarget)

            acwrHigh ->
                "ACWR alto: não aumente a carga. Fique no fácil e revise a semana. " +
                    easyHint(paceTarget, heartRateTarget)

            readiness.level == ReadinessLevel.AMBER || noQuality ->
                "Prontidão âmbar: hoje é fácil, sem qualidade. " + easyHint(paceTarget, heartRateTarget)

            else -> "Base aeróbica em Z2, conversável do início ao fim. " + easyHint(paceTarget, heartRateTarget)
        } + heatHint(heat, input.profile.treadmillAccess, paceTarget != null)
        val distance = paceTarget?.let { target ->
            val secondsPerKm = (target.fast.secondsPerKm + target.slow.secondsPerKm) / 2.0
            (duration * 60.0 / secondsPerKm).roundToOneDecimal()
        }

        return WorkoutRecommendation(
            type = WorkoutType.EASY,
            title = if (conservative) "Fácil, sem pressa" else "Fácil em Z2",
            durationMin = duration,
            distanceKm = distance,
            pace = paceTarget,
            heartRate = heartRateTarget,
            rationale = rationale,
            ruleIds = rules.distinct(),
            evidenceIds = evidence.distinct(),
            confidence = confidence,
        )
    }

    private fun restRecommendation(illness: Boolean) = WorkoutRecommendation(
        type = WorkoutType.REST,
        title = "Descanso",
        durationMin = null,
        distanceKm = null,
        pace = null,
        heartRate = null,
        rationale = if (illness) {
            "Sinal de doença ou lesão: sem treino de qualidade hoje. Se persistir, procure avaliação."
        } else {
            "Prontidão vermelha: o corpo pede descanso. Não reponha o treino de outra forma."
        },
        ruleIds = if (illness) listOf("R-SAFE-01", "R-READY-03") else listOf("R-READY-03"),
        evidenceIds = listOf("EV-SAW-2016"),
        confidence = DataConfidence.HIGH,
    )

    private fun easyPace(profile: AthleteProfile): PaceTarget? {
        val threshold = profile.thresholdPace ?: return null
        val range = PaceZoneCalculator.ranges(threshold).first { it.zone == PaceZone.E }
        return PaceTarget(
            fast = Pace.fromSecondsPerKm(range.minSecPerKm),
            slow = Pace.fromSecondsPerKm(range.maxSecPerKm),
        )
    }

    private fun easyHeartRate(profile: AthleteProfile): HeartRateTarget? {
        val lthr = profile.lactateThresholdHeartRate ?: return null
        val range = HeartRateZoneCalculator.ranges(lthr).first { it.zone == HeartRateZone.Z2 }
        return HeartRateTarget(minBpm = range.minBpm, maxBpm = range.maxBpm)
    }

    private fun easyHint(pace: PaceTarget?, heartRate: HeartRateTarget?): String = when {
        pace != null && heartRate != null ->
            "Alvo ${pace.fast.format()}–${pace.slow.format()}/km · FC ${heartRate.minBpm}–${heartRate.maxBpm}."

        pace != null -> "Alvo ${pace.fast.format()}–${pace.slow.format()}/km."

        heartRate != null -> "Alvo de FC ${heartRate.minBpm}–${heartRate.maxBpm} bpm."

        else -> "Use RPE: esforço conversável; calibre o limiar para receber pace e FC."
    }

    /**
     * Self-reported heat maps to the conservative end of the 3–8% range
     * (`R-PLAN-07`). FC and RPE remain the main guide.
     */
    private fun heatHint(heat: Boolean, treadmillAccess: Boolean, hasPaceTarget: Boolean): String {
        if (!heat) return ""
        val treadmill = if (treadmillAccess) " A esteira também é válida hoje." else ""
        val percent = ((HEAT_PACE_FACTOR - 1.0) * 100).roundToInt()
        val pace = if (hasPaceTarget) {
            "pace alvo $percent% mais lento, sem mudar a FC; "
        } else {
            "reduza o ritmo pelo esforço, sem mudar a FC; "
        }
        return " Calor informado: $pace" +
            "use FC e RPE como guia e prefira o início da manhã.$treadmill"
    }

    private fun PaceTarget.slowerBy(factor: Double): PaceTarget = PaceTarget(
        fast = Pace.fromSecondsPerKm((fast.secondsPerKm * factor).roundToInt()),
        slow = Pace.fromSecondsPerKm((slow.secondsPerKm * factor).roundToInt()),
    )

    private fun combine(readiness: DataConfidence, calibration: DataConfidence): DataConfidence {
        val rank = mapOf(DataConfidence.LOW to 0, DataConfidence.MEDIUM to 1, DataConfidence.HIGH to 2)
        val minimum = minOf(rank.getValue(readiness), rank.getValue(calibration))
        return rank.entries.first { it.value == minimum }.key
    }

    private fun downgrade(confidence: DataConfidence): DataConfidence = when (confidence) {
        DataConfidence.HIGH -> DataConfidence.MEDIUM
        DataConfidence.MEDIUM, DataConfidence.LOW -> DataConfidence.LOW
    }

    private fun Double.roundToOneDecimal(): Double = (this * 10).roundToInt() / 10.0

    private companion object {
        const val ACWR_ALERT = 1.5
        const val MIN_EASY_MIN = 20
        const val MAX_EASY_MIN = 60
        const val DEFAULT_EASY_MIN = 30
        const val HEAT_PACE_FACTOR = 1.03
    }
}
