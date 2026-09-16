package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_items",
    indices = [
        Index(value = ["session_id", "created_at"]),
        Index(value = ["session_id", "product_id"]),
        Index(value = ["uuid"], unique = true),
    ]
)
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
    val created_at: String,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid()
)