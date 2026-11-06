package com.meucoach.app.health

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Hosts the app-level resume trigger without tying sync policy to a screen. */
@HiltViewModel
class HealthSyncViewModel @Inject constructor(private val coordinator: HealthSyncCoordinator) : ViewModel() {
    val state: StateFlow<HealthSyncState> = coordinator.state

    fun syncOnResume() = coordinator.syncOnResume()
}
