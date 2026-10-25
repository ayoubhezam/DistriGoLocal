package com.distrigo.app.ui.secteurs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.lists.ListsRepository
import com.distrigo.app.data.lists.QuartierSummary
import com.distrigo.app.data.lists.SecteurDetail
import com.distrigo.app.ui.common.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface SecteurDetailUiState {
    data object Loading : SecteurDetailUiState
    data class Success(val secteur: SecteurDetail, val quartiers: List<QuartierSummary>) : SecteurDetailUiState
    data class Error(val message: String) : SecteurDetailUiState
}

/** « Détails du secteur » (owner's prototype v5, 2026-10-24): the secteur's name for the bar, and its quartiers. */
@HiltViewModel
class SecteurDetailViewModel @Inject constructor(
    private val lists: ListsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val secteurId: Long = checkNotNull(savedStateHandle.get<Long>(ARG_SECTEUR_ID))

    private val _state = MutableStateFlow<SecteurDetailUiState>(SecteurDetailUiState.Loading)
    val state: StateFlow<SecteurDetailUiState> = _state.asStateFlow()

    /** Reads the secteur again; the screen calls it each time it opens, so an edit or a new quartier shows. */
    fun refresh() = viewModelScope.launch {
        _state.value = withContext(Dispatchers.IO) {
            runCatching { lists.secteur(secteurId)?.let { it to lists.quartiers(secteurId) } }.fold(
                onSuccess = { found ->
                    if (found == null) SecteurDetailUiState.Error("Ce secteur n'existe plus.")
                    else SecteurDetailUiState.Success(found.first, found.second)
                },
                onFailure = { SecteurDetailUiState.Error(userMessage(it)) },
            )
        }
    }

    companion object {
        const val ARG_SECTEUR_ID = "secteurId"
    }
}
