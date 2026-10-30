package com.meucoach.app.ui.activitydetail

import com.meucoach.app.ui.activities.formatDistance
import com.meucoach.app.ui.activities.formatDuration
import com.meucoach.app.ui.activities.heartRateLabel
import com.meucoach.core.data.repository.StoredActivity
import com.meucoach.core.model.ActivityLap
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.Distance
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Pace
import java.util.Locale
import kotlin.math.roundToInt

private val PT_BR = Locale.forLanguageTag("pt-BR")

data class ActivityMetricUi(val label: String, val value: String, val unit: String? = null)

/** Canonical source first, then alternates, without repeating a format. */
fun activitySources(activity: StoredActivity): List<ActivitySource> =
    (listOf(activity.imported.source) + activity.alternates.map { it.source }).distinct()

fun formatDistanceMeters(meters: Double): String = formatDistance(Distance(meters))

fun detailMetrics(imported: ImportedActivity): List<ActivityMetricUi> = buildList {
    heartRateLabel(imported.averageHeartRate, imported.maxHeartRate)?.let { value ->
        add(ActivityMetricUi(label = "FC", value = value, unit = "bpm"))
    }
    imported.averageCadenceSpm?.let { cadence ->
        add(ActivityMetricUi(label = "Cadência", value = "$cadence", unit = "spm"))
    }
    imported.averagePowerW?.let { power ->
        add(ActivityMetricUi(label = "Potência", value = "$power", unit = "W"))
    }
    imported.elevationGainM?.let { gain ->
        add(ActivityMetricUi(label = "Elevação", value = String.format(PT_BR, "%.0f", gain), unit = "m"))
    }
    imported.calories?.let { calories ->
        add(ActivityMetricUi(label = "Calorias", value = "$calories", unit = "kcal"))
    }
    imported.movingTimeSec
        ?.takeIf { it != imported.durationSec }
        ?.let { moving -> add(ActivityMetricUi(label = "Movimento", value = formatDuration(moving))) }
}

/** Lap pace from the file when present, otherwise derived from lap time and distance. */
fun lapPace(lap: ActivityLap): Pace? = lap.averagePace ?: run {
    if (lap.distanceM <= 0.0 || lap.durationSec <= 0) return@run null
    Pace.fromSecondsPerKm((lap.durationSec / (lap.distanceM / 1_000.0)).roundToInt())
}

fun lapDistanceLabel(lap: ActivityLap): String = "${formatDistanceMeters(lap.distanceM)} km"
