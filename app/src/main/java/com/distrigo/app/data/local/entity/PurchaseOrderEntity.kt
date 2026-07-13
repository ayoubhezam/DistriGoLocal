package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "purchase_orders")
data class PurchaseOrderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val supplier_id: Int,
    val date: String,
    val total: Double,
    val status: String,              // "pending" | "received"
    val note: String?,
    val montant_paye: Double,
    val created_at: String
)