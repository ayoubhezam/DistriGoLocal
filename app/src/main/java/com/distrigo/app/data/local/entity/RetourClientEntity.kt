package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "retour_client")
data class RetourClientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val client_id: Int,
    val tournee_id: Int?,       // nullable — same convention as VenteEntity.tournee_id
    val date: String,
    val motif: String?,
    val note: String?,
    val total: Double,
    val created_at: String
)