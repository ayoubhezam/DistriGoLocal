package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tournee_clients")
data class TourneeClientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val tournee_id: Int,
    val client_id: Int,
    val status: String,        // "a_visiter" | "en_cours" | "visite"
    val order_index: Int = 0,
    val visited_at: String?
)