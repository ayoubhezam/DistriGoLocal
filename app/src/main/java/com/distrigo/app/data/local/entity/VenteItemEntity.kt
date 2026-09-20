package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vente_items",
    // `product_id` is indexed for the price history, which asks this table for one product's sale
    // prices across every vente — a filter no other index covers.
    indices = [Index(value = ["vente_id"]), Index(value = ["product_id"]), Index(value = ["uuid"], unique = true)]
)
data class VenteItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val vente_id: Int,
    val product_id: Int,
    val product_name: String,
    val unit_type: String,
    val quantity: Double,
    val unit_price: Double,
    val total_price: Double,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "''")
    val created_at: String = java.time.Instant.now().toString(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis()
)