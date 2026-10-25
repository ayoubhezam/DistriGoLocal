package com.distrigo.app.ui.secteurs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.lists.ListsRepository
import com.distrigo.app.data.lists.QuartierClient
import com.distrigo.app.data.lists.QuartierDetail
import com.distrigo.app.ui.common.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface QuartierDetailUiState {
    data object Loading : QuartierDetailUiState
    data class Success(val quartier: QuartierDetail, val clients: List<QuartierClient>) : QuartierDetailUiState
    data class Error(val message: String) : QuartierDetailUiState
}

/** « Détails du quartier » (owner's prototype v5, 2026-10-24): the quartier's name for the bar, and its clients. */
@HiltViewModel
class QuartierDetailViewModel @Inject constructor(
    private val lists: ListsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val quartierId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_QUARTIER_ID))

    private val _state = MutableStateFlow<QuartierDetailUiState>(QuartierDetailUiState.Loading)
    val state: StateFlow<QuartierDetailUiState> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Reads the quartier again; the screen calls it each time it opens, so a rename or an added client shows. */
    fun refresh() = viewModelScope.launch {
        _state.value = withContext(Dispatchers.IO) {
            runCatching { lists.quartier(quartierId)?.let { it to lists.clientsIn(quartierId) } }.fold(
                onSuccess = { found ->
                    if (found == null) QuartierDetailUiState.Error("Ce quartier n'existe plus.")
                    else QuartierDetailUiState.Success(found.first, found.second)
                },
                onFailure = { QuartierDetailUiState.Error(userMessage(it)) },
            )
        }
    }

    fun search(query: String) { _query.value = query }

    companion object {
        const val ARG_QUARTIER_ID = "quartierId"
    }
}
