package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "retour_fournisseur")
data class RetourFournisseurEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val supplier_id: Int,
    val date: String,          // "yyyy-MM-dd"
    val motif: String?,
    val note: String?,
    val total: Double,
    val created_at: String
)