package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charges")
data class ChargeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type_id: Int,
    val type_name: String,      // denormalized، بنفس نمط VenteItemEntity.product_name
    val subtype_id: Int,
    val subtype_name: String,
    val montant: Double,
    val date_time: String,      // ISO-8601، نفس نمط created_at (يُستعمل مع formatOrderDate/formatOrderTime)
    val fournisseur: String?,   // null إذا subtype.has_fournisseur == false
    val note: String?,
    val created_at: String
)