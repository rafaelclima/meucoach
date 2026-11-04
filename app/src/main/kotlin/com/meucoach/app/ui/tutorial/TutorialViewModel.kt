package com.meucoach.app.ui.tutorial

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class TutorialUiState(val seen: Boolean = false)

/**
 * Single source of truth for the tutorial gate: the app shows the intro once,
 * when a profile exists and the intro was never completed.
 */
@HiltViewModel
class TutorialViewModel @Inject constructor(private val store: TutorialStore) : ViewModel() {
    private val _uiState = MutableStateFlow(TutorialUiState(seen = store.isIntroDone()))
    val uiState: StateFlow<TutorialUiState> = _uiState.asStateFlow()

    fun markSeen() {
        store.markIntroDone()
        _uiState.value = TutorialUiState(seen = true)
    }
}
