package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vente_items",
    // `product_id` is indexed for the price history, which asks this table for one product's sale
    // prices across every vente — a filter no other index covers.
    indices = [Index(value = ["vente_id"]), Index(value = ["product_id"]), Index(value = ["uuid"], unique = true)]
)
data class VenteItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val vente_id: Int,
    val product_id: Int,
    val product_name: String,
    val unit_type: String,
    val quantity: Double,
    val unit_price: Double,
    val total_price: Double,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "''")
    val created_at: String = java.time.Instant.now().toString(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis(),
    /**
     * The product's purchase price when the line was sold — its cost, so a margin stays what it was
     * when the price changes later. The same snapshot a perte keeps (PerteEntity.purchase_price_snapshot).
     *
     * Last, with [cost_estimated], because MIGRATION_59_60 appends them.
     */
    @ColumnInfo(defaultValue = "0")
    val purchase_price_snapshot: Double = 0.0,
    /**
     * True on a line sold before the snapshot existed: MIGRATION_59_60 filled its cost from the
     * product's purchase price on the day of the update, not the day of the sale.
     */
    @ColumnInfo(defaultValue = "0")
    val cost_estimated: Boolean = false
)