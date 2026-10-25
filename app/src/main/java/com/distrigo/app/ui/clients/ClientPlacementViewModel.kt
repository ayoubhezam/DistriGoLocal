package com.distrigo.app.ui.clients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.lists.ClientPlacement
import com.distrigo.app.data.lists.ListsRepository
import com.distrigo.app.ui.common.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface ClientPlacementUiState {
    data object Loading : ClientPlacementUiState
    data class Success(val placement: ClientPlacement) : ClientPlacementUiState
    data class Error(val message: String) : ClientPlacementUiState
}

/** « Ajouter un nouveau client » from a quartier: the place the form fills and locks, read once. */
@HiltViewModel
class ClientPlacementViewModel @Inject constructor(
    private val lists: ListsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val quartierId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_IN_QUARTIER))

    private val _state = MutableStateFlow<ClientPlacementUiState>(ClientPlacementUiState.Loading)
    val state: StateFlow<ClientPlacementUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) {
                runCatching { lists.placement(quartierId) }.fold(
                    onSuccess = { it?.let(ClientPlacementUiState::Success) ?: ClientPlacementUiState.Error("Ce quartier n'existe plus.") },
                    onFailure = { ClientPlacementUiState.Error(userMessage(it)) },
                )
            }
        }
    }

    companion object {
        const val ARG_IN_QUARTIER = "inQuartier"
    }
}
