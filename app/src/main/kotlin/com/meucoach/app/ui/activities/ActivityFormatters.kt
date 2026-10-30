package com.meucoach.app.ui.activities

import com.meucoach.core.data.db.entity.ImportJobStatus
import com.meucoach.core.data.repository.ImportJobSummary
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.Distance
import com.meucoach.core.model.Pace
import com.meucoach.core.model.Sport
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PT_BR = Locale.forLanguageTag("pt-BR")
private val DATE_TIME = DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm", PT_BR)

fun formatDistance(distance: Distance): String = String.format(PT_BR, "%.2f", distance.kilometers)

fun formatDuration(seconds: Int): String {
    require(seconds >= 0) { "Duration must not be negative" }
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remaining = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remaining)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, remaining)
    }
}

fun formatDateTime(instant: Instant, zoneId: ZoneId = ZoneId.systemDefault()): String =
    DATE_TIME.withZone(zoneId).format(instant).replace(".", "")

fun sportLabel(sport: Sport): String = when (sport) {
    Sport.RUNNING -> "Corrida"
    Sport.TREADMILL -> "Esteira"
    Sport.WALKING -> "Caminhada"
    Sport.OTHER -> "Atividade"
}

fun sourceLabel(source: ActivitySource): String = when (source) {
    ActivitySource.FIT -> "FIT"
    ActivitySource.TCX -> "TCX"
    ActivitySource.GPX -> "GPX"
    ActivitySource.HEALTH_CONNECT -> "Health Connect"
    ActivitySource.MANUAL -> "Manual"
}

fun heartRateLabel(average: Int?, max: Int?): String? = when {
    average != null && max != null -> "$average/$max"
    average != null -> "$average"
    max != null -> "máx $max"
    else -> null
}

fun importStatusLabel(status: ImportJobStatus): String = when (status) {
    ImportJobStatus.QUEUED -> "Na fila"
    ImportJobStatus.PARSING -> "Lendo"
    ImportJobStatus.DONE -> "Importado"
    ImportJobStatus.DUPLICATE -> "Duplicado"
    ImportJobStatus.FAILED -> "Falhou"
}

fun importJobDetail(job: ImportJobSummary): String = when (job.status) {
    ImportJobStatus.QUEUED -> "Aguardando processamento"
    ImportJobStatus.PARSING -> "Lendo o arquivo"
    ImportJobStatus.DONE -> "Adicionado à biblioteca"
    ImportJobStatus.DUPLICATE -> "Já fazia parte da biblioteca"
    ImportJobStatus.FAILED -> job.errorMessage ?: "Não foi possível importar"
}

fun paceLabel(pace: Pace?): String = pace?.format() ?: "—"
