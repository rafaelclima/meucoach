package com.meucoach.app.application

import com.meucoach.app.time.TimeProvider
import com.meucoach.app.time.weekStart
import com.meucoach.app.ui.calibration.recentEffortActivities
import com.meucoach.app.ui.calibration.toCalibrationEfforts
import com.meucoach.core.data.repository.CheckInRepository
import com.meucoach.core.data.repository.ProfileRepository
import com.meucoach.core.data.repository.RaceEventRepository
import com.meucoach.core.data.repository.TrainingPlanRepository
import com.meucoach.core.domain.calibration.CalibrationTier
import com.meucoach.core.domain.load.LoadDataState
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.load.TrainingLoadCalculator
import com.meucoach.core.domain.prescription.LevelCalibration
import com.meucoach.core.domain.prescription.LevelGraduationInput
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.RaceStatus
import com.meucoach.core.model.VdotSource
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Monta a entrada pura do [LevelGraduationEngine] a partir do estado
 * persistido (ADR 0042 §3.2). Nada aqui decide treino: só coleta semanas,
 * esforços com FC, calibração, prontidão, doença/lesão de 14 dias, CTL e
 * monotonia semanais e prova/TT — o motor puro faz o resto.
 *
 * Convenções (conservadoras, documentadas):
 * - semanas: 6 anteriores fechadas (a parcial atual não pune a consistência);
 * - calibração: Tier A = `PERFORMANCE` com data (o motor valida 56 dias);
 *   Tier B = `TRAINING_ESTIMATE` salva explicitamente (confirmação);
 * - CTL/monotonia: 4 blocos de 7 dias dos `daily`/`ctlSeries` do snapshot;
 *   monotonia nula bloqueia o avançado, como no motor;
 * - `objectiveCtl` exige [LoadDataState.KNOWN] objetivo (sRPE nunca libera);
 * - prova: maior data de evento `COMPLETED` (o motor valida 56 dias);
 * - TT de 30 min válido: estimativa Tier B confirmada cuja referência tem
 *   25–35 min e até 56 dias; sem isso, só a prova conta.
 */
class LevelGraduationContextBuilder @Inject constructor(
    private val profiles: ProfileRepository,
    private val plans: TrainingPlanRepository,
    private val checkIns: CheckInRepository,
    private val signals: TrainingSignalSource,
    private val raceEvents: RaceEventRepository,
    private val timeProvider: TimeProvider,
) {
    suspend fun build(): LevelGraduationInput? {
        val profile = profiles.getProfile() ?: return null
        val now = timeProvider.now()
        val zone = runCatching { ZoneId.of(profile.timezone) }.getOrNull() ?: timeProvider.zoneId.value
        val today = now.atZone(zone).toLocalDate()
        val weekStart = today.weekStart()
        val recentWeeks = plans.getWeeks((1L..6L).map { weekStart.minusWeeks(it) })
        val snapshot = signals.snapshot(profile, today, now)
        val efforts = recentEffortActivities(snapshot.activities, zone, today).toCalibrationEfforts(zone)
        val illnessDates = checkIns.getAll().filter { it.illnessOrInjury }.map { it.date }.toSet()
        val (ctlLast4, monotonyLast4, objectiveCtl) = loadSeries(snapshot.load)
        val lastRace = raceEvents
            .listAll()
            .filter { it.status == RaceStatus.COMPLETED }
            .map { it.targetDate }
            .filter { !it.isAfter(today) }
            .maxOrNull()
        return LevelGraduationInput(
            profile = profile,
            recentWeeks = recentWeeks,
            strongRuns = VolumeReferenceProjection.build(
                plans = recentWeeks,
                activities = snapshot.activities,
                zoneId = zone,
            ).strongRuns,
            efforts = efforts,
            calibration = calibrationFor(profile),
            readiness = snapshot.readiness,
            illnessOrInjuryDates = illnessDates,
            ctlLast4 = ctlLast4,
            objectiveCtl = objectiveCtl,
            monotonyLast4 = monotonyLast4,
            lastRaceOrTimeTrialDate = lastRace,
            hasValidTimeTrial30Min = hasValidTimeTrial30Min(profile, today),
            today = today,
        )
    }

    private fun calibrationFor(profile: AthleteProfile): LevelCalibration = when (profile.vdotSource) {
        VdotSource.PERFORMANCE -> LevelCalibration(
            tier = CalibrationTier.A,
            calibratedAt = profile.vdotCalibratedAt,
        )

        VdotSource.TRAINING_ESTIMATE -> LevelCalibration(
            tier = CalibrationTier.B,
            calibratedAt = profile.vdotCalibratedAt,
            tierBConfirmed = true,
        )

        null -> LevelCalibration(tier = null, calibratedAt = null)
    }

    private fun loadSeries(load: LoadSnapshot): Triple<List<Double>, List<Double?>, Boolean> {
        val daily = load.daily.map { it.load }
        val ctlSeries = load.ctlSeries
        if (daily.size < FOUR_WEEKS_DAYS || ctlSeries.size < FOUR_WEEKS_DAYS) {
            return Triple(emptyList(), emptyList(), false)
        }
        val weeks = daily.takeLast(FOUR_WEEKS_DAYS).chunked(WEEK_DAYS)
        val monotony = weeks.map { TrainingLoadCalculator.monotony(it) }
        val ctl = ctlSeries.takeLast(FOUR_WEEKS_DAYS).chunked(WEEK_DAYS).map { it.last() }
        return Triple(ctl, monotony, load.objectiveDataState == LoadDataState.KNOWN)
    }

    private fun hasValidTimeTrial30Min(profile: AthleteProfile, today: LocalDate): Boolean {
        if (profile.vdotSource != VdotSource.TRAINING_ESTIMATE) return false
        val calibratedAt = profile.vdotCalibratedAt ?: return false
        if (calibratedAt.isAfter(today)) return false
        if (ChronoUnit.DAYS.between(calibratedAt, today) > RECENT_DAYS) return false
        val referenceSec = profile.vdotReferenceDurationSec ?: return false
        return referenceSec in MIN_TT_SEC..MAX_TT_SEC
    }

    private companion object {
        const val WEEK_DAYS = 7
        const val FOUR_WEEKS_DAYS = 28
        const val RECENT_DAYS = 56L
        const val MIN_TT_SEC = 25 * 60
        const val MAX_TT_SEC = 35 * 60
    }
}
