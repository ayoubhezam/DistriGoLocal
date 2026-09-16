package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "retour_client",
    indices = [Index(value = ["client_id", "date"]), Index(value = ["uuid"], unique = true)]
)
data class RetourClientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val client_id: Int,
    val tournee_id: Int?,       // nullable — same convention as VenteEntity.tournee_id
    val date: String,
    val motif: String?,
    val note: String?,
    val total: Double,
    val created_at: String,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis()
)