package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "charges",
    indices = [
        Index(value = ["subtype_id", "date_time"]),
        // The month totals group by type_id, so they read a range of this one instead of the table.
        Index(value = ["type_id", "date_time"]),
        Index(value = ["uuid"], unique = true),
    ]
)
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
    val created_at: String,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1")
    val version: Int = 1,
    /** The device that created the row; null for rows created before version 48. */
    val origin_device_id: String? = null
)