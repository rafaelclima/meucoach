package com.meucoach.app.ui.activities

import com.meucoach.core.data.repository.ImportJobSummary
import com.meucoach.core.model.ActivitySummary

data class ActivitiesUiState(
    val isLoading: Boolean = true,
    val activities: List<ActivitySummary> = emptyList(),
    val recentImports: List<ImportJobSummary> = emptyList(),
    val isImporting: Boolean = false,
    val message: String? = null,
)
