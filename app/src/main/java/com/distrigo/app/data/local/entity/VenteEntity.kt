package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ventes")
data class VenteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val client_id: Int,
    val tournee_id: Int?,
    val source: String,
    val total: Double,
    val montant_paye: Double,
    val status: String,
    val note: String?,
    val created_at: String,
    val client_name: String? = null,
    val client_image_uri: String? = null    // ← جديد
)