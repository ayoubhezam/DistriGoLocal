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
    val qte_systeme: Int,               // snapshot de product.stock au moment du scan
    val qte_physique: Int,              // saisi par l'utilisateur
    val ecart: Int,                     // qte_physique - qte_systeme (signé : + surplus, - manque)
    val purchase_price_snapshot: Double,
    val valeur_ecart: Double,           // ecart × purchase_price_snapshot (signé)
    val created_at: String
)