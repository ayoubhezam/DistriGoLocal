package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clients", indices = [Index(value = ["uuid"], unique = true)])
data class ClientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val phone: String?,
    val wilaya_name: String?,
    val commune_name: String?,
    val secteur_id: Int?,
    val secteur_name: String?,
    val address: String?,
    val note: String?,
    val balance: Double = 0.0,
    val customer_type: String = "retail",
    val image_uri: String?,
    val latitude: Double?,
    val longitude: Double?,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "''")
    val created_at: String = java.time.Instant.now().toString(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis(),
    /** Null while the row is live; when it was deleted otherwise. Deleted rows are filtered out of every read. */
    val deleted_at: Long? = null
)