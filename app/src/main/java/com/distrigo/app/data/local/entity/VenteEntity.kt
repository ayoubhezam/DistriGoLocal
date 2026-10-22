package com.distrigo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ventes",
    indices = [
        // Covering for the client debt by age (v63): with total and montant_paye in the index, the Dashboard's
        // « Plus de 90 jours » reads the index alone — 630 ms down to 58 ms on 120 000 sales. Its first columns
        // still serve every lookup by client and date.
        Index(value = ["client_id", "created_at", "total", "montant_paye"]),
        Index(value = ["tournee_id"]),
        Index(value = ["created_at"]),
        Index(value = ["source", "created_at"]),
        Index(value = ["uuid"], unique = true),
    ]
)
data class VenteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val client_id: Int,
    val tournee_id: Int?,
    val source: String,
    val total: Double,
    val montant_paye: Double,
    val status: String,
    val note: String?,
    val created_at: String,
    val client_name: String? = null,
    val client_image_uri: String? = null,   // ← جديد
    /**
     * "Effectué par" — who made the sale.
     *
     * Until now this was written only onto the stock movements a sale produces, which meant the
     * vente itself could not answer the question and the receipt had nothing to print. The
     * movements keep their copy: they are the stock ledger and are read on their own terms.
     */
    val user_name: String? = null,
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