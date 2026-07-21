package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chargement_items")
data class ChargementItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val chargement_id: Int,
    val product_id: Int,
    val quantity: Double,
    val direction: String,      // "vers_camion" | "vers_depot"
    // منسوخة وقت الإدخال (denormalized) بنفس منطقك في ProductEntity مع category_name/supplier_name
    val product_name: String,
    val unit_type: String
)