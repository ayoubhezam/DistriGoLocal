package com.distrigo.app.data.repository

import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.VenteEntity
import com.distrigo.app.data.local.entity.VenteItemEntity

/**
 * A sale as the device tests record it for a client return to be made from (FIN-02): its id and its lines' ids, in
 * the order given.
 */
internal class RecordedSale(val venteId: Int, val itemIds: List<Int>, private val productIds: List<Int>) {
    /** The return item for [quantity] of line [index] of this sale, with [motif] when given. */
    fun returning(index: Int, quantity: Double, motif: String? = null): Map<String, Any?> =
        mapOf("vente_item_id" to itemIds[index], "product_id" to productIds[index], "quantity" to quantity, "motif" to motif)
}

/**
 * Records a sale to [clientId] from [source] of [lines] (product, quantity), each at [unitPrice] with [cost] as its
 * purchase price snapshot — as rows only: no stock moves, so a test's stock figures are its returns' alone.
 */
internal suspend fun AppDatabase.recordedSale(
    clientId : Int,
    source   : String,
    vararg lines: Pair<Int, Double>,
    unitPrice: Double = 110.0,
    cost     : Double = 95.0,
): RecordedSale {
    val venteId = venteDao().insertVente(
        VenteEntity(
            client_id = clientId, tournee_id = null, source = source, total = lines.sumOf { it.second } * unitPrice,
            montant_paye = 0.0, status = "pending", note = null, created_at = java.time.Instant.now().toString()
        )
    ).toInt()
    venteDao().insertItems(lines.map { (productId, quantity) ->
        val product = productDao().getProductById(productId)!!
        VenteItemEntity(
            vente_id = venteId, product_id = productId, product_name = product.name, unit_type = product.unit_type,
            quantity = quantity, unit_price = unitPrice, total_price = quantity * unitPrice, purchase_price_snapshot = cost
        )
    })
    val itemIds = retourClientDao().getSaleLinesWithReturned(venteId).map { it.vente_item_id }
    return RecordedSale(venteId, itemIds, lines.map { it.first })
}
