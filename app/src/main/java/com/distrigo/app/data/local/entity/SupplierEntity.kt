package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val phone: String?,
    val address: String?,
    val note: String?,
    val balance: Double,
    val initial_balance: Double = 0.0,
    val latitude: Double?,
    val longitude: Double?,
    val wilaya_name: String?,
    val commune_name: String?,
    val created_at: String = java.time.Instant.now().toString()   // جديد

)
