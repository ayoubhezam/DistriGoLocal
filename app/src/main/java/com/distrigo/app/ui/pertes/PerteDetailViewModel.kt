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
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.BusinessSettingsRepository
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.StockPolicy
import kotlinx.coroutines.flow.first
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
    private val productRepository: ProductRepository,
    private val businessSettings: BusinessSettingsRepository,
    savedState: SavedStateHandle
) : ViewModel() {

    /** The perte's product, live — the stock "Modifier" shows and caps — and the types it can take. */
    var product by mutableStateOf<Product?>(null); private set
    var types by mutableStateOf<List<PerteType>>(emptyList()); private set
    var policy by mutableStateOf(StockPolicy.ALLOWED); private set
    var saving by mutableStateOf(false); private set
    var saveError by mutableStateOf(""); private set

    /**
     * The most this perte may now take: under strict stock, what its place holds plus what the perte
     * already took from it; no limit when negative stock is allowed and the perte is at the dépôt.
     */
    fun cap(): Double? {
        val p = product ?: return null
        val own = perte?.quantity ?: 0.0
        return if (perte?.source == "camion") p.camion_stock + own else policy.depotCap(p, ownQuantity = own)
    }

    /** What the perte's place holds without the perte: the stock it was taken from. */
    fun stockBefore(): Double {
        val p = product ?: return 0.0
        val own = perte?.quantity ?: 0.0
        return (if (perte?.source == "camion") p.camion_stock else p.stock - p.camion_stock) + own
    }

    /** Changes the perte's type and quantity — its product, place and date stay. */
    fun update(typeId: Int, quantity: Double, onDone: () -> Unit) {
        val current = perte ?: return
        saving = true; saveError = ""
        viewModelScope.launch {
            val result = repository.updatePerte(
                id = current.id, productId = current.product_id, typeId = typeId, quantity = quantity,
                source = current.source, dateTime = current.date_time, motif = current.motif, photoPath = current.photo_path
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
