package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chargement_sessions",
    indices = [Index(value = ["session_date"])]
)
data class ChargementSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val session_date: String,   // "yyyy-MM-dd"
    val note: String?,
    val created_at: String
)