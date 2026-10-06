package com.distrigo.app.ui.retours

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.Quantity
import com.distrigo.app.data.model.RetourClientMotifs
import com.distrigo.app.data.model.RetourFournisseurMotifs
import com.distrigo.app.data.model.StockPolicy
import com.distrigo.app.data.repository.BusinessSettingsRepository
import com.distrigo.app.data.repository.ProductRepository
import com.distrigo.app.data.repository.RetourClientRepository
import com.distrigo.app.data.repository.RetourFournisseurRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** A product that can come back, and the most of it that can: delivered or received, less what was returned. */
data class ReturnableProduct(val product: Product, val maxQuantity: Double)

/** One product in a return's selection: how much comes back, and why. */
data class RetourCartLine(val returnable: ReturnableProduct, val quantity: Double, val motif: String) {
    val product: Product get() = returnable.product
}

/** Why a quantity cannot be returned, or null when it can: nothing, or more than [max]. */
fun retourQuantityError(quantity: Double?, max: Double, unit: String): String? = when {
    quantity == null || quantity <= 0 -> "Quantité invalide"
    Quantity.exceeds(quantity, max) -> "Maximum retournable : ${com.distrigo.app.ui.common.formatQty(max)} $unit"
    else -> null
}

/**
 * A new return, from its list to its confirmation — a client's or a supplier's; held by the return's
 * own graph, so leaving it starts the next one empty.
 *
 * The list is only what can come back: what was sold to this client (delivered) or bought from this
 * supplier (received), less what was already returned — for a supplier, also no more than the dépôt
 * holds under strict stock. Nothing is written until "Confirmer"; each line keeps its own motif, which
 * decides its stock effect and its linked perte.
 */
abstract class NewRetourViewModel(
    savedState: SavedStateHandle,
    private val products: ProductRepository,
    partyArg: String,
) : ViewModel() {

    protected val partyId: Int = savedState.get<Int>(partyArg) ?: -1

    abstract val title: String
    /** "ce client" / "ce fournisseur", for "never sold to …". */
    abstract val partyPhrase: String
    abstract val motifs: List<String>
    /** The price a line is valued at: selling for a client, purchase for a supplier. */
    abstract fun priceOf(product: Product): Double
    protected abstract suspend fun loadReturnable(): List<ReturnableProduct>
    protected abstract suspend fun save(date: LocalDate, lines: List<RetourCartLine>): String?

    var partyName by mutableStateOf<String?>(null); protected set

    /**
     * Everything that can come back, before the search and the selection. Loaded on its first
     * subscriber, never from this constructor: it runs before a subclass's own fields are set.
     */
    val eligible: StateFlow<List<ReturnableProduct>?> = flow { emit(loadReturnable()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _cart = MutableStateFlow<List<RetourCartLine>>(emptyList())
    val cart: StateFlow<List<RetourCartLine>> = _cart

    var search by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now())

    /** The list on screen: eligible, not already selected, matching the search by name or code. */
    fun visible(all: List<ReturnableProduct>, cart: List<RetourCartLine>, query: String): List<ReturnableProduct> {
        val taken = cart.map { it.product.id }.toSet()
        // Every word, in the name or a barcode — "har bl 25" — as the product lists search.
        val tokens = com.distrigo.app.ui.common.searchTokens(query)
        return all.filter { it.product.id !in taken }
            .filter { r -> com.distrigo.app.ui.common.productMatchesTokens(r.product, tokens) }
            .sortedBy { it.product.name.lowercase() }
    }

    /** The eligible product a scanned code names; or why not — unknown, or never sold/bought here. */
    suspend fun byBarcode(code: String): Pair<ReturnableProduct?, String?> {
        val product = products.findLiveProductByBarcode(code) ?: return null to "Aucun produit trouvé pour ce code-barres"
        val match = eligible.value?.find { it.product.id == product.id }
            ?: return null to "« ${product.name} » ne peut pas être retourné par $partyPhrase"
        return match to null
    }

    fun inCart(productId: Int): RetourCartLine? = _cart.value.find { it.product.id == productId }

    fun put(returnable: ReturnableProduct, quantity: Double, motif: String) {
        _cart.value = _cart.value.filter { it.product.id != returnable.product.id } + RetourCartLine(returnable, quantity, motif)
    }

    fun remove(productId: Int) { _cart.value = _cart.value.filter { it.product.id != productId } }

    fun total(lines: List<RetourCartLine>): Double = lines.sumOf { it.quantity * priceOf(it.product) }

    fun confirm(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val lines = _cart.value
        if (lines.isEmpty()) return onError("Ajoutez au moins un produit")
        viewModelScope.launch {
            val error = save(date, lines)
            if (error != null) onError(error) else { _cart.value = emptyList(); onSuccess() }
        }
    }

    protected fun items(lines: List<RetourCartLine>) =
        lines.map { mapOf("product_id" to it.product.id, "quantity" to it.quantity, "motif" to it.motif) }
}

/** A client's return: what was delivered to them, back into the camion or out as a perte. */
@HiltViewModel
class NewRetourClientViewModel @Inject constructor(
    savedState: SavedStateHandle,
    products: ProductRepository,
    private val db: AppDatabase,
    private val repository: RetourClientRepository,
) : NewRetourViewModel(savedState, products, "clientId") {
    override val title = "Retour client"
    override val partyPhrase = "ce client — il ne lui a jamais été livré"
    override val motifs = RetourClientMotifs.ALL.map { it.id }
    override fun priceOf(product: Product) = product.selling_price

    private val productRepository = products

    override suspend fun loadReturnable(): List<ReturnableProduct> {
        partyName = db.clientDao().getClientById(partyId)?.name
        val sold = db.venteDao().getSoldQuantitiesForClient(partyId, "delivered").associate { it.product_id to it.total_quantity }
        val returned = db.retourClientDao().getReturnedQuantitiesForClient(partyId).associate { it.product_id to it.total_quantity }
        val byId = productRepository.getLiveProductsByIds(sold.keys).associateBy { it.id }
        return sold.mapNotNull { (productId, soldQty) ->
            val remaining = Quantity.normalize(soldQty - (returned[productId] ?: 0.0))
            if (remaining > 0) byId[productId]?.let { ReturnableProduct(it, remaining) } else null
        }
    }

    override suspend fun save(date: LocalDate, lines: List<RetourCartLine>): String? =
        repository.createRetour(partyId, null, date.toString(), null, null, items(lines))["error"] as? String
}

/** A return to a supplier: what was received from them, out of the dépôt. */
@HiltViewModel
class NewRetourFournisseurViewModel @Inject constructor(
    savedState: SavedStateHandle,
    products: ProductRepository,
    private val db: AppDatabase,
    private val repository: RetourFournisseurRepository,
    private val businessSettings: BusinessSettingsRepository,
) : NewRetourViewModel(savedState, products, "supplierId") {
    override val title = "Retour fournisseur"
    override val partyPhrase = "ce fournisseur — il ne vous l'a jamais livré"
    override val motifs = RetourFournisseurMotifs.ALL.map { it.id }
    override fun priceOf(product: Product) = product.purchase_price

    private val productRepository = products

    override suspend fun loadReturnable(): List<ReturnableProduct> {
        partyName = db.supplierDao().getSupplierById(partyId)?.name
        val purchased = db.purchaseDao().getPurchasedQuantitiesForSupplier(partyId, "received").associate { it.product_id to it.total_quantity }
        val returned = db.retourFournisseurDao().getReturnedQuantitiesForSupplier(partyId).associate { it.product_id to it.total_quantity }
        val byId = productRepository.getLiveProductsByIds(purchased.keys).associateBy { it.id }
        // Strict stock: a return goes back out of the dépôt, so no more than the dépôt holds.
        val policy = StockPolicy(businessSettings.observeAllowNegativeStock().first())
        return purchased.mapNotNull { (productId, purchasedQty) ->
            val product = byId[productId] ?: return@mapNotNull null
            val returnable = Quantity.normalize(purchasedQty - (returned[productId] ?: 0.0))
            val remaining = policy.depotCap(product)?.let { minOf(returnable, it) } ?: returnable
            if (remaining > 0) ReturnableProduct(product, remaining) else null
        }
    }

    override suspend fun save(date: LocalDate, lines: List<RetourCartLine>): String? =
        repository.createRetour(partyId, date.toString(), null, null, items(lines))["error"] as? String
}
