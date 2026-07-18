package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charge_subtypes")
data class ChargeSubTypeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type_id: Int,
    val name: String,
    val icon: String,
    val has_fournisseur: Boolean = false,   // يتحكم بظهور حقل Fournisseur/Station في الفورم
    val is_default: Boolean = false,
    val created_at: String
)