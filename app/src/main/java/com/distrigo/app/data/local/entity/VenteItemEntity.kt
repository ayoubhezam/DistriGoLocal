package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vente_items")
data class VenteItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val vente_id: Int,
    val product_id: Int,
    val product_name: String,
    val unit_type: String,
    val quantity: Double,
    val unit_price: Double,
    val total_price: Double
)