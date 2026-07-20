package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "purchase_order_items")
data class PurchaseOrderItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val purchase_order_id: Int,
    val product_id: Int,
    val quantity: Int,
    val unit_cost: Double,
    val total_cost: Double,
    val product_name: String,
    val unit_type: String,
    val nb_colis: Int = 1,
    val unite_par_colis: Int = 1,
    val has_expiry: Boolean = false,
    val expiry_date: String? = null   // "yyyy-MM-dd"
)