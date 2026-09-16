package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "client_payments",
    indices = [
        Index(value = ["client_id", "created_at"]),
        Index(value = ["created_at"]),
        Index(value = ["uuid"], unique = true),
    ]
)
data class ClientPaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val client_id: Int,
    val amount: Double,
    val note: String?,
    val created_at: String,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid()
)