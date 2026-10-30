package com.meucoach.app.ui.activitydetail

import com.meucoach.core.data.repository.StoredActivity

data class ActivityDetailUiState(
    val isLoading: Boolean = true,
    val activity: StoredActivity? = null,
    val notFound: Boolean = false,
)
