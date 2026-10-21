package com.distrigo.app.ui.settings.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.lists.ListEntry
import com.distrigo.app.data.lists.ListKind
import com.distrigo.app.data.lists.ListOutcome
import com.distrigo.app.data.lists.ListsRepository
import com.distrigo.app.ui.common.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** « Listes et catégories »: how many entries each list has. */
@HiltViewModel
class ListsHomeViewModel @Inject constructor(private val lists: ListsRepository) : ViewModel() {

    private val _counts = MutableStateFlow<Map<ListKind, Int>?>(null)
    val counts: StateFlow<Map<ListKind, Int>?> = _counts.asStateFlow()

    /** Reads the counts again; the screen calls it each time it opens, so a list just edited is counted right. */
    fun refresh() = viewModelScope.launch {
        _counts.value = withContext(Dispatchers.IO) { runCatching { lists.counts() }.getOrNull() } ?: _counts.value
    }
}

/** One list as its screen shows it: null [entries] until read once. */
data class ListEditorState(
    val entries: List<ListEntry>? = null,
    val query: String = "",
)

/**
 * One list of « Listes et catégories » — [ListKind] from the route, under [parentId] for a category's
 * sous-catégories or a type's sous-types — and its three writes. Each write answers with null when done, or the
 * reason it was refused.
 */
@HiltViewModel
class ListEditorViewModel @Inject constructor(
    private val lists: ListsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val kind: ListKind = ListKind.valueOf(checkNotNull(savedStateHandle.get<String>(ARG_KIND)))
    val parentId: Long? = savedStateHandle.get<Long>(ARG_PARENT_ID)?.takeIf { it > 0 }
    val parentName: String? = savedStateHandle.get<String>(ARG_PARENT_NAME)?.takeIf { it.isNotBlank() }

    private val _state = MutableStateFlow(ListEditorState())
    val state: StateFlow<ListEditorState> = _state.asStateFlow()

    fun refresh() = viewModelScope.launch {
        val entries = withContext(Dispatchers.IO) { runCatching { lists.entries(kind, parentId) }.getOrNull() }
        if (entries != null) _state.update { it.copy(entries = entries) }
    }

    fun search(query: String) = _state.update { it.copy(query = query) }

    fun add(name: String, commune: String?, wilaya: String?, hasFournisseur: Boolean, onDone: (String?) -> Unit) =
        write(onDone) { lists.add(kind, name, parentId, commune, wilaya, hasFournisseur) }

    fun rename(id: Long, name: String, onDone: (String?) -> Unit) = write(onDone) { lists.rename(kind, id, name) }

    fun delete(id: Long, onDone: (String?) -> Unit) = write(onDone) { lists.delete(kind, id) }

    private fun write(onDone: (String?) -> Unit, block: () -> ListOutcome) = viewModelScope.launch {
        val reason = withContext(Dispatchers.IO) {
            try {
                when (val outcome = block()) {
                    ListOutcome.Done -> null
                    is ListOutcome.Refused -> outcome.reason
                }
            } catch (e: Exception) {
                // A read-only license refuses the write in the database itself
                userMessage(e)
            }
        }
        if (reason == null) refresh()
        onDone(reason)
    }

    companion object {
        const val ARG_KIND = "kind"
        const val ARG_PARENT_ID = "parentId"
        const val ARG_PARENT_NAME = "parentName"
    }
}
