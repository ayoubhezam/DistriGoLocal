package com.distrigo.app.ui.clients

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.lists.AssignOutcome
import com.distrigo.app.data.lists.ListsRepository
import com.distrigo.app.data.lists.QuartierDetail
import com.distrigo.app.ui.common.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ClientSelectionState(
    /** The quartier the clients go to; null until read. */
    val target: QuartierDetail? = null,
    val selected: Set<Int> = emptySet(),
    val saving: Boolean = false,
    /** Why the save was refused (the quartier is gone, the license is read-only); shown above the button. */
    val error: String? = null,
)

sealed interface ClientSelectionEvent {
    data class Saved(val ids: List<Int>, val added: Int, val moved: Int) : ClientSelectionEvent
}

/**
 * « Ajouter des clients » (owner, 2026-10-24): the Clients list in select mode keeps what is ticked here, apart from the
 * list's own search and filters ([ClientViewModel]). Saving puts every ticked client in the quartier in one transaction.
 */
@HiltViewModel
class ClientSelectionViewModel @Inject constructor(
    private val lists: ListsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val quartierId: Long = checkNotNull(savedStateHandle.get<Long>(ClientViewModel.ARG_QUARTIER_ID))

    private val _state = MutableStateFlow(ClientSelectionState())
    val state: StateFlow<ClientSelectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ClientSelectionEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ClientSelectionEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val target = withContext(Dispatchers.IO) { runCatching { lists.quartier(quartierId) }.getOrNull() }
            _state.update { it.copy(target = target, error = if (target == null) "Ce quartier n'existe plus." else null) }
        }
    }

    fun toggle(clientId: Int) = _state.update {
        it.copy(selected = if (clientId in it.selected) it.selected - clientId else it.selected + clientId, error = null)
    }

    fun save() {
        val s = _state.value
        if (s.saving || s.selected.isEmpty()) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val ids = s.selected.toList()
            val outcome = withContext(Dispatchers.IO) {
                try {
                    lists.assignClientsToQuartier(quartierId, ids)
                } catch (e: Exception) {
                    // A read-only license refuses the write in the database itself
                    AssignOutcome.Refused(userMessage(e))
                }
            }
            when (outcome) {
                is AssignOutcome.Done -> {
                    _state.update { it.copy(saving = false) }
                    _events.emit(ClientSelectionEvent.Saved(ids, outcome.added, outcome.moved))
                }
                is AssignOutcome.Refused -> _state.update { it.copy(saving = false, error = outcome.reason) }
            }
        }
    }
}
