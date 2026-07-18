package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "inventory_sessions")
data class InventorySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val status: String,          // "draft" | "completed"
    val started_at: String,      // ISO-8601
    val completed_at: String?    // null tant que "draft"
)