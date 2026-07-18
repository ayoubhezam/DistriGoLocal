package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "perte_types")
data class PerteTypeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val icon: String,
    val color_hex: String,
    val is_default: Boolean = false,
    val created_at: String,
    val description: String? = null
)