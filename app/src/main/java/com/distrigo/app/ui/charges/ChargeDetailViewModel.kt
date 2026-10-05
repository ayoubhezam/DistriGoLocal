package com.distrigo.app.ui.charges

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.Charge
import com.distrigo.app.data.model.ChargeSubType
import com.distrigo.app.data.model.ChargeType
import com.distrigo.app.data.repository.ChargeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One expense, read-only: what tapping it in its list opens. Changing it takes an explicit
 * "Modifier" and a confirmation; the form is never where a record is merely looked at.
 */
@HiltViewModel
class ChargeDetailViewModel @Inject constructor(
    private val repository: ChargeRepository,
    savedState: SavedStateHandle
) : ViewModel() {

    val chargeId: Int = savedState.get<Int>(ARG_CHARGE) ?: -1

    var charge by mutableStateOf<Charge?>(null); private set
    var subType by mutableStateOf<ChargeSubType?>(null); private set
    var type by mutableStateOf<ChargeType?>(null); private set
    /** True once a load found nothing: the charge was deleted elsewhere. */
    var missing by mutableStateOf(false); private set
    var deleting by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    /** Every type and sub-type, for "Modifier", which can move the charge to another. */
    var types by mutableStateOf<List<ChargeType>>(emptyList()); private set
    var subTypes by mutableStateOf<List<ChargeSubType>>(emptyList()); private set
    var saving by mutableStateOf(false); private set
    var saveError by mutableStateOf(""); private set

    /** The charge as the dialog takes it: its day, the time kept for when it is saved again. */
    fun input(): ChargeInput? = charge?.let { c ->
        val day = runCatching { java.time.Instant.parse(c.date_time).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
            .getOrDefault(java.time.LocalDate.now())
        ChargeInput(c.subtype_id, c.montant, c.fournisseur, day, c.note)
    }

    /** Saves the dialog: the day changes, the charge keeps the time of day it had. */
    fun update(input: ChargeInput, onDone: () -> Unit) {
        val current = charge ?: return
        saving = true; saveError = ""
        viewModelScope.launch {
            try {
                repository.updateCharge(
                    id = current.id, montant = input.montant, dateTime = chargeInstant(input.date, timeOf = current.date_time),
                    fournisseur = input.fournisseur, note = input.note, subtypeId = input.subtypeId
                )
                load(); onDone()
            } catch (e: Exception) {
                saveError = e.message ?: "Enregistrement impossible"
            }
            saving = false
        }
    }

    fun addSubType(typeId: Int, name: String, hasFournisseur: Boolean, onDone: (ChargeSubType?, String?) -> Unit) {
        viewModelScope.launch {
            val (created, failure) = repository.createSubType(typeId, name, hasFournisseur)
            if (created != null) subTypes = repository.getAllSubTypes()
            onDone(created, failure)
        }
    }

    /** Called each time the screen shows, so coming back from the form shows what it saved. */
    fun load() {
        viewModelScope.launch {
            val found = repository.getCharge(chargeId)
            charge = found
            missing = found == null
            if (found != null) {
                types = repository.getChargeTypes()
                subTypes = repository.getAllSubTypes()
                subType = subTypes.find { it.id == found.subtype_id }
                type = types.find { it.id == found.type_id }
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val current = charge ?: return
        deleting = true
        error = null
        viewModelScope.launch {
            try {
                repository.deleteCharge(current.id)
                onDeleted()
            } catch (e: Exception) {
                error = e.message ?: "La suppression a échoué."
                deleting = false
            }
        }
    }

    companion object {
        const val ARG_CHARGE = "chargeId"
    }
}
