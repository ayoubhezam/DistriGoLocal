package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tournee_clients",
    indices = [Index(value = ["tournee_id", "order_index"]), Index(value = ["uuid"], unique = true)]
)
data class TourneeClientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val tournee_id: Int,
    val client_id: Int,
    val status: String,        // "a_visiter" | "en_cours" | "visite"
    val order_index: Int = 0,
    val visited_at: String?,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid()
)