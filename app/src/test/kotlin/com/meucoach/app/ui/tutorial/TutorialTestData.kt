package com.meucoach.app.ui.tutorial

import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.ReadinessLevel

internal fun tutorialState(
    hasCheckedInToday: Boolean = false,
    hasActivities: Boolean = false,
    healthConnected: Boolean = false,
    isLoading: Boolean = false,
    error: String? = null,
) = TutorialUiState(
    seen = false,
    isLoading = isLoading,
    name = "Rafael",
    goalLabel = "5 km",
    availabilityLabel = "seg, qua, sáb · 3 h/semana",
    planPreview = TutorialPlanPreview(
        sessions = listOf(
            TutorialSessionPreview("Seg", "Fácil e conversável", 40, WorkoutType.EASY),
            TutorialSessionPreview("Qua", "Forte e controlado", 30, WorkoutType.QUALITY),
            TutorialSessionPreview("Sáb", "Longo tranquilo", 60, WorkoutType.LONG),
        ),
        weeklyMinutes = 130,
        qualityCount = 1,
    ),
    workoutPreview = TutorialWorkoutPreview(
        title = "Fácil e conversável",
        zone = "Z2 · leve",
        durationMin = 40,
        rationale = "Base aeróbica em ritmo fácil, conversável do início ao fim.",
    ),
    readinessPreview = TutorialReadinessPreview(
        score = 72,
        level = ReadinessLevel.GREEN,
        label = "Alta",
        confidence = DataConfidence.MEDIUM,
    ),
    hasCheckedInToday = hasCheckedInToday,
    hasActivities = hasActivities,
    healthConnected = healthConnected,
    error = error,
)
