package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sous_categories", indices = [Index(value = ["uuid"], unique = true)])
data class SousCategorieEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val category_id: Int,
    val name: String,
    val sort_order: Int,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid()
)