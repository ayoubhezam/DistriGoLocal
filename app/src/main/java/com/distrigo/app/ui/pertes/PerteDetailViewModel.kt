package com.distrigo.app.ui.pertes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.Perte
import com.distrigo.app.data.model.PerteType
import com.distrigo.app.data.repository.PerteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One perte, read-only: what tapping it in its list opens. It moves stock, so changing it takes an
 * explicit "Modifier" and a confirmation, and deleting it says the quantity goes back to stock.
 */
@HiltViewModel
class PerteDetailViewModel @Inject constructor(
    private val repository: PerteRepository,
    savedState: SavedStateHandle
) : ViewModel() {

    val perteId: Int = savedState.get<Int>(ARG_PERTE) ?: -1

    var perte by mutableStateOf<Perte?>(null); private set
    var type by mutableStateOf<PerteType?>(null); private set
    /** True once a load found nothing: the perte was deleted elsewhere. */
    var missing by mutableStateOf(false); private set
    var deleting by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    /** A perte made by a client or supplier return: it changes with that return, not here. */
    val isLinked: Boolean get() = perte?.source_type != null

    /** Called each time the screen shows, so coming back from the form shows what it saved. */
    fun load() {
        viewModelScope.launch {
            val found = repository.getPerte(perteId)
            perte = found
            missing = found == null
            type = found?.let { repository.getPerteType(it.type_id) }
        }
    }

    fun delete(onDeleted: (typeId: Int) -> Unit) {
        val current = perte ?: return
        deleting = true
        error = null
        viewModelScope.launch {
            val result = try {
                repository.deletePerte(current.id)
            } catch (e: Exception) {
                mapOf("error" to (e.message ?: "La suppression a échoué."))
            }
            val failure = result["error"] as? String
            if (failure != null) {
                error = failure
                deleting = false
            } else {
                onDeleted(current.type_id)
            }
        }
    }

    companion object {
        const val ARG_PERTE = "perteId"
    }
}
