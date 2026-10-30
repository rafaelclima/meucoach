package com.meucoach.core.data.profile

import com.meucoach.core.model.GoalType
import com.meucoach.core.model.TrainingGoal
import com.meucoach.core.model.WeeklyAvailability
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

/** JSON columns for the parts of the profile that are lists/records. */
object ProfileJson {
    fun encodeAvailability(availability: WeeklyAvailability): String {
        val json = JSONObject()
        availability.minutesByDay.entries
            .sortedBy { it.key }
            .forEach { (day, minutes) -> json.put(day.name, minutes) }
        return json.toString()
    }

    fun decodeAvailability(raw: String): WeeklyAvailability = runCatching {
        val json = JSONObject(raw)
        val minutes = buildMap {
            json.keys().forEach { key ->
                val day = runCatching { DayOfWeek.valueOf(key) }.getOrNull() ?: return@forEach
                put(day, json.getInt(key))
            }
        }
        WeeklyAvailability(minutes)
    }.getOrElse { WeeklyAvailability() }

    fun encodeGoal(goal: TrainingGoal?): String? {
        if (goal == null) return null
        return JSONObject()
            .put("type", goal.type.name)
            .apply {
                goal.targetDate?.let { put("targetDate", it.toString()) }
                goal.targetDistanceM?.let { put("targetDistanceM", it) }
                goal.targetTimeSec?.let { put("targetTimeSec", it) }
            }.toString()
    }

    fun decodeGoal(raw: String?): TrainingGoal? {
        if (raw == null) return null
        return runCatching {
            val json = JSONObject(raw)
            TrainingGoal(
                type = GoalType.valueOf(json.getString("type")),
                targetDate = if (json.has("targetDate")) LocalDate.parse(json.getString("targetDate")) else null,
                targetDistanceM = if (json.has("targetDistanceM")) json.getDouble("targetDistanceM") else null,
                targetTimeSec = if (json.has("targetTimeSec")) json.getInt("targetTimeSec") else null,
            )
        }.getOrNull()
    }
}
