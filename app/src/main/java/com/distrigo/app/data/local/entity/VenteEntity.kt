package com.distrigo.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ventes",
    indices = [
        Index(value = ["client_id", "created_at"]),
        Index(value = ["tournee_id"]),
        Index(value = ["created_at"]),
        Index(value = ["source", "created_at"]),
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
    val user_name: String? = null
)