package com.distrigo.app.ui.components

import com.distrigo.app.data.model.PurchaseOrder
import com.distrigo.app.data.model.Vente
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ReceiptLineItem(
    val name       : String,
    val quantity   : Double,
    val unitLabel  : String,
    val unitPrice  : Double,
    val totalPrice : Double,
    val nbColis      : Double? = null,
    val unitePerColis: Int? = null
)

data class ReceiptData(
    val documentTitle : String,
    val partyLabel    : String,   // "Client" أو "Fournisseur"
    val partyName     : String,
    val dateLabel      : String,
    val items         : List<ReceiptLineItem>,
    val total         : Double,
    val paid          : Double,
    val note          : String? = null,
    val businessName     : String = "DISTRIGO",
    val businessLogoPath : String? = null
) {
    val balance: Double get() = total - paid
    val amountInWords: String get() = numberToFrenchWords(total)
    val qrContent: String get() = "$documentTitle | $dateLabel | ${"%.2f".format(total)} DA"
}

private fun formatReceiptDate(createdAt: String?): String {
    return try {
        if (createdAt.isNullOrEmpty()) return ""
        val instant = Instant.parse(createdAt)
        val zoneId  = ZoneId.of("Africa/Algiers")
        instant.atZone(zoneId).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH))
    } catch (e: Exception) { createdAt ?: "" }
}

fun Vente.toReceiptData(context: android.content.Context): ReceiptData = ReceiptData(
    documentTitle = "Vente #$id",
    partyLabel    = "Client",
    partyName     = client_name,
    dateLabel     = formatReceiptDate(created_at),
    items = (items ?: emptyList()).map {
        ReceiptLineItem(
            name = it.product_name, quantity = it.quantity, unitLabel = it.unit_type,
            unitPrice = it.unit_price, totalPrice = it.total_price
        )
    },
    total = total,
    paid  = montant_paye ?: 0.0,
    note  = note,
    businessName     = com.distrigo.app.data.BusinessSettingsStore.getBusinessName(context),
    businessLogoPath = com.distrigo.app.data.BusinessSettingsStore.getLogoFile(context)?.absolutePath
)

fun PurchaseOrder.toReceiptData(context: android.content.Context): ReceiptData = ReceiptData(
    documentTitle = "Bon d'achat #$id",
    partyLabel    = "Fournisseur",
    partyName     = supplier_name,
    dateLabel     = formatReceiptDate(created_at ?: date),
    items = (items ?: emptyList()).map {
        ReceiptLineItem(
            name = it.product_name, quantity = it.quantity, unitLabel = it.unit_type,
            unitPrice = it.unit_cost, totalPrice = it.total_cost,
            nbColis = it.nb_colis, unitePerColis = it.unite_par_colis
        )
    },
    total = total,
    paid  = montant_paye ?: 0.0,
    note  = note,
    businessName     = com.distrigo.app.data.BusinessSettingsStore.getBusinessName(context),
    businessLogoPath = com.distrigo.app.data.BusinessSettingsStore.getLogoFile(context)?.absolutePath
)