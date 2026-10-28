package com.meucoach.core.domain.zones

import com.meucoach.core.model.Pace
import com.meucoach.core.model.PaceZone
import kotlin.math.abs
import kotlin.math.roundToInt

data class PaceZoneRange(val zone: PaceZone, val minSecPerKm: Int, val maxSecPerKm: Int) {
    init {
        require(minSecPerKm > 0) { "Minimum pace must be positive" }
        require(maxSecPerKm >= minSecPerKm) { "Maximum pace must be >= minimum" }
    }

    fun contains(secondsPerKm: Int): Boolean = secondsPerKm in minSecPerKm..maxSecPerKm
}

object PaceZoneCalculator {
    private data class ZoneSpec(
        val zone: PaceZone,
        val fastFactor: Double,
        val slowFactor: Double,
        val centerFactor: Double,
    )

    private val specs =
        listOf(
            ZoneSpec(zone = PaceZone.E, fastFactor = 1.25, slowFactor = 1.40, centerFactor = 1.325),
            ZoneSpec(zone = PaceZone.M, fastFactor = 1.10, slowFactor = 1.15, centerFactor = 1.125),
            ZoneSpec(zone = PaceZone.T, fastFactor = 1.00, slowFactor = 1.03, centerFactor = 1.015),
            ZoneSpec(zone = PaceZone.I, fastFactor = 0.92, slowFactor = 0.95, centerFactor = 0.935),
            ZoneSpec(zone = PaceZone.R, fastFactor = 0.85, slowFactor = 0.90, centerFactor = 0.875),
        )

    fun ranges(thresholdPace: Pace): List<PaceZoneRange> {
        require(thresholdPace.secondsPerKm in 150..900) { "Threshold pace out of supported range" }

        return specs.map { spec ->
            PaceZoneRange(
                zone = spec.zone,
                minSecPerKm = (thresholdPace.secondsPerKm * spec.fastFactor).roundToInt(),
                maxSecPerKm = (thresholdPace.secondsPerKm * spec.slowFactor).roundToInt(),
            )
        }
    }

    /**
     * Classificação por proximidade do centro da faixa-alvo. As faixas de prescrição
     * têm gaps propositais; o pace deve cair na zona de treino mais próxima.
     */
    fun zoneFor(pace: Pace, thresholdPace: Pace): PaceZone {
        require(thresholdPace.secondsPerKm in 150..900) { "Threshold pace out of supported range" }
        val ratio = pace.secondsPerKm.toDouble() / thresholdPace.secondsPerKm
        return specs.minBy { spec -> abs(ratio - spec.centerFactor) }.zone
    }
}
