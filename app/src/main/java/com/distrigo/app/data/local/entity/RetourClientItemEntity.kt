package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "retour_client_items",
    indices = [Index(value = ["retour_id"]), Index(value = ["uuid"], unique = true)]
)
data class RetourClientItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val retour_id: Int,
    val product_id: Int,
    val product_name: String,
    val unit_type: String,
    val quantity: Double,
    val unit_price: Double,     // selling_price snapshot
    val total_price: Double,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid()
)