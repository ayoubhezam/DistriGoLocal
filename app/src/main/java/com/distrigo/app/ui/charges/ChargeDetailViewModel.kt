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

    /** Called each time the screen shows, so coming back from the form shows what it saved. */
    fun load() {
        viewModelScope.launch {
            val found = repository.getCharge(chargeId)
            charge = found
            missing = found == null
            if (found != null) {
                subType = repository.getAllSubTypes().find { it.id == found.subtype_id }
                type = repository.getChargeTypes().find { it.id == found.type_id }
            }
        }
    }

    fun delete(onDeleted: (SavedCharge) -> Unit) {
        val current = charge ?: return
        deleting = true
        error = null
        viewModelScope.launch {
            try {
                repository.deleteCharge(current.id)
                onDeleted(SavedCharge(current.id, current.subtype_id, current.type_id, current.montant, isNew = false))
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
