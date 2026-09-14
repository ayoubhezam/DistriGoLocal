package com.distrigo.app.ui.common

import com.distrigo.app.data.model.Client
import com.distrigo.app.data.model.Product
import com.distrigo.app.data.model.PurchaseOrder
import com.distrigo.app.data.model.Supplier
import com.distrigo.app.data.model.Vente

/**
 * Verbatim copies of the inline list code C6 replaced, kept only to prove the replacement returns
 * the same thing.
 *
 * Each body is the original with its ViewModel fields or remembered state turned into parameters of
 * the same name. Nothing else is changed — not the regex built inside the per-row predicate, not the
 * `run { }`, not the formatting — because the point is to compare against what the screens actually
 * ran.
 */
internal object LegacyListFilters {

    // ── ClientsScreen.kt ──
    fun clientsScreen(clients: List<Client>, search: String, typeFilter: String, debtOnly: Boolean): List<Client> {
        val filtered = clients.filter { c ->
            val tokens = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
            val matchesSearch = tokens.isEmpty() || tokens.all { token ->
                c.name.contains(token, ignoreCase = true) || (c.phone?.contains(token, ignoreCase = true) == true)
            }
            matchesSearch &&
                    (typeFilter == "all" || c.customer_type == typeFilter) &&
                    (!debtOnly || c.balance > 0)
        }
        return filtered
    }

    fun clientsDebt(clients: List<Client>): Pair<List<Client>, Double> {
        val debtClients = clients.filter { it.balance > 0 }
        val totalDebt   = debtClients.sumOf { it.balance }
        return debtClients to totalDebt
    }

    // ── SuppliersScreen.kt ──
    fun suppliersScreen(suppliers: List<Supplier>, search: String): Pair<List<Supplier>, Double> {
        val filtered  = suppliers.filter { supplier ->
            val tokens = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
            tokens.isEmpty() || tokens.all { token ->
                supplier.name.contains(token, ignoreCase = true) || (supplier.phone?.contains(token, ignoreCase = true) == true)
            }
        }

        val totalDebt = suppliers.filter { it.balance > 0 }.sumOf { it.balance }
        return filtered to totalDebt
    }

    // ── ClientSearchPicker.kt ──
    fun clientPicker(clients: List<Client>, clientSearch: String): List<Client> {
        val filteredClients = clients.filter { client ->
            val tokens = clientSearch.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
            tokens.isEmpty() || tokens.all { token ->
                client.name.contains(token, ignoreCase = true) ||
                        (client.phone?.contains(token, ignoreCase = true) == true)
            }
        }
        return filteredClients
    }

    // ── VenteFormNavGraph.kt, products step ──
    fun venteFormProducts(products: List<Product>, search: String): List<Product> {
        val filteredProducts = products.filter { product ->
            val tokens = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
            tokens.isEmpty() || tokens.all { token ->
                product.name.contains(token, ignoreCase = true) ||
                        (product.barcode?.contains(token, ignoreCase = true) == true)
            }
        }
        return filteredProducts
    }

    // ── InventoryScreen.kt, InventoryProductSearchDialog ──
    fun inventorySearch(products: List<Product>, search: String): List<Product> {
        val tokens = search.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val filtered = products.filter { product ->
            tokens.isEmpty() || tokens.all { token ->
                product.name.contains(token, ignoreCase = true) || (product.barcode?.contains(token, ignoreCase = true) == true)
            }
        }
        return filtered
    }

    // ── VentesScreen.kt ──
    class DepotVente(
        val depotVentes    : List<Vente>,
        val clients        : List<Pair<Int, String>>,
        val filteredVentes : List<Vente>,
        val groupedVentes  : Map<String, List<Vente>>
    )

    fun ventesScreen(
        ventes: List<Vente>, searchQuery: String, filterStatus: String?, filterPaymentStatus: String?,
        filterClientId: Int?, filterDateFrom: String?, filterDateTo: String?
    ): DepotVente {
        val depotVentes = ventes.filter { it.source == "depot" }

        val clients = depotVentes
                .map { it.client_id to it.client_name }
                .distinctBy { it.first }
                .sortedBy { it.second }

        val filteredVentes = depotVentes.filter { vente ->
            val matchSearch = searchQuery.isBlank() || run {
                val tokens = searchQuery.trim()
                    .split("\\s+".toRegex())
                    .filter { it.isNotEmpty() }
                tokens.all { token ->
                    vente.client_name.contains(token, ignoreCase = true) ||
                            vente.id.toString().contains(token)
                }
            }

            val matchStatus = filterStatus == null || vente.status == filterStatus

            val matchPayment = when (filterPaymentStatus) {
                "paye"    -> (vente.montant_paye ?: 0.0) >= vente.total && vente.total > 0
                "impaye"  -> (vente.montant_paye ?: 0.0) <= 0.0
                "partiel" -> (vente.montant_paye ?: 0.0) > 0.0 && (vente.montant_paye ?: 0.0) < vente.total
                else      -> true
            }

            val matchClient = filterClientId == null || vente.client_id == filterClientId

            val venteDate     = vente.created_at?.take(10) ?: ""
            val dateFrom      = filterDateFrom
            val dateTo        = filterDateTo
            val matchDateFrom = dateFrom == null || venteDate >= dateFrom
            val matchDateTo   = dateTo   == null || venteDate <= dateTo

            matchSearch && matchStatus && matchPayment && matchClient && matchDateFrom && matchDateTo
        }

        val groupedVentes = filteredVentes.groupBy { vente -> vente.created_at?.take(10) ?: "" }

        return DepotVente(depotVentes, clients, filteredVentes, groupedVentes)
    }

    // ── PurchasesScreen.kt ──
    class Achats(
        val suppliers      : List<Pair<Int, String>>,
        val filteredOrders : List<PurchaseOrder>,
        val groupedOrders  : Map<String, List<PurchaseOrder>>
    )

    fun purchasesScreen(
        orders: List<PurchaseOrder>, searchQuery: String, filterReceptionStatus: String?,
        filterPaymentStatus: String?, filterSupplierId: Int?, filterDateFrom: String?, filterDateTo: String?
    ): Achats {
        val suppliers = orders.map { it.supplier_id to it.supplier_name }
                .distinctBy { it.first }
                .sortedBy { it.second }

        val filteredOrders = orders.filter { order ->
            val matchSearch = searchQuery.isBlank() || run {
                val tokens = searchQuery.trim()
                    .split("\\s+".toRegex())
                    .filter { it.isNotEmpty() }
                tokens.all { token ->
                    order.supplier_name.contains(token, ignoreCase = true) ||
                            order.id.toString().contains(token)
                }
            }

            val matchReception = filterReceptionStatus == null ||
                    order.status == filterReceptionStatus

            val matchPayment = when (filterPaymentStatus) {
                "paye"    -> (order.montant_paye ?: 0.0) >= order.total && order.total > 0
                "impaye"  -> (order.montant_paye ?: 0.0) <= 0.0
                "partiel" -> (order.montant_paye ?: 0.0) > 0.0 && (order.montant_paye ?: 0.0) < order.total
                else      -> true
            }

            val matchSupplier = filterSupplierId == null ||
                    order.supplier_id == filterSupplierId

            val orderDate     = order.created_at?.take(10) ?: order.date.take(10)
            val dateFrom      = filterDateFrom
            val dateTo        = filterDateTo
            val matchDateFrom = dateFrom == null || orderDate >= dateFrom
            val matchDateTo   = dateTo   == null || orderDate <= dateTo

            matchSearch && matchReception && matchPayment && matchSupplier && matchDateFrom && matchDateTo
        }

        val groupedOrders = filteredOrders.groupBy { order ->
            order.created_at?.take(10) ?: order.date.take(10)
        }

        return Achats(suppliers, filteredOrders, groupedOrders)
    }
}
