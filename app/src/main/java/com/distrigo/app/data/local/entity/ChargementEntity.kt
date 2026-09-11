package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chargements",
    indices = [Index(value = ["session_id"])]
)
data class ChargementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val session_id: Int,
    val note: String?,
    val created_at: String
)