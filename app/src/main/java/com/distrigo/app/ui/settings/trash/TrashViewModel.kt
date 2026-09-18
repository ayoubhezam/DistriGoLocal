package com.distrigo.app.ui.settings.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.trash.TrashItem
import com.distrigo.app.data.trash.TrashKind
import com.distrigo.app.data.trash.TrashOutcome
import com.distrigo.app.data.trash.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** What the screen asks the user, or tells them, about one item. */
sealed class TrashPrompt {
    /** Nothing uses it: ask before deleting it for good. */
    data class ConfirmDelete(val item: TrashItem) : TrashPrompt()
    /** Refused, and why: something uses it, or a rule forbids restoring it. */
    data class Refused(val title: String, val reason: String) : TrashPrompt()
}

data class TrashUiState(
    val counts: Map<TrashKind, Int> = emptyMap(),
    val kind: TrashKind? = null,
    val items: List<TrashItem> = emptyList(),
    val loading: Boolean = true,
    val prompt: TrashPrompt? = null,
    /** A short confirmation shown once, then cleared. */
    val message: String? = null,
) {
    val total: Int get() = counts.values.sum()
    /** Only the kinds with something in the bin, in the order the bin lists them. */
    val kinds: List<TrashKind> get() = TrashKind.entries.filter { (counts[it] ?: 0) > 0 }
}

@HiltViewModel
class TrashViewModel @Inject constructor(private val trash: TrashRepository) : ViewModel() {

    private val _state = MutableStateFlow(TrashUiState())
    val state: StateFlow<TrashUiState> = _state.asStateFlow()

    /** Reads the bin again; the screen calls it each time it opens. */
    fun refresh() = viewModelScope.launch {
        val counts = withContext(Dispatchers.IO) { trash.counts() }
        // Stay on the chosen kind while it still has items, otherwise show the first kind that does.
        val kind = _state.value.kind?.takeIf { (counts[it] ?: 0) > 0 } ?: TrashKind.entries.firstOrNull { (counts[it] ?: 0) > 0 }
        val items = kind?.let { withContext(Dispatchers.IO) { trash.items(it) } }.orEmpty()
        _state.update { it.copy(counts = counts, kind = kind, items = items, loading = false) }
    }

    fun choose(kind: TrashKind) {
        _state.update { it.copy(kind = kind) }
        refresh()
    }

    fun restore(item: TrashItem) = viewModelScope.launch {
        when (val outcome = withContext(Dispatchers.IO) { trash.restore(item.kind, item.id) }) {
            TrashOutcome.Done -> _state.update { it.copy(message = "« ${item.name} » a été restauré.") }
            is TrashOutcome.Refused -> _state.update { it.copy(prompt = TrashPrompt.Refused("Restauration impossible", outcome.reason)) }
        }
        refresh()
    }

    /** Checks first what uses the item, so the user is only asked to confirm what can actually be done. */
    fun askDelete(item: TrashItem) = viewModelScope.launch {
        val usages = withContext(Dispatchers.IO) { trash.usages(item.kind, item.id) }
        _state.update {
            it.copy(
                prompt = if (usages.isEmpty()) {
                    TrashPrompt.ConfirmDelete(item)
                } else {
                    TrashPrompt.Refused(
                        "Suppression impossible",
                        "« ${item.name} » est encore utilisé par ${TrashRepository.words(usages)}. " +
                            "Il reste dans la corbeille ; vous pouvez le restaurer à tout moment."
                    )
                }
            )
        }
    }

    fun deleteForGood(item: TrashItem) = viewModelScope.launch {
        _state.update { it.copy(prompt = null) }
        when (val outcome = withContext(Dispatchers.IO) { trash.deletePermanently(item.kind, item.id) }) {
            TrashOutcome.Done -> _state.update { it.copy(message = "« ${item.name} » a été supprimé définitivement.") }
            is TrashOutcome.Refused -> _state.update { it.copy(prompt = TrashPrompt.Refused("Suppression impossible", outcome.reason)) }
        }
        refresh()
    }

    fun dismissPrompt() = _state.update { it.copy(prompt = null) }

    fun messageShown() = _state.update { it.copy(message = null) }
}
