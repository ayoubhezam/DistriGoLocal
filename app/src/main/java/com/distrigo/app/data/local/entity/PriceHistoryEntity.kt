package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "price_history",
    indices = [Index(value = ["product_id", "created_at"]), Index(value = ["uuid"], unique = true)]
)
data class PriceHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val product_id: Int,
    val unit_cost: Double,
    val date: String,
    val created_at: String,
    val supplier_name: String,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis()
)