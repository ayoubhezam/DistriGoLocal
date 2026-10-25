package com.distrigo.app.ui.secteurs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.lists.ListOutcome
import com.distrigo.app.data.lists.ListsRepository
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

data class QuartierFormState(
    /** Same steps as the secteur's form: Loading, then Ready, or Error when the quartier or its secteur is gone. */
    val status: SecteurFormStatus = SecteurFormStatus.Loading,
    val name: String = "",
    val loadedName: String = "",
    /** The secteur, fixed: shown read-only. */
    val secteurName: String = "",
    val commune: String = "",
    /** How many live clients are in the quartier: while any are, it is not deleted. */
    val clients: Int = 0,
    val nameError: String? = null,
    val saving: Boolean = false,
) {
    val dirty: Boolean get() = name != loadedName
}

/**
 * « Nouveau quartier » / « Modifier le quartier » (owner's prototype v5, 2026-10-24): a name, unique in the secteur;
 * the secteur comes from where the form was opened and never changes. No tournée here.
 */
@HiltViewModel
class QuartierFormViewModel @Inject constructor(
    private val lists: ListsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** The quartier edited; null for a new one, which then has [secteurId]. */
    val quartierId: Long? = savedStateHandle.get<Long>(ARG_QUARTIER_ID)?.takeIf { it > 0 }
    private val secteurId: Long? = savedStateHandle.get<Long>(ARG_SECTEUR_ID)?.takeIf { it > 0 }

    private val _state = MutableStateFlow(QuartierFormState())
    val state: StateFlow<QuartierFormState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SecteurFormEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SecteurFormEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    quartierId?.let { lists.quartier(it) }?.let { q ->
                        QuartierFormState(SecteurFormStatus.Ready, q.name, q.name, q.secteurName, q.commune, q.clients)
                    } ?: secteurId?.takeIf { quartierId == null }?.let { lists.secteur(it) }?.let { s ->
                        QuartierFormState(SecteurFormStatus.Ready, secteurName = s.name, commune = s.commune)
                    }
                }
            }
            _state.value = loaded.fold(
                onSuccess = { it ?: QuartierFormState(SecteurFormStatus.Error(if (quartierId != null) "Ce quartier n'existe plus." else "Ce secteur n'existe plus.")) },
                onFailure = { QuartierFormState(SecteurFormStatus.Error(userMessage(it))) },
            )
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name, nameError = null) }

    fun save() {
        val s = _state.value
        if (s.saving || s.status != SecteurFormStatus.Ready) return
        if (ListsRepository.clean(s.name).isEmpty()) {
            _state.update { it.copy(nameError = "Saisissez le nom du quartier.") }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = quartierId
            val reason = write { if (id == null) lists.addQuartier(secteurId!!, s.name) else lists.renameQuartier(id, s.name) }
            _state.update { it.copy(saving = false, nameError = reason) }
            if (reason == null) _events.emit(SecteurFormEvent.Saved(created = id == null))
        }
    }

    fun delete() {
        val id = quartierId ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val reason = write { lists.deleteQuartier(id) }
            _state.update { it.copy(saving = false) }
            _events.emit(if (reason == null) SecteurFormEvent.Deleted else SecteurFormEvent.Refused(reason))
        }
    }

    /** Runs [block] off the main thread; null when done, or why not. */
    private suspend fun write(block: () -> ListOutcome): String? = withContext(Dispatchers.IO) {
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

    companion object {
        const val ARG_QUARTIER_ID = "quartierId"
        const val ARG_SECTEUR_ID = "secteurId"
    }
}
