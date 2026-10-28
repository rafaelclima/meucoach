package com.meucoach.core.model

import java.util.Locale
import kotlin.math.roundToInt

@JvmInline
value class Distance(val meters: Double) {
    init {
        require(meters.isFinite()) { "Distance must be finite" }
        require(meters >= 0.0) { "Distance must not be negative" }
    }

    val kilometers: Double
        get() = meters / METERS_PER_KILOMETER

    companion object {
        private const val METERS_PER_KILOMETER = 1_000.0

        fun ofKilometers(kilometers: Double): Distance {
            require(kilometers.isFinite()) { "Kilometers must be finite" }
            return Distance(kilometers * METERS_PER_KILOMETER)
        }
    }
}

@JvmInline
value class Pace private constructor(val secondsPerKm: Int) {
    init {
        require(secondsPerKm > 0) { "Pace must be positive" }
    }

    val minutesPerKm: Double
        get() = secondsPerKm / 60.0

    fun format(): String = String.format(Locale.US, "%d:%02d", secondsPerKm / 60, secondsPerKm % 60)

    fun speedMetersPerSecond(): Double = 1_000.0 / secondsPerKm

    operator fun compareTo(other: Pace): Int = secondsPerKm.compareTo(other.secondsPerKm)

    companion object {
        fun fromSecondsPerKm(secondsPerKm: Int): Pace = Pace(secondsPerKm)

        fun of(minutes: Int, seconds: Int): Pace {
            require(minutes >= 0) { "Minutes must not be negative" }
            require(seconds in 0..59) { "Seconds must be between 0 and 59" }
            return Pace(minutes * 60 + seconds)
        }

        fun fromSpeedMetersPerSecond(speedMetersPerSecond: Double): Pace {
            require(speedMetersPerSecond.isFinite()) { "Speed must be finite" }
            require(speedMetersPerSecond > 0.0) { "Speed must be positive" }
            return Pace((1_000.0 / speedMetersPerSecond).roundToInt())
        }
    }
}
