package com.meucoach.app.ui.coach

import com.meucoach.app.ui.review.WeeklyReviewFormat
import com.meucoach.core.domain.coach.CoachProgressSummary
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.VdotSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Friendly copy of the four-week rollup (PRD evolução, F4).
 *
 * The domain returns aggregates only; this formatter turns them into short
 * pt-BR lines without grades, streaks or promises. VDOT is shown only with
 * provenance and within 56 days; otherwise the line says the athlete runs
 * by RPE instead of inventing a reference.
 */
object CoachProgressLabels {
    private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

    fun coverageNote(available: Int, total: Int): String? =
        if (available < total) "$available de $total semanas · parcial." else null

    fun adherenceLine(summary: CoachProgressSummary): String {
        val percent = summary.adherencePercent ?: return "Sem sessões planejadas."
        return "${summary.doneSessions} de ${summary.plannedSessions} sessões do plano ($percent%)"
    }

    fun volumeLine(summary: CoachProgressSummary): String {
        if (summary.volumePercent == null) return "Sem volume planejado."
        return "Planejado ${WeeklyReviewFormat.minutes(summary.plannedMinutes)} · " +
            "feito ${WeeklyReviewFormat.minutes(summary.executedMinutes)} (${summary.volumePercent}%)"
    }

    fun extraLine(summary: CoachProgressSummary): String? =
        WeeklyReviewFormat.volumeExtra(summary.extraMinutes.takeIf { it > 0 })

    fun intensityLine(summary: CoachProgressSummary): String {
        val easy = summary.easyPercent28d ?: return "Sem base de FC para medir intensidade."
        return "$easy% fácil nos últimos 28 dias. O ideal é 80%."
    }

    fun readinessLine(summary: CoachProgressSummary): String {
        val avg = summary.avgReadiness ?: return "Sem prontidão medida."
        val red = if (summary.redDays == 0) {
            "nenhum dia com prontidão baixa"
        } else {
            "${summary.redDays} ${if (summary.redDays == 1) "dia com prontidão baixa" else "dias com prontidão baixa"}"
        }
        return "Prontidão média $avg · $red."
    }

    fun sleepLine(summary: CoachProgressSummary): String {
        val avg = summary.avgSleepMinutes ?: return "Sem sono medido."
        val short = if (summary.shortNights == 0) {
            "sem curta"
        } else {
            "${summary.shortNights} ${if (summary.shortNights == 1) "curta" else "curtas"}"
        }
        return "Sono médio ${WeeklyReviewFormat.minutes(avg)} · $short."
    }

    fun vdotLine(profile: AthleteProfile?, today: LocalDate): String {
        val vdot = profile?.vdot
        val calibratedAt = profile?.vdotCalibratedAt
        val source = profile?.vdotSource
        val fresh = calibratedAt != null && !calibratedAt.isBefore(today.minusDays(VDOT_FRESH_DAYS))
        if (vdot == null || source == null || !fresh) {
            return "Sem índice de aptidão recente — você corre pelo esforço percebido."
        }
        // `fresh` above already proves the date exists; `require` documents it.
        val calibrationDate = requireNotNull(calibratedAt) {
            "A fresh VDOT must carry a calibration date"
        }
        val origin = when (source) {
            VdotSource.PERFORMANCE -> "prova"
            VdotSource.TRAINING_ESTIMATE -> "estimativa de treino"
        }
        val value = vdot.roundToInt()
        return "Índice de aptidão $value ($origin em ${calibrationDate.format(dayFormat)})."
    }

    private const val VDOT_FRESH_DAYS = 56L
}
