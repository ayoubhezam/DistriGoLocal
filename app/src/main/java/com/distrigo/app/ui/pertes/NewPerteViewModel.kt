package com.distrigo.app.ui.pertes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.paging.PriceColumn
import com.distrigo.app.data.model.PerteType
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.StockPolicy
import com.distrigo.app.data.repository.BusinessSettingsRepository
import com.distrigo.app.data.repository.PerteRepository
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.ui.common.PagedProductList
import com.distrigo.app.ui.common.debouncedSearch
import com.distrigo.app.ui.purchases.ProductListFilters
import com.distrigo.app.ui.purchases.toListQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/** One product in a new perte's selection: how much was lost, and why. */
data class PerteCartLine(val product: Product, val typeId: Int, val typeName: String, val quantity: Double, val motif: String? = null) {
    val value: Double get() = quantity * product.purchase_price
}

/**
 * A new perte, from its list to its confirmation — held by the new perte's own graph, so leaving it
 * starts the next one empty.
 *
 * Nothing is written until "Confirmer": the selection is the products lost so far, and the list
 * leaves them out, as the Inventaire's list leaves out what was counted. The pertes are recorded at
 * the dépôt; under strict stock ("Autoriser le stock négatif" off) none may take more than the dépôt
 * holds — the dialog says so, and the repository refuses it regardless.
 */
@HiltViewModel
class NewPerteViewModel @Inject constructor(
    private val repository: PerteRepository,
    productRepository: ProductRepository,
    businessSettings: BusinessSettingsRepository,
) : ViewModel() {

    val stockPolicy: StateFlow<StockPolicy> = businessSettings.observeAllowNegativeStock()
        .map { StockPolicy(allowNegative = it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StockPolicy.ALLOWED)

    private val _types = MutableStateFlow<List<PerteType>>(emptyList())
    val types: StateFlow<List<PerteType>> = _types

    private val _cart = MutableStateFlow<List<PerteCartLine>>(emptyList())
    val cart: StateFlow<List<PerteCartLine>> = _cart

    var search by mutableStateOf("")
    var filters by mutableStateOf(ProductListFilters())
    var date by mutableStateOf(LocalDate.now())

    /** The products in stock, less those already in the selection. */
    val productList = PagedProductList(
        scope      = viewModelScope,
        repository = productRepository,
        query      = combine(snapshotFlow { filters }, debouncedSearch { search }, _cart) { f, s, cart ->
            f.toListQuery(s, priceColumn = PriceColumn.SELLING)
                .copy(inStockOnly = true, excludeIds = cart.map { it.product.id }, sort = com.distrigo.app.data.local.paging.ProductSort.NAME_ASC)
        },
    )

    private val products = productRepository

    init {
        viewModelScope.launch {
            repository.seedDefaultPerteTypesIfNeeded()
            _types.value = repository.getPerteTypes()
        }
    }

    /** The product a scanned code belongs to, or null. */
    suspend fun productByBarcode(code: String): Product? = products.findLiveProductByBarcode(code)

    /** The most a perte of [product] may take: its dépôt stock under strict stock, no limit otherwise. */
    fun capFor(product: Product): Double? = stockPolicy.value.depotCap(product)

    fun inCart(productId: Int): PerteCartLine? = _cart.value.find { it.product.id == productId }

    /** Adds [product] to the selection, or replaces its line. */
    fun put(product: Product, typeId: Int, quantity: Double, motif: String?) {
        val name = _types.value.find { it.id == typeId }?.name ?: ""
        val line = PerteCartLine(product, typeId, name, quantity, motif)
        _cart.value = _cart.value.filter { it.product.id != product.id } + line
    }

    fun remove(productId: Int) { _cart.value = _cart.value.filter { it.product.id != productId } }

    /** Records every line, dated [date] at the present time of that day; all of them or none. */
    fun confirm(onSuccess: (Int) -> Unit, onError: (String) -> Unit) {
        val lines = _cart.value
        if (lines.isEmpty()) return onError("Aucun produit")
        val at = date.atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toString()
        viewModelScope.launch {
            val result = repository.addPertes(lines.map { PerteRepository.PerteLine(it.product.id, it.typeId, it.quantity, it.motif) }, at)
            val error = result["error"] as? String
            if (error != null) onError(error) else { _cart.value = emptyList(); onSuccess(lines.size) }
        }
    }
}
