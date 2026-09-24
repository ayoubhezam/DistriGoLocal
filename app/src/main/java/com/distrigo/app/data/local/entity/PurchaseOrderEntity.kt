package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "purchase_orders",
    indices = [
        Index(value = ["supplier_id", "created_at"]),
        Index(value = ["uuid"], unique = true),
        // The Achats list reads newest first by created_at, a page at a time; see PurchaseOrderListSql.
        Index(value = ["created_at"]),
    ]
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
    val uuid: String = newRowUuid(),
    @ColumnInfo(defaultValue = "0")
    val updated_at: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "1")
    val version: Int = 1,
    /** The device that created the row; null for rows created before version 48. */
    val origin_device_id: String? = null,
    /** The number printed on the document — `V-6DED-000124`, or its old id for one created before version 49. See DocumentNumberTriggers. */
    val numero: String? = null
)