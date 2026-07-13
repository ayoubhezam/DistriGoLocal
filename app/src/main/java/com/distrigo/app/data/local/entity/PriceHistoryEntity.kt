package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_history")
data class PriceHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val product_id: Int,
    val unit_cost: Double,
    val date: String,
    val created_at: String,
    val supplier_name: String
)