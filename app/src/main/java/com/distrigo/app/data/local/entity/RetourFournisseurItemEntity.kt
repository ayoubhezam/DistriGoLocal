package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "retour_fournisseur_items",
    // product_id (DB-03, v68): what was sent back of a product, for the returnable quantities and the Corbeille
    indices = [Index(value = ["retour_id"]), Index(value = ["uuid"], unique = true), Index(value = ["product_id"])]
)
data class RetourFournisseurItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val retour_id: Int,
    val product_id: Int,
    val product_name: String,
    val unit_type: String,
    val quantity: Double,
    val unit_price: Double,     // purchase_price snapshot
    val total_price: Double,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "''")
    val created_at: String = java.time.Instant.now().toString(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis(),
    /** Why this line came back — its own, since v61: it decides the line's stock effect and linked perte. */
    val motif: String? = null
)