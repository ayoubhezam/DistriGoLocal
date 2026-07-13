package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "supplier_payments")
data class SupplierPaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val supplier_id: Int,
    val amount: Double,
    val note: String?,
    val created_at: String
)