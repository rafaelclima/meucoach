package com.meucoach.app.ui.common

import java.text.Normalizer
import java.util.Locale

/** A timezone keeps its IANA id as the stored value; the picker shows a place name. */
data class TimezoneOption(val zoneId: String, val label: String)

/**
 * Brazilian zones only for the MVP. The id is what the app persists; the label is
 * what the user reads, so nobody has to know what "America/Araguaina" means.
 */
val AVAILABLE_TIMEZONES: List<TimezoneOption> = listOf(
    TimezoneOption("America/Sao_Paulo", "Brasília · Sudeste"),
    TimezoneOption("America/Rio_Branco", "Rio Branco"),
    TimezoneOption("America/Manaus", "Manaus"),
    TimezoneOption("America/Belem", "Belém"),
    TimezoneOption("America/Fortaleza", "Fortaleza"),
    TimezoneOption("America/Recife", "Recife"),
    TimezoneOption("America/Maceio", "Maceió"),
    TimezoneOption("America/Araguaina", "Araguaína"),
    TimezoneOption("America/Bahia", "Salvador"),
    TimezoneOption("America/Santarem", "Santarém"),
    TimezoneOption("America/Noronha", "Fernando de Noronha"),
    TimezoneOption("America/Boa_Vista", "Boa Vista"),
    TimezoneOption("America/Campo_Grande", "Campo Grande"),
    TimezoneOption("America/Cuiaba", "Cuiabá"),
    TimezoneOption("America/Porto_Velho", "Porto Velho"),
)
    .sortedBy { it.label }

/** Cidades principais para localização (opcional). */
val AVAILABLE_CITIES: List<String> = listOf(
    "Natal",
    "São Paulo",
    "Rio de Janeiro",
    "Brasília",
    "Belo Horizonte",
    "Porto Alegre",
    "Curitiba",
    "Salvador",
    "Fortaleza",
    "Recife",
    "Manaus",
    "Belém",
    "Goiânia",
    "Campo Grande",
    "João Pessoa",
    "Aracaju",
    "Teresina",
    "Vitória",
    "Florianópolis",
)
    .sorted()

fun timezoneLabel(zoneId: String): String = AVAILABLE_TIMEZONES.firstOrNull { it.zoneId == zoneId }?.label ?: zoneId

/** Accent-insensitive search so "brasilia" finds "Brasília". */
fun matchesSearch(text: String, query: String): Boolean = normalize(text).contains(normalize(query))

private fun normalize(value: String): String = Normalizer
    .normalize(value, Normalizer.Form.NFD)
    .replace(MARKS, "")
    .lowercase(Locale.ROOT)

private val MARKS = "\\p{M}+".toRegex()
