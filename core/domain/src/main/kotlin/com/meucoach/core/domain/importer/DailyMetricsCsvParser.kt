package com.meucoach.core.domain.importer

import com.meucoach.core.model.DailyMetrics
import com.meucoach.core.model.DailyMetricsSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class CsvRowError(val line: Int, val message: String)

data class DailyMetricsCsvResult(
    val metrics: List<DailyMetrics>,
    val errors: List<CsvRowError>,
    val duplicateDates: Int,
)

/**
 * Parser for the app's daily metrics template:
 * `date,sleep_minutes,resting_heart_rate,hrv_rmssd,spo2`.
 *
 * Only `date` is mandatory. Missing values stay null; malformed rows are
 * reported and skipped without discarding the rest of the file.
 */
class DailyMetricsCsvParser {
    fun parse(text: String): DailyMetricsCsvResult {
        val lines = text.removePrefix("\uFEFF").lines()
        val headerLine = lines.indexOfFirst { it.isNotBlank() }
        require(headerLine >= 0) { "CSV vazio." }
        val header = lines[headerLine].split(',').map { it.trim().lowercase() }
        val dateIndex = header.indexOfFirst { it in DATE_ALIASES }
        require(dateIndex >= 0) { "Coluna de data obrigatória." }

        val indexes = ColumnIndexes(
            date = dateIndex,
            sleep = header.indexOfFirst { it in SLEEP_ALIASES }.takeIf { it >= 0 },
            restingHeartRate = header.indexOfFirst { it in RHR_ALIASES }.takeIf { it >= 0 },
            hrv = header.indexOfFirst { it in HRV_ALIASES }.takeIf { it >= 0 },
            spo2 = header.indexOfFirst { it in SPO2_ALIASES }.takeIf { it >= 0 },
        )

        val errors = mutableListOf<CsvRowError>()
        val byDate = linkedMapOf<LocalDate, DailyMetrics>()
        var duplicates = 0

        lines.drop(headerLine + 1).forEachIndexed { offset, line ->
            if (line.isBlank()) return@forEachIndexed
            val lineNumber = headerLine + offset + 2
            val cells = line.split(',').map { it.trim() }
            val date = parseDate(cells.getOrNull(dateIndex))
            if (date == null) {
                errors += CsvRowError(lineNumber, "Data inválida.")
                return@forEachIndexed
            }
            val metrics = runCatching {
                DailyMetrics(
                    date = date,
                    sleepDurationMin = parseOptionalInt(cells, indexes.sleep),
                    restingHeartRate = parseOptionalDouble(cells, indexes.restingHeartRate),
                    hrvRmssdMs = parseOptionalDouble(cells, indexes.hrv),
                    spo2Avg = parseOptionalDouble(cells, indexes.spo2),
                    source = DailyMetricsSource.CSV,
                )
            }.getOrElse {
                errors += CsvRowError(lineNumber, it.message ?: "Valores inválidos.")
                return@forEachIndexed
            }
            if (byDate.put(date, metrics) != null) duplicates++
        }

        return DailyMetricsCsvResult(
            metrics = byDate.values.toList(),
            errors = errors,
            duplicateDates = duplicates,
        )
    }

    private fun parseDate(value: String?): LocalDate? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { LocalDate.parse(text, ISO) }
            .recoverCatching { LocalDate.parse(text, BR) }
            .getOrNull()
    }

    private fun parseOptionalInt(cells: List<String>, index: Int?): Int? {
        val value = cells.getOrNull(index ?: return null)?.trim() ?: return null
        if (value.isEmpty()) return null
        return value.toIntOrNull() ?: throw IllegalArgumentException("Número inteiro inválido: $value")
    }

    private fun parseOptionalDouble(cells: List<String>, index: Int?): Double? {
        val value = cells.getOrNull(index ?: return null)?.trim() ?: return null
        if (value.isEmpty()) return null
        return value.replace(',', '.').toDoubleOrNull()
            ?: throw IllegalArgumentException("Número inválido: $value")
    }

    private data class ColumnIndexes(
        val date: Int,
        val sleep: Int?,
        val restingHeartRate: Int?,
        val hrv: Int?,
        val spo2: Int?,
    )

    private companion object {
        val ISO = DateTimeFormatter.ISO_LOCAL_DATE
        val BR = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
        val DATE_ALIASES = setOf("date", "data")
        val SLEEP_ALIASES = setOf("sleep_minutes", "sleep", "sono_minutos", "sono")
        val RHR_ALIASES = setOf("resting_heart_rate", "rhr", "fc_repouso")
        val HRV_ALIASES = setOf("hrv_rmssd", "hrv", "rmssd")
        val SPO2_ALIASES = setOf("spo2", "spo2_avg")
    }
}
