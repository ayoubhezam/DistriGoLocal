package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "price_history",
    indices = [Index(value = ["product_id", "created_at"]), Index(value = ["uuid"], unique = true), Index(value = ["purchase_order_id"])]
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
    val updated_at: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1")
    val version: Int = 1,
    /** The device that created the row; null for rows created before version 48. */
    val origin_device_id: String? = null,
    /**
     * The bon this price was paid on (DB-01, v69): deleted with it, replaced when it is edited, so a deleted bon no
     * longer shows in the purchase form's price suggestions.
     */
    val purchase_order_id: Int? = null
)