package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A quartier: a part of one secteur, where clients are (owner, 2026-10-24). Its secteur is fixed once created.
 * Like a secteur, it has no `deleted_at`: it is deleted for good, and only while no client is in it.
 */
@Entity(
    tableName = "quartiers",
    indices = [Index(value = ["secteur_id"]), Index(value = ["uuid"], unique = true)]
)
data class QuartierEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val nom: String,
    val secteur_id: Int,
    @ColumnInfo(defaultValue = "''")
    val created_at: String = java.time.Instant.now().toString(),
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1")
    val version: Int = 1,
    val origin_device_id: String? = null
)
