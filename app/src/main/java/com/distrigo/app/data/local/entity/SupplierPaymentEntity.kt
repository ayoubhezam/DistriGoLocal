package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "supplier_payments",
    indices = [Index(value = ["supplier_id", "created_at"])]
)
data class SupplierPaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val supplier_id: Int,
    val amount: Double,
    val note: String?,
    val created_at: String
)