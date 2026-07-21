package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_items")
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val session_id: Int,
    val product_id: Int,
    val product_name: String,           // denormalized
    val product_image_uri: String?,     // denormalized
    val qte_systeme: Double,
    val qte_physique: Double,
    val ecart: Double,
    val purchase_price_snapshot: Double,
    val valeur_ecart: Double,           // ecart × purchase_price_snapshot (signé)
    val created_at: String
)