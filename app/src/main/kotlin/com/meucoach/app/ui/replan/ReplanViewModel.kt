package com.meucoach.app.ui.replan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meucoach.app.replan.ReplanControllerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Today/Perfil facade of the shared `ReplanController`. The acceptance machine
 * lives in the controller so the Coach tab uses exactly the same rules.
 */
@HiltViewModel
class ReplanViewModel @Inject constructor(factory: ReplanControllerFactory) : ViewModel() {
    private val controller = factory.create(viewModelScope)

    val uiState: StateFlow<ReplanUiState> = controller.uiState

    fun refresh() = controller.refresh()

    fun refreshForProfile() = controller.refreshForProfile()

    fun replanNow() = controller.replanNow()

    fun accept() = controller.accept()

    fun decline() = controller.decline()

    fun suppress() = controller.suppress()

    fun undo() = controller.undo()

    fun dismissSuggestion() = controller.dismissSuggestion()

    fun consumeMessage() = controller.consumeMessage()
}
