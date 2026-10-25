package com.distrigo.app.ui.secteurs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.lists.ListsRepository
import com.distrigo.app.data.lists.SecteurSummary
import com.distrigo.app.ui.common.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The Secteurs list as its screen shows it. */
sealed interface SecteursUiState {
    data object Loading : SecteursUiState
    data object Empty : SecteursUiState
    data class Success(val secteurs: List<SecteurSummary>) : SecteursUiState
    data class Error(val message: String) : SecteursUiState
}

/**
 * « Secteurs » (owner's prototype, 2026-10-24): every secteur with its commune, quartiers and clients, and a search.
 */
@HiltViewModel
class SecteursViewModel @Inject constructor(private val lists: ListsRepository) : ViewModel() {

    private val _state = MutableStateFlow<SecteursUiState>(SecteursUiState.Loading)
    val state: StateFlow<SecteursUiState> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Reads the secteurs again; the screen calls it each time it opens, so counts changed elsewhere are right. */
    fun refresh() = viewModelScope.launch {
        _state.value = withContext(Dispatchers.IO) {
            runCatching { lists.secteurs() }.fold(
                onSuccess = { if (it.isEmpty()) SecteursUiState.Empty else SecteursUiState.Success(it) },
                onFailure = { SecteursUiState.Error(userMessage(it)) },
            )
        }
    }

    fun search(query: String) { _query.value = query }
}
