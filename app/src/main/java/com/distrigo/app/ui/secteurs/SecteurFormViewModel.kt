package com.distrigo.app.ui.secteurs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.lists.ListKind
import com.distrigo.app.data.lists.ListOutcome
import com.distrigo.app.data.lists.ListsRepository
import com.distrigo.app.data.lists.SecteurDetail
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

/** What the form holds; [SecteurFormState.dirty] compares it with what was loaded. */
data class SecteurFields(
    val name: String = "",
    val commune: String? = null,
    val wilaya: String? = null,
    val description: String = "",
)

sealed interface SecteurFormStatus {
    data object Loading : SecteurFormStatus
    data object Ready : SecteurFormStatus
    data class Error(val message: String) : SecteurFormStatus
}

data class SecteurFormState(
    val status: SecteurFormStatus = SecteurFormStatus.Loading,
    val fields: SecteurFields = SecteurFields(),
    val loaded: SecteurFields = SecteurFields(),
    /** What uses the secteur, so its commune is read-only (« 2 quartiers et 14 clients »); null when free. */
    val lockedBy: String? = null,
    val nameError: String? = null,
    val wilayaError: String? = null,
    val communeError: String? = null,
    val saving: Boolean = false,
) {
    val dirty: Boolean get() = fields != loaded
}

sealed interface SecteurFormEvent {
    data class Saved(val created: Boolean) : SecteurFormEvent
    data object Deleted : SecteurFormEvent
    /** A refusal that belongs to no field, e.g. a delete refused because something uses the secteur. */
    data class Refused(val reason: String) : SecteurFormEvent
}

/**
 * « Nouveau secteur » / « Modifier le secteur » (owner's prototype v5, 2026-10-24): a name, a commune, a description.
 * The name is free and unique within its commune; the commune is read-only while anything uses the secteur.
 */
@HiltViewModel
class SecteurFormViewModel @Inject constructor(
    private val lists: ListsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** The secteur edited; null for a new one. */
    val secteurId: Long? = savedStateHandle.get<Long>(ARG_SECTEUR_ID)?.takeIf { it > 0 }

    private val _state = MutableStateFlow(SecteurFormState())
    val state: StateFlow<SecteurFormState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SecteurFormEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SecteurFormEvent> = _events.asSharedFlow()

    init {
        val id = secteurId
        if (id == null) _state.update { it.copy(status = SecteurFormStatus.Ready) }
        else viewModelScope.launch {
            val secteur = withContext(Dispatchers.IO) { runCatching { lists.secteur(id) } }
            secteur.fold(
                onSuccess = { found -> if (found == null) fail("Ce secteur n'existe plus.") else load(found) },
                onFailure = { fail(userMessage(it)) },
            )
        }
    }

    private fun load(secteur: SecteurDetail) {
        val fields = SecteurFields(secteur.name, secteur.commune, secteur.wilaya, secteur.description)
        _state.update { it.copy(status = SecteurFormStatus.Ready, fields = fields, loaded = fields, lockedBy = secteur.usedBy) }
    }

    private fun fail(message: String) = _state.update { it.copy(status = SecteurFormStatus.Error(message)) }

    fun setName(name: String) = _state.update { it.copy(fields = it.fields.copy(name = name), nameError = null) }

    fun setDescription(description: String) = _state.update { it.copy(fields = it.fields.copy(description = description)) }

    /** A wilaya first; another one clears the commune, which must be one of its own. */
    fun setWilaya(wilaya: String) = _state.update {
        when {
            it.lockedBy != null -> it
            it.fields.wilaya == wilaya -> it.copy(wilayaError = null)
            else -> it.copy(fields = it.fields.copy(wilaya = wilaya, commune = null), wilayaError = null)
        }
    }

    fun setCommune(commune: String) = _state.update {
        if (it.lockedBy != null || it.fields.wilaya == null) it
        else it.copy(fields = it.fields.copy(commune = commune), communeError = null, nameError = null)
    }

    /** « Suggestion : Sedrata »: the commune's name as the secteur's, editable after. */
    fun useSuggestion() = _state.update { s -> s.fields.commune?.let { s.copy(fields = s.fields.copy(name = it), nameError = null) } ?: s }

    fun save() {
        val s = _state.value
        if (s.saving || s.status != SecteurFormStatus.Ready) return
        val f = s.fields
        val nameError = if (ListsRepository.clean(f.name).isEmpty()) "Saisissez le nom du secteur." else null
        val wilayaError = if (f.wilaya.isNullOrBlank()) "Sélectionnez une wilaya." else null
        val communeError = if (wilayaError == null && f.commune.isNullOrBlank()) "Sélectionnez une commune." else null
        if (nameError != null || wilayaError != null || communeError != null) {
            _state.update { it.copy(nameError = nameError, wilayaError = wilayaError, communeError = communeError) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = secteurId
            val reason = write {
                if (id == null) lists.add(ListKind.SECTEURS, f.name, commune = f.commune, wilaya = f.wilaya, description = f.description)
                else lists.updateSecteur(id, f.name, f.commune!!, f.wilaya, f.description)
            }
            _state.update { it.copy(saving = false, nameError = reason) }
            if (reason == null) _events.emit(SecteurFormEvent.Saved(created = id == null))
        }
    }

    fun delete() {
        val id = secteurId ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val reason = write { lists.delete(ListKind.SECTEURS, id) }
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
        const val ARG_SECTEUR_ID = "secteurId"
    }
}
