package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "retour_client_items",
    // vente_item_id: what was already returned of a sale line, read for every line of the sale a return is made from.
    // product_id (DB-03, v68): a product's returns, for the Corbeille and the duplicate checks
    indices = [Index(value = ["retour_id"]), Index(value = ["uuid"], unique = true), Index(value = ["vente_item_id"]), Index(value = ["product_id"])]
)
data class RetourClientItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val retour_id: Int,
    val product_id: Int,
    val product_name: String,
    val unit_type: String,
    val quantity: Double,
    val unit_price: Double,     // selling_price snapshot
    val total_price: Double,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "''")
    val created_at: String = java.time.Instant.now().toString(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis(),
    /** Why this line came back — its own, since v61: it decides the line's stock effect and linked perte. */
    val motif: String? = null,
    /** The sale line it came back from (FIN-02, v65): its price and cost are this line's. Null before v65. */
    val vente_item_id: Int? = null,
    /**
     * The goods' cost, the sale line's purchase price snapshot (FIN-02, v65), so the Résultat takes back what they cost
     * when sold. Null before v65: the report falls back to today's purchase price.
     */
    val purchase_price_snapshot: Double? = null
)