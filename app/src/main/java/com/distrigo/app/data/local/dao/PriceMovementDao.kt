package com.distrigo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.distrigo.app.data.model.NUMBER_LABEL_SQL

/** One document line naming a product, as the price history reads it. */
data class PriceMovementRow(
    val kind: String,
    val document_id: Int,
    val document_label: String,
    val party: String?,
    val dated_at: String,
    val unit_price: Double,
    val quantity: Double,
)

/**
 * A product's prices, from the documents they were agreed in — see PriceMovement.
 *
 * Both halves are read in one query rather than two, so the merge and the ordering are the
 * database's. `index_purchase_order_items_product_id` and `index_vente_items_product_id` (migration
 * 53 -> 54) are what keep each half proportional to the product's own lines.
 */
@Dao
interface PriceMovementDao {

    @Query(
        "SELECT 'achat' AS kind, o.id AS document_id, " +
            "(SELECT $NUMBER_LABEL_SQL FROM purchase_orders WHERE purchase_orders.id = o.id) AS document_label, " +
            "o.supplier_name AS party, " +
            // A bon created before instants were recorded is dated by its calendar date, as the export dates it.
            "CASE WHEN COALESCE(o.created_at, '') GLOB '????-??-??T*' THEN o.created_at ELSE o.date END AS dated_at, " +
            "i.unit_cost AS unit_price, i.quantity AS quantity " +
            "FROM purchase_order_items i JOIN purchase_orders o ON o.id = i.purchase_order_id " +
            "WHERE i.product_id = :productId " +
            "UNION ALL " +
            "SELECT 'vente' AS kind, v.id AS document_id, " +
            "(SELECT $NUMBER_LABEL_SQL FROM ventes WHERE ventes.id = v.id) AS document_label, " +
            "COALESCE(v.client_name, c.name) AS party, " +
            "v.created_at AS dated_at, it.unit_price AS unit_price, it.quantity AS quantity " +
            "FROM vente_items it JOIN ventes v ON v.id = it.vente_id " +
            "LEFT JOIN clients c ON c.id = v.client_id " +
            "WHERE it.product_id = :productId " +
            "ORDER BY dated_at DESC, document_id DESC"
    )
    suspend fun forProduct(productId: Int): List<PriceMovementRow>
}
