package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "secteurs", indices = [Index(value = ["uuid"], unique = true)])
data class SecteurEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val nom: String,
    val commune_name: String,
    val wilaya_name: String?,
    val created_at: String,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis()
)