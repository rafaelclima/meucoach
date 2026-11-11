package com.meucoach.app.ui.activitydetail

import com.meucoach.app.ui.activities.paceLabel
import com.meucoach.core.domain.feedback.ComparisonVerdict
import com.meucoach.core.domain.feedback.SessionComparison
import com.meucoach.core.domain.feedback.SessionComparisonKind
import kotlin.math.roundToInt

/** One comparison line: the plan, the run, and no judgement. */
internal data class ComparisonRowUi(val label: String, val planned: String, val actual: String)

/**
 * Rows exist only when both sides exist. A missing value is omitted instead of
 * being shown as zero; the card then says there are not enough targets.
 */
internal fun comparisonRows(comparison: SessionComparison): List<ComparisonRowUi> {
    val plan = comparison.plan ?: return emptyList()
    return buildList {
        plan.durationMin?.let { planned ->
            add(
                ComparisonRowUi(
                    label = "Duração",
                    planned = "$planned min",
                    actual = "${comparison.actualDurationSec.toMinutes()} min",
                ),
            )
        }
        plan.targets.distanceKm
            ?.takeIf { comparison.actualDistanceM > 0.0 }
            ?.let { planned ->
                add(
                    ComparisonRowUi(
                        label = "Distância",
                        planned = "${formatDistanceMeters(planned * 1_000.0)} km",
                        actual = "${formatDistanceMeters(comparison.actualDistanceM)} km",
                    ),
                )
            }
        plan.targets.pace?.let { target ->
            comparison.actualAveragePace?.let { actual ->
                add(
                    ComparisonRowUi(
                        label = "Ritmo",
                        planned = "${paceLabel(target.fast)}–${paceLabel(target.slow)}/km",
                        actual = "${paceLabel(actual)}/km",
                    ),
                )
            }
        }
        plan.targets.heartRate?.let { target ->
            comparison.actualAverageHeartRate?.let { actual ->
                val range = target.maxBpm?.let { max -> "${target.minBpm}–$max bpm" } ?: "≥ ${target.minBpm} bpm"
                add(ComparisonRowUi(label = "FC média", planned = range, actual = "$actual bpm"))
            }
        }
        plan.targets.rpe?.let { planned ->
            comparison.actualRpe?.let { actual ->
                add(ComparisonRowUi(label = "Esforço", planned = "RPE $planned", actual = "RPE $actual"))
            }
        }
    }
}

/**
 * Only differences leave the card; matching metrics stay silent and are
 * summarised as one line. Wording is factual: it describes direction, never
 * quality or a next prescription.
 */
internal fun comparisonNotes(comparison: SessionComparison): List<String> {
    if (comparison.kind == SessionComparisonKind.FREE_RUN) return emptyList()
    return buildList {
        comparison.durationVerdict?.noteFor("Duração", comparison.durationPercentOfPlan)?.let(::add)
        comparison.distanceVerdict?.noteFor("Distância", comparison.distancePercentOfPlan)?.let(::add)
        when (comparison.paceVerdict) {
            ComparisonVerdict.ABOVE -> add("Ritmo mais rápido que o alvo")
            ComparisonVerdict.BELOW -> add("Ritmo mais lento que o alvo")
            else -> Unit
        }
        when (comparison.heartRateVerdict) {
            ComparisonVerdict.ABOVE -> add("FC média acima da faixa-alvo")
            ComparisonVerdict.BELOW -> add("FC média abaixo da faixa-alvo")
            else -> Unit
        }
        when (comparison.rpeVerdict) {
            ComparisonVerdict.ABOVE -> add("Esforço percebido acima do previsto")
            ComparisonVerdict.BELOW -> add("Esforço percebido abaixo do previsto")
            else -> Unit
        }
    }
}

/** Closing line of the card: honest empty states instead of a neutral claim. */
internal fun comparisonSummary(comparison: SessionComparison): String? = when {
    comparison.kind == SessionComparisonKind.FREE_RUN -> null
    !comparison.hasComparableTarget -> "Sem alvos suficientes para comparar."
    comparisonNotes(comparison).isEmpty() -> "Tudo dentro do planejado."
    else -> null
}

/** Actual values of a run that was not evidence of a planned session. */
internal fun freeRunSummary(comparison: SessionComparison): String {
    val distance = comparison.actualDistanceM
        .takeIf { it > 0.0 }
        ?.let { "${formatDistanceMeters(it)} km" }
        ?: "—"
    val pace = comparison.actualAveragePace?.let { "${paceLabel(it)}/km" } ?: "—"
    val heartRate = comparison.actualAverageHeartRate?.let { "$it bpm" } ?: "—"
    return "Duração ${comparison.actualDurationSec.toMinutes()} min · Distância $distance · " +
        "Ritmo $pace · FC média $heartRate"
}

private fun Int.toMinutes(): Int = (this / 60.0).roundToInt()

private fun ComparisonVerdict.noteFor(label: String, percent: Int?): String? = when (this) {
    ComparisonVerdict.ABOVE -> "$label acima do planejado${percent?.let { " ($it%)" }.orEmpty()}"
    ComparisonVerdict.BELOW -> "$label abaixo do planejado${percent?.let { " ($it%)" }.orEmpty()}"
    ComparisonVerdict.WITHIN -> null
}
