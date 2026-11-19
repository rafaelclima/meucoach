package com.meucoach.core.domain.prescription

import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.VdotSource
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Cold-start ramp input (ADR 0053).
 *
 * A recent VDOT with provenance proves the athlete already tolerates a short
 * hard effort, so one short quality (≤30 min) may be kept even without 8
 * objective load days. It never bypasses safety/readiness/recovery gates,
 * never grows volume and never applies to BEGINNER.
 */
data class ColdStartAllowance(
    val vdot: Double?,
    val vdotSource: VdotSource?,
    val calibratedAt: LocalDate?,
    val today: LocalDate,
) {
    fun isValid(): Boolean {
        if (vdot == null || vdotSource == null || calibratedAt == null) return false
        if (!vdot.isFinite() || vdot <= 0.0) return false
        if (calibratedAt.isAfter(today)) return false
        return ChronoUnit.DAYS.between(calibratedAt, today) <= MAX_AGE_DAYS
    }

    companion object {
        /** VDOT older than this is stale and no longer proves tolerance. */
        const val MAX_AGE_DAYS = 56L

        /** Planned quality longer than this never uses the ramp. */
        const val MAX_DURATION_MIN = 30

        fun fromProfile(profile: AthleteProfile, today: LocalDate): ColdStartAllowance = ColdStartAllowance(
            vdot = profile.vdot,
            vdotSource = profile.vdotSource,
            calibratedAt = profile.vdotCalibratedAt,
            today = today,
        )
    }
}
