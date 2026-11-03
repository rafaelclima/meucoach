package com.meucoach.app.ui.onboarding

import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.Experience
import com.meucoach.core.model.GoalType
import com.meucoach.core.model.Sex
import com.meucoach.core.model.TrainingGoal
import com.meucoach.core.model.Units
import com.meucoach.core.model.WeeklyAvailability
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val RACE_GOAL_TYPES = setOf(
    GoalType.FIVE_K,
    GoalType.TEN_K,
    GoalType.HALF_MARATHON,
    GoalType.MARATHON,
)

/**
 * Aplica máscara de data (dd/MM/aaaa) enquanto o usuário digita.
 * Remove tudo que não é número e adiciona as barras automaticamente.
 */
fun applyDateMask(input: String): String {
    val digits = input.filter { it.isDigit() }.take(8)
    return buildString {
        digits.forEachIndexed { index, char ->
            append(char)
            if (index == 1 || index == 3) append('/')
        }
    }
}

enum class OnboardingField {
    BIRTH_DATE,
    HEIGHT,
    WEIGHT,
    TARGET_DATE,
    TARGET_TIME,
    AVAILABILITY,
    RESTING_HEART_RATE,
    MAX_HEART_RATE,
    TIMEZONE,
    CITY,
}

data class OnboardingForm(
    val birthDate: String = "",
    val sex: Sex = Sex.UNSPECIFIED,
    val heightCm: String = "",
    val weightKg: String = "",
    val experience: Experience = Experience.BEGINNER,
    val goalType: GoalType = GoalType.GENERAL_FITNESS,
    val targetDate: String = "",
    val targetTime: String = "",
    val availabilityMinutes: Map<DayOfWeek, String> = emptyMap(),
    val restingHeartRate: String = "",
    val maxHeartRateObserved: String = "",
    val timezone: String = ZoneId.systemDefault().id,
    val city: String = "",
    val treadmillAccess: Boolean = false,
) {
    val isRaceGoal: Boolean
        get() = goalType in RACE_GOAL_TYPES
}

data class OnboardingValidation(val errors: Map<OnboardingField, String> = emptyMap()) {
    val isValid: Boolean get() = errors.isEmpty()
}

object OnboardingValidator {
    private val DATE = DateTimeFormatter
        .ofPattern("dd/MM/uuuu")
        .withResolverStyle(java.time.format.ResolverStyle.STRICT)

    fun validateStep(step: Int, form: OnboardingForm, today: LocalDate = LocalDate.now()): OnboardingValidation =
        when (step) {
            0 -> validateAbout(form, today)
            1 -> validateGoal(form, today)
            else -> validateRoutine(form)
        }

    fun validateAll(form: OnboardingForm, today: LocalDate = LocalDate.now()): OnboardingValidation =
        OnboardingValidation(
            validateStep(0, form, today).errors +
                validateStep(1, form, today).errors +
                validateStep(2, form, today).errors,
        )

    fun toProfile(
        form: OnboardingForm,
        id: String,
        existing: AthleteProfile? = null,
        today: LocalDate = LocalDate.now(),
    ): AthleteProfile {
        val validation = validateAll(form, today)
        require(validation.isValid) { "Profile form has invalid fields: ${validation.errors.keys}" }
        return AthleteProfile(
            id = id,
            birthDate = LocalDate.parse(form.birthDate.trim(), DATE),
            sex = form.sex,
            heightCm = form.heightCm.toDoubleOrNull(),
            weightKg = form.weightKg.toDoubleOrNull(),
            restingHeartRate = form.restingHeartRate.toIntOrNull(),
            maxHeartRateObserved = form.maxHeartRateObserved.toIntOrNull(),
            lactateThresholdHeartRate = existing?.lactateThresholdHeartRate,
            thresholdPace = existing?.thresholdPace,
            vdot = existing?.vdot,
            experience = form.experience,
            timezone = form.timezone.trim(),
            weeklyAvailability = WeeklyAvailability(
                form.availabilityMinutes.mapValues { (_, minutes) -> requireNotNull(minutes.toIntOrNull()) },
            ),
            goal = goalFor(form, existing),
            city = form.city.trim().ifBlank { null },
            latitude = existing?.latitude,
            longitude = existing?.longitude,
            units = Units.METRIC,
            treadmillAccess = form.treadmillAccess,
        )
    }

    fun fromProfile(profile: AthleteProfile?): OnboardingForm {
        if (profile == null) return OnboardingForm()
        return OnboardingForm(
            birthDate = profile.birthDate.format(DATE),
            sex = profile.sex,
            heightCm = profile.heightCm?.let { formatDecimal(it) } ?: "",
            weightKg = profile.weightKg?.let { formatDecimal(it) } ?: "",
            experience = profile.experience,
            goalType = profile.goal?.type ?: GoalType.GENERAL_FITNESS,
            targetDate = profile.goal?.targetDate?.format(DATE) ?: "",
            targetTime = profile.goal?.targetTimeSec?.let { formatDuration(it) } ?: "",
            availabilityMinutes = profile.weeklyAvailability.minutesByDay.mapValues { it.value.toString() },
            restingHeartRate = profile.restingHeartRate?.toString() ?: "",
            maxHeartRateObserved = profile.maxHeartRateObserved?.toString() ?: "",
            timezone = profile.timezone,
            city = profile.city ?: "",
            treadmillAccess = profile.treadmillAccess,
        )
    }

    private fun validateAbout(form: OnboardingForm, today: LocalDate): OnboardingValidation {
        val errors = mutableMapOf<OnboardingField, String>()
        val birthDate = parseDate(form.birthDate)
        when {
            birthDate == null -> errors[OnboardingField.BIRTH_DATE] = "Use dd/mm/aaaa."

            !birthDate.isBefore(today) -> errors[OnboardingField.BIRTH_DATE] = "A data precisa estar no passado."

            birthDate.isBefore(today.minusYears(MAX_AGE.toLong())) ->
                errors[OnboardingField.BIRTH_DATE] = "Confira o ano."
        }
        form.heightCm.toDoubleOrNull()?.let { height ->
            if (height !in MIN_HEIGHT_CM..MAX_HEIGHT_CM) {
                errors[OnboardingField.HEIGHT] = "Altura entre 80 e 250 cm."
            }
        } ?: form.heightCm.takeIf { it.isNotBlank() }?.let {
            errors[OnboardingField.HEIGHT] = "Altura inválida."
        }
        form.weightKg.toDoubleOrNull()?.let { weight ->
            if (weight !in MIN_WEIGHT_KG..MAX_WEIGHT_KG) {
                errors[OnboardingField.WEIGHT] = "Peso entre 30 e 300 kg."
            }
        } ?: form.weightKg.takeIf { it.isNotBlank() }?.let {
            errors[OnboardingField.WEIGHT] = "Peso inválido."
        }
        return OnboardingValidation(errors)
    }

    private fun validateGoal(form: OnboardingForm, today: LocalDate): OnboardingValidation {
        val errors = mutableMapOf<OnboardingField, String>()
        if (form.goalType in RACE_GOAL_TYPES && form.targetDate.isNotBlank()) {
            val date = parseDate(form.targetDate)
            when {
                date == null -> errors[OnboardingField.TARGET_DATE] = "Use dd/mm/aaaa."
                !date.isAfter(today) -> errors[OnboardingField.TARGET_DATE] = "A prova precisa estar no futuro."
            }
        } else if (form.targetDate.isNotBlank() && parseDate(form.targetDate) == null) {
            errors[OnboardingField.TARGET_DATE] = "Use dd/mm/aaaa."
        }
        if (form.targetTime.isNotBlank() && parseDuration(form.targetTime) == null) {
            errors[OnboardingField.TARGET_TIME] = "Use mm:ss ou h:mm:ss."
        }
        form.availabilityMinutes.forEach { (_, minutes) ->
            val value = minutes.toIntOrNull()
            if (value == null || value !in 1..MAX_MINUTES_PER_DAY) {
                errors[OnboardingField.AVAILABILITY] = "Minutos por dia entre 1 e 1.440."
            }
        }
        return OnboardingValidation(errors)
    }

    private fun validateRoutine(form: OnboardingForm): OnboardingValidation {
        val errors = mutableMapOf<OnboardingField, String>()
        val resting = form.restingHeartRate.toIntOrNull()
        val max = form.maxHeartRateObserved.toIntOrNull()
        if (form.restingHeartRate.isNotBlank()) {
            if (resting == null || resting !in 30..120) {
                errors[OnboardingField.RESTING_HEART_RATE] = "FC de repouso entre 30 e 120 bpm."
            }
        }
        if (form.maxHeartRateObserved.isNotBlank()) {
            if (max == null || max !in 100..250) {
                errors[OnboardingField.MAX_HEART_RATE] = "FC máxima entre 100 e 250 bpm."
            }
        }
        if (resting != null && max != null && max <= resting) {
            errors[OnboardingField.MAX_HEART_RATE] = "A FC máxima precisa ser maior que a de repouso."
        }
        if (runCatching { ZoneId.of(form.timezone.trim()) }.isFailure) {
            errors[OnboardingField.TIMEZONE] = "Fuso horário inválido."
        }
        if (form.city.isNotBlank() && form.city.trim().length < 2) {
            errors[OnboardingField.CITY] = "Informe uma cidade válida."
        }
        return OnboardingValidation(errors)
    }

    private fun goalFor(form: OnboardingForm, existing: AthleteProfile?): TrainingGoal? {
        if (form.goalType == GoalType.GENERAL_FITNESS) return null
        val sameType = existing?.goal?.type == form.goalType
        return TrainingGoal(
            type = form.goalType,
            targetDate = parseDate(form.targetDate),
            targetDistanceM = if (sameType) existing?.goal?.targetDistanceM else defaultDistance(form.goalType),
            targetTimeSec = parseDuration(form.targetTime),
        )
    }

    private fun defaultDistance(type: GoalType): Double? = when (type) {
        GoalType.FIVE_K -> 5_000.0
        GoalType.TEN_K -> 10_000.0
        GoalType.HALF_MARATHON -> 21_097.5
        GoalType.MARATHON -> 42_195.0
        GoalType.GENERAL_FITNESS, GoalType.CUSTOM -> null
    }

    private fun parseDate(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim(), DATE) }.getOrNull()

    private fun parseDuration(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.any { it.isBlank() }) return null
        val values = parts.map { it.toIntOrNull() ?: return null }
        return when (values.size) {
            2 -> {
                val (minutes, seconds) = values
                if (minutes !in 0..180 || seconds !in 0..59) return null
                minutes * 60 + seconds
            }

            3 -> {
                val (hours, minutes, seconds) = values
                if (hours !in 0..23 || minutes !in 0..59 || seconds !in 0..59) return null
                hours * 3_600 + minutes * 60 + seconds
            }

            else -> null
        }
    }

    private fun formatDuration(seconds: Int): String {
        val hours = seconds / 3_600
        val minutes = (seconds % 3_600) / 60
        val rest = seconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, rest)
        } else {
            "%d:%02d".format(minutes, rest)
        }
    }

    private fun formatDecimal(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private const val MIN_HEIGHT_CM = 80.0
    private const val MAX_HEIGHT_CM = 250.0
    private const val MIN_WEIGHT_KG = 30.0
    private const val MAX_WEIGHT_KG = 300.0
    private const val MAX_AGE = 110
    private const val MAX_MINUTES_PER_DAY = 1_440
}
