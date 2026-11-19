package com.meucoach.app.ui.coach

import com.meucoach.core.domain.coach.CoachProgressSummary
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sex
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CoachProgressLabelsTest {
    private val today = LocalDate.of(2026, 11, 19)

    @Test
    fun `cobertura parcial avisa que e parcial`() {
        assertThat(CoachProgressLabels.coverageNote(available = 1, total = 4))
            .isEqualTo("1 de 4 semanas · parcial.")
        assertThat(CoachProgressLabels.coverageNote(available = 4, total = 4)).isNull()
    }

    @Test
    fun `adesao e volume usam os agregados`() {
        val summary = summary()

        assertThat(CoachProgressLabels.adherenceLine(summary)).isEqualTo("6 de 9 sessões do plano (67%)")
        assertThat(CoachProgressLabels.volumeLine(summary)).isEqualTo("Planejado 3h34 · feito 3h39 (102%)")
        assertThat(CoachProgressLabels.extraLine(summary)).contains("28 min")
    }

    @Test
    fun `sem dados as linhas dizem que falta base`() {
        val summary = summary().copy(
            plannedSessions = 0,
            doneSessions = 0,
            adherencePercent = null,
            plannedMinutes = 0,
            executedMinutes = 0,
            extraMinutes = 0,
            volumePercent = null,
            avgReadiness = null,
            avgSleepMinutes = null,
            easyPercent28d = null,
        )

        assertThat(CoachProgressLabels.adherenceLine(summary)).isEqualTo("Sem sessões planejadas.")
        assertThat(CoachProgressLabels.volumeLine(summary)).isEqualTo("Sem volume planejado.")
        assertThat(CoachProgressLabels.extraLine(summary)).isNull()
        assertThat(CoachProgressLabels.intensityLine(summary)).isEqualTo("Sem base de FC para medir intensidade.")
        assertThat(CoachProgressLabels.readinessLine(summary)).isEqualTo("Sem prontidão medida.")
        assertThat(CoachProgressLabels.sleepLine(summary)).isEqualTo("Sem sono medido.")
    }

    @Test
    fun `vdot recente mostra valor e origem, vencido pede rpe`() {
        val fresh = profile(calibratedAt = LocalDate.of(2026, 10, 24))
        val stale = profile(calibratedAt = LocalDate.of(2026, 8, 1))

        assertThat(CoachProgressLabels.vdotLine(fresh, today)).isEqualTo("VDOT 31 (prova em 24/10).")
        assertThat(CoachProgressLabels.vdotLine(stale, today)).isEqualTo("Sem VDOT recente — correndo por RPE.")
        assertThat(CoachProgressLabels.vdotLine(null, today)).isEqualTo("Sem VDOT recente — correndo por RPE.")
    }

    private fun summary() = CoachProgressSummary(
        availableWeeks = 4,
        plannedSessions = 9,
        doneSessions = 6,
        adherencePercent = 67,
        plannedMinutes = 214,
        executedMinutes = 219,
        extraMinutes = 28,
        volumePercent = 102,
        avgReadiness = 62,
        redDays = 1,
        avgSleepMinutes = 360,
        shortNights = 2,
        easyPercent28d = 52,
    )

    private fun profile(calibratedAt: LocalDate) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        vdot = 31.4,
        vdotSource = com.meucoach.core.model.VdotSource.PERFORMANCE,
        vdotCalibratedAt = calibratedAt,
        vdotReferenceDistanceM = 5_000.0,
        vdotReferenceDurationSec = 1_859,
        timezone = "UTC",
    )
}
