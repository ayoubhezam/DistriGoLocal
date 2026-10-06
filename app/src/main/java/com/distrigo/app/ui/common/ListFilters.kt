package com.distrigo.app.ui.common

import com.distrigo.app.data.model.barcodeContains
import java.time.ZoneId
import com.distrigo.app.data.time.BusinessDates
import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.PurchaseOrder
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.data.model.Vente

// ── List search, filtering and grouping, as plain functions ──────────────────────────────────────
//
// Each of these was written inline in a composable body, where it re-ran on every recomposition —
// not only on a keystroke but on every sheet opened, dialog shown or loading flag flipped — over
// the whole list. As functions of their inputs, a screen can wrap a call in remember(inputs…) and
// pay for it only when one of those inputs changes, and the logic can be tested on its own.
//
// Every function returns exactly what the inline code it replaced returned, element for element
// and in the same order. ListFiltersEquivalenceTest keeps a verbatim copy of that code and checks
// it against these on randomized 10 000-row lists.

private val WHITESPACE = "\\s+".toRegex()

/**
 * The words a search box's text is split into; a row matches when it contains every one of them.
 *
 * The regex is compiled once, here. Most screens built it inside their per-row predicate, so a
 * search over N rows compiled it N times on every recomposition.
 */
fun searchTokens(query: String): List<String> =
    query.trim().split(WHITESPACE).filter { it.isNotEmpty() }

/** True when every token appears in at least one of [fields], ignoring case: the app's one way of searching. */
fun matchesAllTokens(tokens: List<String>, vararg fields: String?): Boolean =
    tokens.all { token -> fields.any { it?.contains(token, ignoreCase = true) == true } }

// ── Products ──

/** True when every token appears in the product's name or its barcode, ignoring case. */
fun productMatchesTokens(product: Product, tokens: List<String>): Boolean =
    tokens.isEmpty() || tokens.all { token ->
        product.name.contains(token, ignoreCase = true) ||
            product.barcodeContains(token)
    }

/** Products found by name or barcode: the Vente form's product step and the inventory search. */
fun searchProducts(products: List<Product>, query: String): List<Product> {
    val tokens = searchTokens(query)
    return products.filter { productMatchesTokens(it, tokens) }
}

// ── Clients ──

/**
 * Clients found by name or phone, of one customer type ("all" for any), and — when [debtOnly] —
 * only those who owe something. The client picker searches alone and keeps the defaults.
 */
fun filterClients(
    clients    : List<Client>,
    query      : String,
    typeFilter : String  = "all",
    debtOnly   : Boolean = false
): List<Client> {
    val tokens = searchTokens(query)
    return clients.filter { c ->
        val matchesSearch = tokens.isEmpty() || tokens.all { token ->
            c.name.contains(token, ignoreCase = true) || (c.phone?.contains(token, ignoreCase = true) == true)
        }
        matchesSearch &&
            (typeFilter == "all" || c.customer_type == typeFilter) &&
            (!debtOnly || c.balance > 0)
    }
}

/**
 * Where a party's account stands, as the solde cell reads it: owing from half a centime up, in
 * advance from half a centime down, settled between.
 */
enum class BalanceFilter(val label: String) {
    OWING("Avec dettes"), SETTLED("Soldés"), ADVANCE("En avance");

    fun matches(balance: Double): Boolean = when (this) {
        OWING   -> balance >= 0.005
        ADVANCE -> balance <= -0.005
        SETTLED -> balance > -0.005 && balance < 0.005
    }
}

/**
 * What the Clients list's filter sheet narrows by — the type the old chips offered, the debt they
 * offered, and where the client is. Null means "any"; places compare without case or edge spaces.
 */
data class ClientListFilters(
    val type    : String?        = null,   // customer_type: "retail" | "wholesale" | "business"
    val wilaya  : String?        = null,
    val commune : String?        = null,
    val secteur : String?        = null,
    val balance : BalanceFilter? = null,
) {
    val isActive: Boolean get() = type != null || wilaya != null || commune != null || secteur != null || balance != null
}

/** [filterClients]' search, then the sheet's criteria. */
fun filterClients(clients: List<Client>, query: String, filters: ClientListFilters): List<Client> =
    filterClients(clients, query).filter { c ->
        (filters.type == null || c.customer_type == filters.type) &&
            samePlace(filters.wilaya, c.wilaya_name) &&
            samePlace(filters.commune, c.commune_name) &&
            samePlace(filters.secteur, c.secteur_name) &&
            (filters.balance?.matches(c.balance) ?: true)
    }

/** What the Fournisseurs list's filter sheet narrows by: where the supplier is, and its solde. */
data class SupplierListFilters(
    val wilaya  : String?        = null,
    val commune : String?        = null,
    val balance : BalanceFilter? = null,
) {
    val isActive: Boolean get() = wilaya != null || commune != null || balance != null
}

/** [filterSuppliers]' search, then the sheet's criteria. */
fun filterSuppliers(suppliers: List<Supplier>, query: String, filters: SupplierListFilters): List<Supplier> =
    filterSuppliers(suppliers, query).filter { s ->
        samePlace(filters.wilaya, s.wilaya_name) &&
            samePlace(filters.commune, s.commune_name) &&
            (filters.balance?.matches(s.balance) ?: true)
    }

/** The distinct, non-blank places a list holds, sorted — the sheet offers only what exists. */
fun placesOf(values: List<String?>): List<String> =
    values.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }

private fun samePlace(wanted: String?, actual: String?): Boolean =
    wanted == null || actual?.trim().equals(wanted.trim(), ignoreCase = true)

/** The clients who currently owe something. */
fun clientsInDebt(clients: List<Client>): List<Client> = clients.filter { it.balance > 0 }

// ── Suppliers ──

/** Suppliers found by name or phone. */
fun filterSuppliers(suppliers: List<Supplier>, query: String): List<Supplier> {
    val tokens = searchTokens(query)
    return suppliers.filter { supplier ->
        tokens.isEmpty() || tokens.all { token ->
            supplier.name.contains(token, ignoreCase = true) || (supplier.phone?.contains(token, ignoreCase = true) == true)
        }
    }
}

/** What is owed to suppliers in total: the sum of every positive balance. */
fun supplierDebtTotal(suppliers: List<Supplier>): Double =
    suppliers.filter { it.balance > 0 }.sumOf { it.balance }

// ── Dépôt Vente ──

/** The Dépôt Vente list's filter sheet. Null means "any". */
data class VenteListFilters(
    val status        : String? = null,
    val paymentStatus : String? = null,
    val clientId      : Int?    = null,
    val dateFrom      : String? = null,
    val dateTo        : String? = null
)

/** The sales made at the depot, as opposed to from the van. */
fun depotVentesOf(ventes: List<Vente>): List<Vente> = ventes.filter { it.source == "depot" }

/** Each client that appears in [ventes], once, sorted by name: the client filter's choices. */
fun clientsOfVentes(ventes: List<Vente>): List<Pair<Int, String>> =
    ventes
        .map { it.client_id to it.client_name }
        .distinctBy { it.first }
        .sortedBy { it.second }

/** [ventes] matching the search — client name or sale number — and every filter in [filters]. */
fun filterVentes(
    ventes: List<Vente>, query: String, filters: VenteListFilters,
    zone: ZoneId = ZoneId.systemDefault()
): List<Vente> {
    // A blank search matches everything without being tokenised, exactly as the inline code did.
    val searchBlank = query.isBlank()
    val tokens      = if (searchBlank) emptyList() else searchTokens(query)
    val dateFrom    = filters.dateFrom
    val dateTo      = filters.dateTo
    return ventes.filter { vente ->
        val matchSearch = searchBlank || tokens.all { token ->
            vente.client_name.contains(token, ignoreCase = true) ||
                vente.id.toString().contains(token) ||
                vente.numero?.contains(token, ignoreCase = true) == true
        }

        val matchStatus = filters.status == null || vente.status == filters.status

        val matchPayment = when (filters.paymentStatus) {
            "paye"    -> (vente.montant_paye ?: 0.0) >= vente.total && vente.total > 0
            "impaye"  -> (vente.montant_paye ?: 0.0) <= 0.0
            "partiel" -> (vente.montant_paye ?: 0.0) > 0.0 && (vente.montant_paye ?: 0.0) < vente.total
            else      -> true
        }

        val matchClient = filters.clientId == null || vente.client_id == filters.clientId

        val venteDate     = BusinessDates.localDay(vente.created_at, zone)
        val matchDateFrom = dateFrom == null || venteDate >= dateFrom
        val matchDateTo   = dateTo   == null || venteDate <= dateTo

        matchSearch && matchStatus && matchPayment && matchClient && matchDateFrom && matchDateTo
    }
}

/** [ventes] grouped by day, the days in the order they first appear. */
fun groupVentesByDay(ventes: List<Vente>, zone: ZoneId = ZoneId.systemDefault()): Map<String, List<Vente>> =
    ventes.groupBy { vente -> BusinessDates.localDay(vente.created_at, zone) }

// ── Achats ──

/** The Achats list's filter sheet. Null means "any". */
data class OrderListFilters(
    val receptionStatus : String? = null,
    val paymentStatus   : String? = null,
    val supplierId      : Int?    = null,
    val dateFrom        : String? = null,
    val dateTo          : String? = null
)

/** Each supplier that appears in [orders], once, sorted by name: the supplier filter's choices. */
fun suppliersOfOrders(orders: List<PurchaseOrder>): List<Pair<Int, String>> =
    orders.map { it.supplier_id to it.supplier_name }
        .distinctBy { it.first }
        .sortedBy { it.second }

/** [orders] matching the search — supplier name or bon number — and every filter in [filters]. */
fun filterOrders(
    orders: List<PurchaseOrder>, query: String, filters: OrderListFilters,
    zone: ZoneId = ZoneId.systemDefault()
): List<PurchaseOrder> {
    // A blank search matches everything without being tokenised, exactly as the inline code did.
    val searchBlank = query.isBlank()
    val tokens      = if (searchBlank) emptyList() else searchTokens(query)
    val dateFrom    = filters.dateFrom
    val dateTo      = filters.dateTo
    return orders.filter { order ->
        val matchSearch = searchBlank || tokens.all { token ->
            order.supplier_name.contains(token, ignoreCase = true) ||
                order.id.toString().contains(token) ||
                order.numero?.contains(token, ignoreCase = true) == true
        }

        val matchReception = filters.receptionStatus == null ||
            order.status == filters.receptionStatus

        val matchPayment = when (filters.paymentStatus) {
            "paye"    -> (order.montant_paye ?: 0.0) >= order.total && order.total > 0
            "impaye"  -> (order.montant_paye ?: 0.0) <= 0.0
            "partiel" -> (order.montant_paye ?: 0.0) > 0.0 && (order.montant_paye ?: 0.0) < order.total
            else      -> true
        }

        val matchSupplier = filters.supplierId == null ||
            order.supplier_id == filters.supplierId

        val orderDate     = BusinessDates.localDay(order.created_at ?: order.date, zone)
        val matchDateFrom = dateFrom == null || orderDate >= dateFrom
        val matchDateTo   = dateTo   == null || orderDate <= dateTo

        matchSearch && matchReception && matchPayment && matchSupplier && matchDateFrom && matchDateTo
    }
}

/** [orders] grouped by day, the days in the order they first appear. */
fun groupOrdersByDay(orders: List<PurchaseOrder>, zone: ZoneId = ZoneId.systemDefault()): Map<String, List<PurchaseOrder>> =
    orders.groupBy { order ->
        BusinessDates.localDay(order.created_at ?: order.date, zone)
    }
