package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tournees")
data class TourneeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val status: String,              // "ouverte" | "fermée"
    val date_debut: String?,
    val date_fin: String?,
    val note: String?,
    val nom: String,
    val wilaya_name: String?,
    val commune_name: String?,
    val chauffeur: String?,
    val vehicule: String?,
    val created_at: String
)