package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchase_orders",
    indices = [Index(value = ["supplier_id", "created_at"]), Index(value = ["uuid"], unique = true)]
)
data class PurchaseOrderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val supplier_id: Int,
    val date: String,
    val total: Double,
    val status: String,
    val note: String?,
    val montant_paye: Double,
    val created_at: String,
    val supplier_name: String? = null,
    val supplier_image_uri: String? = null,
    @ColumnInfo(defaultValue = "''")
    val uuid: String = newRowUuid()
)