package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val phone: String?,
    val wilaya_name: String?,
    val commune_name: String?,
    val address: String?,
    val note: String?,
    val balance: Double = 0.0,
    val customer_type: String = "retail",
    val image_uri: String?,
    val latitude: Double?,
    val longitude: Double?
)