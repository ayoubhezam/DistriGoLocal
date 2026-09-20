package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchase_order_items",
    // `product_id` is indexed for the price history, which asks this table for one product's
    // purchase prices across every bon.
    indices = [Index(value = ["purchase_order_id"]), Index(value = ["product_id"]), Index(value = ["uuid"], unique = true)]
)
data class PurchaseOrderItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val purchase_order_id: Int,
    val product_id: Int,
    val quantity: Double,
    val unit_cost: Double,
    val total_cost: Double,
    val product_name: String,
    val unit_type: String,
    val nb_colis: Double = 1.0,
    val unite_par_colis: Int = 1,
    val has_expiry: Boolean = false,
    val expiry_date: String? = null,   // "yyyy-MM-dd"
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "''")
    val created_at: String = java.time.Instant.now().toString(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis()
)