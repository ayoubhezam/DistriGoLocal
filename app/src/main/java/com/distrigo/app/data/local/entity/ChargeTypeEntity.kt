package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charge_types", indices = [Index(value = ["uuid"], unique = true)])
data class ChargeTypeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val icon: String,          // مفتاح نصي للأيقونة، يُترجم لاحقاً في UI عبر Mapper
    val color_hex: String,
    val is_default: Boolean = false,
    val created_at: String,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis()
)