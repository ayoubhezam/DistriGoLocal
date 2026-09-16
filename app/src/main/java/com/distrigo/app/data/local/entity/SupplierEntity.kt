package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suppliers", indices = [Index(value = ["uuid"], unique = true)])
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val phone: String?,
    val address: String?,
    val note: String?,
    val balance: Double,
    val initial_balance: Double = 0.0,
    val latitude: Double?,
    val longitude: Double?,
    val wilaya_name: String?,
    val commune_name: String?,
    val created_at: String = java.time.Instant.now().toString(),   // جديد
    val image_uri: String? = null,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1")
    val version: Int = 1,
    /** The device that created the row; null for rows created before version 48. */
    val origin_device_id: String? = null,
    /** Null while the row is live; when it was deleted otherwise. Deleted rows are filtered out of every read. */
    val deleted_at: Long? = null
)
