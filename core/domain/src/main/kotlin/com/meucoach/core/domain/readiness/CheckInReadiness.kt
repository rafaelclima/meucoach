package com.meucoach.core.domain.readiness

import com.meucoach.core.model.DailyCheckIn

fun DailyCheckIn.toSubjectiveCheckIn(): SubjectiveCheckIn = SubjectiveCheckIn(
    sleepQuality = sleepQuality,
    fatigue = fatigue,
    soreness = soreness,
    stress = stress,
)

fun DailyCheckIn.toReadinessSignals(): ReadinessSignals = ReadinessSignals(
    illnessOrInjury = illnessOrInjury,
)

/** Readiness from the daily check-in alone; objective inputs stay null. */
fun DailyCheckIn.toReadinessResult(): ReadinessResult = ReadinessCalculator.calculate(
    checkIn = toSubjectiveCheckIn(),
    sleep = null,
    hrv = null,
    restingHeartRate = null,
    signals = toReadinessSignals(),
)
