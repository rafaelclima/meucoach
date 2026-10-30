package com.meucoach.app.ui.activitydetail

import com.meucoach.core.data.repository.StoredActivity
import com.meucoach.core.domain.insights.ActivityInsight

data class ActivityDetailUiState(
    val isLoading: Boolean = true,
    val activity: StoredActivity? = null,
    val notFound: Boolean = false,
    val insights: List<ActivityInsight> = emptyList(),
)
