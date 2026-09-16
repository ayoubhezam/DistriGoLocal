package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pertes",
    indices = [
        Index(value = ["type_id", "date_time"]),
        Index(value = ["source_type", "source_id"]),
        Index(value = ["uuid"], unique = true),
    ]
)
data class PerteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type_id: Int,
    val type_name: String,               // denormalized
    val product_id: Int,
    val product_name: String,            // denormalized
    val product_image_uri: String?,      // denormalized
    val quantity: Double,
    val unit: String,
    val source: String,                  // "depot" | "camion"
    val purchase_price_snapshot: Double, // سعر الشراء وقت التسجيل (لا يتأثر لو تغيّر السعر لاحقاً)
    val valeur_totale: Double,           // purchase_price_snapshot × quantity
    val date_time: String,               // ISO-8601
    val motif: String?,
    val photo_path: String?,             // مسار محلي في filesDir
    val created_at: String,
    val source_type: String? = null,
    val source_id: Int? = null,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1")
    val version: Int = 1,
    /** The device that created the row; null for rows created before version 48. */
    val origin_device_id: String? = null
)