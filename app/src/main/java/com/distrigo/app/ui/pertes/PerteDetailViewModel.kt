package com.distrigo.app.ui.pertes

import com.distrigo.app.ui.common.userMessage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.model.Perte
import com.distrigo.app.data.model.PerteType
import com.distrigo.app.data.repository.PerteRepository
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.BusinessSettingsRepository
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.StockPolicy
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.distrigo.app.R
import com.distrigo.app.core.text.UiText

/**
 * One perte, read-only: what tapping it in its list opens. It moves stock, so changing it takes an
 * explicit "Modifier" and a confirmation, and deleting it says the quantity goes back to stock.
 */
@HiltViewModel
class PerteDetailViewModel @Inject constructor(
    private val repository: PerteRepository,
    private val productRepository: ProductRepository,
    private val businessSettings: BusinessSettingsRepository,
    savedState: SavedStateHandle
) : ViewModel() {

    /** The perte's product, live — the stock "Modifier" shows and caps — and the types it can take. */
    var product by mutableStateOf<Product?>(null); private set
    var types by mutableStateOf<List<PerteType>>(emptyList()); private set
    /** « Perdus pendant le transport »: picking it in « Modifier » moves the place to the camion (STK-03). */
    var transportTypeId by mutableStateOf<Int?>(null); private set
    var policy by mutableStateOf(StockPolicy.ALLOWED); private set
    var saving by mutableStateOf(false); private set
    var saveError by mutableStateOf(""); private set

    /** What the perte took from [source]: its quantity at its own place, nothing elsewhere. */
    private fun ownAt(source: String): Double = if (perte?.source == source) perte?.quantity ?: 0.0 else 0.0

    /**
     * The most this perte may now take at [source] (STK-03): what the camion holds, or under strict stock what the
     * dépôt holds — each plus what the perte already took from there; no limit at the dépôt when negative stock is allowed.
     */
    fun cap(source: String): Double? {
        val p = product ?: return null
        return if (source == PerteSource.CAMION) maxOf(0.0, p.camion_stock + ownAt(source)) else policy.depotCap(p, ownQuantity = ownAt(source))
    }

    /** What [source] holds without the perte: the stock it would be taken from. */
    fun stockBefore(source: String): Double {
        val p = product ?: return 0.0
        return (if (source == PerteSource.CAMION) p.camion_stock else p.stock - p.camion_stock) + ownAt(source)
    }

    /** Creates a custom type from the dialog; [onDone] gets it, or why not. */
    fun addType(name: String, onDone: (PerteType?, String?) -> Unit) {
        viewModelScope.launch {
            val (created, failure) = repository.createPerteType(name)
            if (created != null) types = repository.getPerteTypes()
            onDone(created, failure)
        }
    }

    /** Changes the perte's type, quantity and place (STK-03) — its product and date stay. */
    fun update(typeId: Int, quantity: Double, motif: String?, source: String, onDone: () -> Unit) {
        val current = perte ?: return
        saving = true; saveError = ""
        viewModelScope.launch {
            val result = repository.updatePerte(
                id = current.id, productId = current.product_id, typeId = typeId, quantity = quantity,
                source = source, dateTime = current.date_time, motif = motif, photoPath = current.photo_path
            )
            saving = false
            val error = result["error"] as? String
            if (error != null) saveError = error else { load(); onDone() }
        }
    }

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
            product = found?.let { productRepository.getLiveProduct(it.product_id) }
            types = repository.getPerteTypes()
            transportTypeId = repository.findDefaultPerteType(com.distrigo.app.data.model.DefaultPerteType.PERTE_TRANSPORT)?.id
            policy = StockPolicy(allowNegative = businessSettings.observeAllowNegativeStock().first())
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
                mapOf("error" to (userMessage(e, com.distrigo.app.core.text.AppStrings.write(UiText.Res(R.string.pertes_delete_failed)))))
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
