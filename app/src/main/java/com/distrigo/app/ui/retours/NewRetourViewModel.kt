package com.distrigo.app.ui.retours

import com.distrigo.app.ui.common.caughtAsError

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.local.dao.ReturnableSaleRow
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.Quantity
import com.distrigo.app.data.model.RetourClientMotifs
import com.distrigo.app.data.model.RetourFournisseurMotifs
import com.distrigo.app.data.model.StockPolicy
import com.distrigo.app.data.model.numberLabel
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
import com.distrigo.app.R
import com.distrigo.app.core.text.UiText

/**
 * Something that can come back, and the most of it that can.
 *
 * For a client, a line of the sale the return is made from (FIN-02): [lineId] is that sale line, [unitPrice] the
 * price it was sold at, [maxQuantity] what was sold less what already came back from it. For a supplier, a product:
 * [lineId] is the product, [unitPrice] its purchase price, [maxQuantity] what was received less what was returned.
 */
data class ReturnableProduct(
    val product    : Product,
    val maxQuantity: Double,
    val unitPrice  : Double = product.selling_price,
    val lineId     : Int = product.id,
)

/**
 * A line of a return's selection: how much of a returnable line comes back, and why. A line may be split across motifs
 * (2 périmés, 3 abîmés), never twice under the same one.
 */
data class RetourCartLine(val returnable: ReturnableProduct, val quantity: Double, val motif: String) {
    val product: Product get() = returnable.product
    /** A line's identity in the selection: what it returns and its motif. */
    val key: String get() = "${returnable.lineId}|$motif"
    /** What it comes to, at the price of what it returns. */
    val total: Double get() = quantity * returnable.unitPrice
}

/** Why a quantity cannot be returned, or null when it can: nothing, or more than [max]. */
fun retourQuantityError(quantity: Double?, max: Double, unit: String): String? = when {
    quantity == null || quantity <= 0 -> com.distrigo.app.core.text.AppStrings.write(UiText.Res(R.string.common_invalid_quantity))
    Quantity.exceeds(quantity, max) -> com.distrigo.app.core.text.AppStrings.write(UiText.Res(R.string.retours_max_value, com.distrigo.app.core.text.AppStrings.write(com.distrigo.app.ui.common.quantityText(max, unit))))
    else -> null
}

/**
 * A new return, from its list to its confirmation — a client's or a supplier's; held by the return's
 * own graph, so leaving it starts the next one empty.
 *
 * The list is only what can come back: for a client, the lines of the sale picked first, less what already came back
 * from them; for a supplier, what was received from them less what was returned — also no more than the dépôt holds
 * under strict stock. Nothing is written until "Confirmer"; each line keeps its own motif, which decides its stock
 * effect and its linked perte.
 */
abstract class NewRetourViewModel(
    savedState: SavedStateHandle,
    private val products: ProductRepository,
    partyArg: String,
) : ViewModel() {

    protected val partyId: Int = savedState.get<Int>(partyArg) ?: -1

    @get:androidx.annotation.StringRes abstract val title: Int
    /** « … ne peut pas être retourné… », for a scanned product that is not in the list. */
    @get:androidx.annotation.StringRes abstract val notReturnable: Int
    abstract val motifs: List<String>
    protected abstract suspend fun loadReturnable(): List<ReturnableProduct>
    protected abstract suspend fun save(date: LocalDate, lines: List<RetourCartLine>): String?

    var partyName by mutableStateOf<String?>(null); protected set

    /** The sale the return is made from, as its screens name it — a client's only. */
    var saleLabel by mutableStateOf<String?>(null); protected set

    /** What the screens show under their title: who, and for a client the sale. */
    val subtitle: String? get() = listOfNotNull(partyName, saleLabel).joinToString(" · ").ifEmpty { null }

    /** True when each line is valued at the price of the sale it comes from (a client's): the list shows that price. */
    open val pricedBySale: Boolean get() = false

    /** Where the goods come back — "depot" or "camion" — when the return lets the user choose (a client's); else null. */
    var location by mutableStateOf<String?>(null)

    private val _eligible = MutableStateFlow<List<ReturnableProduct>?>(null)

    /** Everything that can come back, before the search and the selection; null while it loads. */
    val eligible: StateFlow<List<ReturnableProduct>?> = _eligible

    /** Loads [eligible] again. Never from this constructor: it runs before a subclass's own fields are set. */
    protected fun reload() {
        _eligible.value = null
        viewModelScope.launch { _eligible.value = loadReturnable() }
    }

    private val _cart = MutableStateFlow<List<RetourCartLine>>(emptyList())
    val cart: StateFlow<List<RetourCartLine>> = _cart

    protected fun clearCart() { _cart.value = emptyList() }

    var search by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now())

    /** What is left of [r] to select: its maximum, less its lines in [cart] — all but [except], the one being changed. */
    fun left(r: ReturnableProduct, cart: List<RetourCartLine>, except: RetourCartLine? = null): Double =
        Quantity.normalize(r.maxQuantity - cart.filter { it.returnable.lineId == r.lineId && it.key != except?.key }.sumOf { it.quantity })

    /**
     * The list on screen: eligible with something left to select, matching the search by name or code. Each comes
     * with what is left as its maximum: 8 that can come back, 2 of them selected, shows 6.
     */
    fun visible(all: List<ReturnableProduct>, cart: List<RetourCartLine>, query: String): List<ReturnableProduct> {
        // Every word, in the name or a barcode — "har bl 25" — as the product lists search.
        val tokens = com.distrigo.app.ui.common.searchTokens(query)
        return all.mapNotNull { r -> left(r, cart).takeIf { it > 0 }?.let { r.copy(maxQuantity = it) } }
            .filter { r -> com.distrigo.app.ui.common.productMatchesTokens(r.product, tokens) }
            .sortedBy { it.product.name.lowercase() }
    }

    /**
     * The eligible line a scanned code names, with what is left of it to select as its maximum; or why not —
     * unknown, not returnable here, or all of it already selected.
     */
    suspend fun byBarcode(code: String): Pair<ReturnableProduct?, String?> {
        val product = products.findLiveProductByBarcode(code) ?: return null to com.distrigo.app.core.text.AppStrings.write(UiText.Res(R.string.common_barcode_not_found))
        val matches = eligible.value?.filter { it.product.id == product.id }.orEmpty()
        if (matches.isEmpty()) return null to com.distrigo.app.core.text.AppStrings.write(UiText.Res(notReturnable, product.name))
        val match = matches.firstOrNull { left(it, _cart.value) > 0 }
            ?: return null to com.distrigo.app.core.text.AppStrings.write(UiText.Res(R.string.retours_already_selected, product.name))
        return match.copy(maxQuantity = left(match, _cart.value)) to null
    }

    /** Adds [quantity] of the returnable line [lineId] for [motif]: onto its line with that motif, or as a new line. */
    fun add(lineId: Int, quantity: Double, motif: String) {
        val returnable = eligible.value?.find { it.lineId == lineId } ?: return
        val cart = _cart.value
        val same = cart.find { it.returnable.lineId == lineId && it.motif == motif }
        _cart.value = if (same == null) cart + RetourCartLine(returnable, quantity, motif)
                      else cart.map { if (it.key == same.key) it.copy(quantity = Quantity.normalize(it.quantity + quantity)) else it }
    }

    /** Changes [line] to [quantity] and [motif]; moved to a motif another line of the same has, it joins that line. */
    fun change(line: RetourCartLine, quantity: Double, motif: String) {
        val cart = _cart.value
        val same = cart.find { it.returnable.lineId == line.returnable.lineId && it.motif == motif && it.key != line.key }
        _cart.value = if (same == null) cart.map { if (it.key == line.key) it.copy(quantity = quantity, motif = motif) else it }
                      else cart.filter { it.key != line.key }
                          .map { if (it.key == same.key) it.copy(quantity = Quantity.normalize(it.quantity + quantity)) else it }
    }

    fun remove(line: RetourCartLine) { _cart.value = _cart.value.filter { it.key != line.key } }

    fun total(lines: List<RetourCartLine>): Double = lines.sumOf { it.total }

    fun confirm(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val lines = _cart.value
        if (lines.isEmpty()) return onError(com.distrigo.app.core.text.AppStrings.write(UiText.Res(R.string.retours_add_one)))
        viewModelScope.launch {
            // A failure is said on the summary, which stays open, rather than stopping the app
            val error = caughtAsError { save(date, lines)?.let { mapOf("error" to it) } ?: emptyMap() }["error"] as? String
            if (error != null) onError(error) else { _cart.value = emptyList(); onSuccess() }
        }
    }
}

/**
 * A client's return, made from one of their sales (FIN-02, STK-02, UX-01): the sale is picked first, newest first,
 * whatever its status; its lines are what can come back, at their sale price, up to what was sold less what already
 * came back; the goods come back to the sale's source unless the user picks the other place.
 */
@HiltViewModel
class NewRetourClientViewModel @Inject constructor(
    savedState: SavedStateHandle,
    products: ProductRepository,
    private val db: AppDatabase,
    private val repository: RetourClientRepository,
) : NewRetourViewModel(savedState, products, "clientId") {
    override val title = R.string.common_kind_client_return
    override val notReturnable = R.string.retours_not_returnable_client
    override val motifs = RetourClientMotifs.ALL.map { it.id }
    override val pricedBySale get() = true

    private val productRepository = products

    /** The client's sales with something left to return, newest first; null while they load. */
    val sales: StateFlow<List<ReturnableSaleRow>?> = flow {
        partyName = db.clientDao().getClientById(partyId)?.name
        emit(db.retourClientDao().getReturnableSales(partyId))
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** The sale picked, or null before one is. */
    var sale by mutableStateOf<ReturnableSaleRow?>(null); private set

    /**
     * Makes the return from [picked]: its lines become the list, and the goods come back where it left from. Another
     * sale than the one picked before starts the selection again — its lines are not this sale's.
     */
    fun pickSale(picked: ReturnableSaleRow) {
        if (sale?.id == picked.id) return
        sale = picked
        saleLabel = numberLabel(picked.numero, picked.id)
        location = if (picked.source == "camion") "camion" else "depot"
        search = ""
        clearCart()
        reload()
    }

    override suspend fun loadReturnable(): List<ReturnableProduct> {
        val picked = sale ?: return emptyList()
        val lines = db.retourClientDao().getSaleLinesWithReturned(picked.id)
        val byId = productRepository.getLiveProductsByIds(lines.map { it.product_id }.toSet()).associateBy { it.id }
        return lines.mapNotNull { line ->
            if (line.left <= 0) null
            else byId[line.product_id]?.let { ReturnableProduct(it, line.left, unitPrice = line.unit_price, lineId = line.vente_item_id) }
        }
    }

    override suspend fun save(date: LocalDate, lines: List<RetourCartLine>): String? {
        val picked = sale ?: return com.distrigo.app.core.text.AppStrings.write(UiText.Res(R.string.retours_add_one))
        val items = lines.map {
            mapOf("vente_item_id" to it.returnable.lineId, "product_id" to it.product.id, "quantity" to it.quantity, "motif" to it.motif)
        }
        return repository.createRetour(
            clientId = partyId, venteId = picked.id, emplacement = location ?: "depot",
            date = date.toString(), motif = null, note = null, items = items
        )["error"] as? String
    }
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
    override val title = R.string.common_kind_supplier_return
    override val notReturnable = R.string.retours_not_returnable_supplier
    override val motifs = RetourFournisseurMotifs.ALL.map { it.id }

    private val productRepository = products

    init { reload() }

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
            if (remaining > 0) ReturnableProduct(product, remaining, unitPrice = product.purchase_price) else null
        }
    }

    override suspend fun save(date: LocalDate, lines: List<RetourCartLine>): String? =
        repository.createRetour(
            partyId, date.toString(), null, null,
            lines.map { mapOf("product_id" to it.product.id, "quantity" to it.quantity, "motif" to it.motif) }
        )["error"] as? String
}
