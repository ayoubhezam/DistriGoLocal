package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_sessions",
    indices = [Index(value = ["status"]), Index(value = ["uuid"], unique = true)]
)
data class InventorySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val status: String,          // "draft" | "completed"
    val started_at: String,      // ISO-8601
    val completed_at: String?,    // null tant que "draft"
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "''")
    val created_at: String = java.time.Instant.now().toString(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1")
    val version: Int = 1,
    /** The device that created the row; null for rows created before version 48. */
    val origin_device_id: String? = null
)