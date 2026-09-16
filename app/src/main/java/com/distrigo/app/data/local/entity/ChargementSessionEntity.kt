package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chargement_sessions",
    indices = [Index(value = ["session_date"]), Index(value = ["uuid"], unique = true)]
)
data class ChargementSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val session_date: String,   // "yyyy-MM-dd"
    val note: String?,
    val created_at: String,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1")
    val version: Int = 1
)