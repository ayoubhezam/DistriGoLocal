package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "retour_fournisseur_items")
data class RetourFournisseurItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val retour_id: Int,
    val product_id: Int,
    val product_name: String,
    val unit_type: String,
    val quantity: Double,
    val unit_price: Double,     // purchase_price snapshot
    val total_price: Double
)