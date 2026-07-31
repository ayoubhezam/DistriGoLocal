package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sous_categories")
data class SousCategorieEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val category_id: Int,
    val name: String,
    val sort_order: Int
)