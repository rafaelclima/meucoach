package com.meucoach.core.domain.coach

import com.meucoach.core.domain.load.LoadDataState
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.CompletedRace
import com.meucoach.core.domain.prescription.RecoveryWindow
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.RaceStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class CoachGuardKind {
    RECOVERY,
    RACE_WEEK,
    TAPER,
    STALE_SOURCE,
    CALIBRATION,
    MONOTONY,
    STRAIN,
    NO_AVAILABILITY,
}

data class CoachGuard(val kind: CoachGuardKind, val title: String, val detail: String)

data class CoachGuardInputs(
    val load: LoadSnapshot,
    val races: List<RaceEvent>,
    val hasAvailability: Boolean,
    val today: LocalDate,
)

/**
 * Structural guards for the Coach tab (PRD evolução, F2).
 *
 * Read-only description of what constrains the week: recovery, race
 * proximity, stale or calibrating load, low variation and missing
 * availability. Day-level blocks (check-in, sleep, readiness) belong to the
 * next-session card (F1) and are not repeated here. It never decides load,
 * pace or planning; every guard mirrors a state the engine already holds.
 */
object CoachGuards {
    private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

    fun resolve(inputs: CoachGuardInputs): List<CoachGuard> = buildList {
        val completed = inputs.races.mapNotNull(CompletedRace::from)
        RecoveryWindow.active(completed, inputs.today)?.let { window ->
            val end = dayFormat.format(window.endDate())
            val cap = window.maxSessionMinutes(inputs.today)
            add(
                CoachGuard(
                    kind = CoachGuardKind.RECOVERY,
                    title = "Recuperação até $end.",
                    detail = "Teto de $cap min, sem qualidade nem longão.",
                ),
            )
        }

        nearestPlanned(inputs.races, inputs.today)?.let { event ->
            val days = ChronoUnit.DAYS.between(inputs.today, event.targetDate).toInt()
            val label = dayFormat.format(event.targetDate)
            if (days <= RACE_WEEK_DAYS) {
                add(
                    CoachGuard(
                        kind = CoachGuardKind.RACE_WEEK,
                        title = "Semana de prova.",
                        detail = "Micro-dose mantida até $label.",
                    ),
                )
            } else if (days <= taperDays(event.distance)) {
                add(
                    CoachGuard(
                        kind = CoachGuardKind.TAPER,
                        title = "Taper para $label.",
                        detail = "Volume reduzido, intensidade em micro-dose.",
                    ),
                )
            }
        }

        val load = inputs.load
        when (load.dataState) {
            LoadDataState.STALE -> add(
                CoachGuard(
                    kind = CoachGuardKind.STALE_SOURCE,
                    title = "Dados desatualizados.",
                    detail = "Última importação há mais de 14 dias — qualidade pausada até atualizar.",
                ),
            )

            LoadDataState.INSUFFICIENT -> add(calibration(load))

            LoadDataState.KNOWN -> {
                if (!load.acwrReliable) {
                    add(calibration(load))
                } else {
                    if (load.monotony != null && !load.monotonyOk) {
                        add(
                            CoachGuard(
                                kind = CoachGuardKind.MONOTONY,
                                title = "Semana monótona.",
                                detail = "Varie os estímulos — monotonia acima do limite.",
                            ),
                        )
                    }
                    if (load.strainSpike) {
                        add(
                            CoachGuard(
                                kind = CoachGuardKind.STRAIN,
                                title = "Pico de esforço.",
                                detail = "Carga da semana acima do dobro da base — dia leve.",
                            ),
                        )
                    }
                }
            }
        }

        if (!inputs.hasAvailability) {
            add(
                CoachGuard(
                    kind = CoachGuardKind.NO_AVAILABILITY,
                    title = "Sem dias configurados.",
                    detail = "Usando base de 30 min, confiança baixa.",
                ),
            )
        }
    }

    private fun calibration(load: LoadSnapshot) = CoachGuard(
        kind = CoachGuardKind.CALIBRATION,
        title = "Base de carga em calibração.",
        detail = "${load.objectiveDaysWithLoad} de 28 dias com carga objetiva.",
    )

    private fun nearestPlanned(races: List<RaceEvent>, today: LocalDate): RaceEvent? = races
        .filter { it.status == RaceStatus.PLANNED && !it.targetDate.isBefore(today) }
        .minByOrNull { it.targetDate }

    private fun taperDays(distance: RaceDistance): Int = when (distance) {
        RaceDistance.FIVE_K, RaceDistance.TEN_K, RaceDistance.HALF_MARATHON -> TAPER_DAYS_SHORT
        RaceDistance.MARATHON -> TAPER_DAYS_MARATHON
    }

    private const val RACE_WEEK_DAYS = 7
    private const val TAPER_DAYS_SHORT = 14
    private const val TAPER_DAYS_MARATHON = 21
}
