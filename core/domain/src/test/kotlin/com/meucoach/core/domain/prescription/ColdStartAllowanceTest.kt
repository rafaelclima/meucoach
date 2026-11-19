package com.meucoach.core.domain.prescription

import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Experience
import com.meucoach.core.model.Sex
import com.meucoach.core.model.VdotSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.LocalDate

class ColdStartAllowanceTest {
    private val today = LocalDate.of(2026, 11, 19)

    @Test
    fun `recent performance VDOT is valid`() {
        val allowance = ColdStartAllowance(
            vdot = 31.0,
            vdotSource = VdotSource.PERFORMANCE,
            calibratedAt = today.minusDays(10),
            today = today,
        )

        assertThat(allowance.isValid()).isTrue()
    }

    @Test
    fun `training estimate VDOT is valid`() {
        val allowance = ColdStartAllowance(
            vdot = 30.0,
            vdotSource = VdotSource.TRAINING_ESTIMATE,
            calibratedAt = today.minusDays(20),
            today = today,
        )

        assertThat(allowance.isValid()).isTrue()
    }

    @Test
    fun `stale calibration beyond 56 days is invalid`() {
        val allowance = ColdStartAllowance(
            vdot = 31.0,
            vdotSource = VdotSource.PERFORMANCE,
            calibratedAt = today.minusDays(57),
            today = today,
        )

        assertThat(allowance.isValid()).isFalse()
    }

    @Test
    fun `boundary 56 days is still valid`() {
        val allowance = ColdStartAllowance(
            vdot = 31.0,
            vdotSource = VdotSource.PERFORMANCE,
            calibratedAt = today.minusDays(56),
            today = today,
        )

        assertThat(allowance.isValid()).isTrue()
    }

    @Test
    fun `missing provenance is invalid`() {
        val allowance = ColdStartAllowance(
            vdot = 31.0,
            vdotSource = null,
            calibratedAt = today,
            today = today,
        )

        assertThat(allowance.isValid()).isFalse()
    }

    @Test
    fun `legacy profile without VDOT is invalid`() {
        val profile = profile(vdot = null, source = null, calibratedAt = null)

        assertThat(ColdStartAllowance.fromProfile(profile, today).isValid()).isFalse()
    }

    @Test
    fun `fromProfile maps recent VDOT`() {
        val profile = profile(
            vdot = 31.0,
            source = VdotSource.PERFORMANCE,
            calibratedAt = today.minusDays(5),
        )

        val allowance = ColdStartAllowance.fromProfile(profile, today)

        assertThat(allowance.isValid()).isTrue()
    }

    private fun profile(
        vdot: Double? = 31.0,
        source: VdotSource? = VdotSource.PERFORMANCE,
        calibratedAt: LocalDate? = today.minusDays(5),
    ) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        experience = Experience.INTERMEDIATE,
        timezone = "America/Sao_Paulo",
        vdot = vdot,
        vdotSource = source,
        vdotCalibratedAt = calibratedAt,
        vdotReferenceDistanceM = if (source == VdotSource.PERFORMANCE && vdot != null) 5_000.0 else null,
        vdotReferenceDurationSec = if (source == VdotSource.PERFORMANCE && vdot != null) 1_859 else null,
    )
}
