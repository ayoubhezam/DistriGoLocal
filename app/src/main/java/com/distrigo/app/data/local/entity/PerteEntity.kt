package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pertes")
data class PerteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type_id: Int,
    val type_name: String,               // denormalized
    val product_id: Int,
    val product_name: String,            // denormalized
    val product_image_uri: String?,      // denormalized
    val quantity: Int,
    val unit: String,
    val source: String,                  // "depot" | "camion"
    val purchase_price_snapshot: Double, // سعر الشراء وقت التسجيل (لا يتأثر لو تغيّر السعر لاحقاً)
    val valeur_totale: Double,           // purchase_price_snapshot × quantity
    val date_time: String,               // ISO-8601
    val motif: String?,
    val photo_path: String?,             // مسار محلي في filesDir
    val created_at: String
)