package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchase_order_items",
    indices = [Index(value = ["purchase_order_id"]), Index(value = ["uuid"], unique = true)]
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
    val uuid: String = newRowUuid()
)