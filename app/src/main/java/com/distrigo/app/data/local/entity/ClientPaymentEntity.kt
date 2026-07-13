package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "client_payments")
data class ClientPaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val client_id: Int,
    val amount: Double,
    val note: String?,
    val created_at: String
)