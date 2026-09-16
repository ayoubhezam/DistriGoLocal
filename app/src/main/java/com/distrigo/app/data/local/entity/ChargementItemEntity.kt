package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chargement_items",
    indices = [Index(value = ["chargement_id"]), Index(value = ["uuid"], unique = true)]
)
data class ChargementItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val chargement_id: Int,
    val product_id: Int,
    val quantity: Double,
    val direction: String,      // "vers_camion" | "vers_depot"
    // منسوخة وقت الإدخال (denormalized) بنفس منطقك في ProductEntity مع category_name/supplier_name
    val product_name: String,
    val unit_type: String,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "''")
    val created_at: String = java.time.Instant.now().toString(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis()
)