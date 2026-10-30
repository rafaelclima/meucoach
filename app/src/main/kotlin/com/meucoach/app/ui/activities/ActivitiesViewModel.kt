package com.meucoach.app.ui.activities

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.importer.ImportCoordinator
import com.meucoach.app.importer.importEnqueueMessage
import com.meucoach.core.data.repository.ActivityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActivitiesViewModel @Inject constructor(
    repository: ActivityRepository,
    private val coordinator: ImportCoordinator,
) : ViewModel() {
    private val message = MutableStateFlow<String?>(null)
    private val importing = MutableStateFlow(false)

    val uiState: StateFlow<ActivitiesUiState> = combine(
        repository.observeActivitySummaries(),
        repository.observeImportJobs(),
        message,
        importing,
    ) { activities, jobs, currentMessage, isImporting ->
        ActivitiesUiState(
            isLoading = false,
            activities = activities,
            recentImports = jobs.take(RECENT_IMPORT_LIMIT),
            isImporting = isImporting,
            message = currentMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ActivitiesUiState(),
    )

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importing.value = true
            try {
                message.value = importEnqueueMessage(coordinator.enqueue(uris))
            } finally {
                importing.value = false
            }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    private companion object {
        const val RECENT_IMPORT_LIMIT = 5
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
