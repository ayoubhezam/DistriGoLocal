package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tournees",
    indices = [Index(value = ["status"]), Index(value = ["uuid"], unique = true)]
)
data class TourneeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val status: String,
    val date_debut: String?,
    val date_fin: String?,
    val note: String?,
    val nom: String,
    val wilaya_name: String?,
    val commune_name: String?,
    val created_at: String,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis()
)