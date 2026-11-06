package com.meucoach.app.ui.profile

import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Experience
import com.meucoach.core.model.GoalType
import com.meucoach.core.model.Sex
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val PT_BR = Locale.forLanguageTag("pt-BR")
private val DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu")

fun sexLabel(sex: Sex): String = when (sex) {
    Sex.FEMALE -> "Feminino"
    Sex.MALE -> "Masculino"
    Sex.OTHER -> "Outro"
    Sex.UNSPECIFIED -> "Prefiro não dizer"
}

fun experienceLabel(experience: Experience): String = when (experience) {
    Experience.BEGINNER -> "Iniciante"
    Experience.INTERMEDIATE -> "Intermediário"
    Experience.ADVANCED -> "Avançado"
}

fun goalLabel(goal: GoalType): String = when (goal) {
    GoalType.GENERAL_FITNESS -> "Saúde geral"
    GoalType.FIVE_K -> "5 km"
    GoalType.TEN_K -> "10 km"
    GoalType.HALF_MARATHON -> "Meia maratona"
    GoalType.MARATHON -> "Maratona"
    GoalType.CUSTOM -> "Outro"
}

fun ageLabel(profile: AthleteProfile, today: LocalDate): String {
    val years = Period.between(profile.birthDate, today).years
    return "$years anos"
}

fun targetDateLabel(profile: AthleteProfile): String = profile.goal?.targetDate?.format(DATE) ?: "Sem data"

fun targetTimeLabel(seconds: Int?): String {
    if (seconds == null) return "Sem tempo alvo"
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val rest = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, rest)
    }
}

fun availabilityLabel(profile: AthleteProfile): String {
    val availability = profile.weeklyAvailability
    if (availability.minutesByDay.isEmpty()) return "Não informada"
    val days = availability.minutesByDay.keys
        .sortedBy { it.value }
        .joinToString(", ") { day -> day.getDisplayName(TextStyle.SHORT, PT_BR).trimEnd('.') }
    val hours = availability.totalMinutes / 60.0
    val duration = if (hours % 1.0 == 0.0) "${hours.toInt()} h" else String.format(PT_BR, "%.1f h", hours)
    return "$days · $duration/semana"
}

fun goalLabel(profile: AthleteProfile): String = profile.goal?.type?.let(::goalLabel) ?: "Saúde geral"

fun availabilityDayLabel(day: DayOfWeek): String =
    day.getDisplayName(TextStyle.FULL, PT_BR).replaceFirstChar { it.uppercaseChar() }
