package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charge_subtypes", indices = [Index(value = ["uuid"], unique = true)])
data class ChargeSubTypeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type_id: Int,
    val name: String,
    val icon: String,
    val has_fournisseur: Boolean = false,   // يتحكم بظهور حقل Fournisseur/Station في الفورم
    val is_default: Boolean = false,
    val created_at: String,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis(),
    /** Null while the row is live; when it was deleted otherwise. Deleted rows are filtered out of every read. */
    val deleted_at: Long? = null
)