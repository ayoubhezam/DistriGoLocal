package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "perte_types", indices = [Index(value = ["uuid"], unique = true)])
data class PerteTypeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val icon: String,
    val color_hex: String,
    val is_default: Boolean = false,
    val created_at: String,
    val description: String? = null,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1")
    val version: Int = 1,
    /** Null while the row is live; when it was deleted otherwise. Deleted rows are filtered out of every read. */
    val deleted_at: Long? = null
)