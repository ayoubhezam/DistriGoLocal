package com.distrigo.app.ui.components

import com.distrigo.app.data.model.Client
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
    /**
     * The date and the time, apart.
     *
     * These used to be one `dateLabel` that the preview and the PDF each split back up with
     * `substringBefore(" ")` / `substringAfter(" ")`. That only held while the format was exactly
     * `dd/MM/yyyy HH:mm`; naming the day ("vendredi 12/09/2026") puts a space inside the date and
     * hands the time half the date instead. Two fields cannot be mis-split.
     */
    val dateLabel     : String,
    val timeLabel     : String,
    val items         : List<ReceiptLineItem>,
    val total         : Double,
    val paid          : Double,
    val note          : String? = null,
    val businessName     : String = "DISTRIGO",
    val businessPhone    : String? = null,
    val businessLogoPath : String? = null,
    /** "Effectué par". Null on documents that never recorded it — printed as "-". */
    val performedBy   : String? = null,
    /** Already French ("Détail" / "Gros" / "Société"); null for a supplier document. */
    val clientType    : String? = null,
    val clientSecteur : String? = null
) {
    val balance: Double get() = total - paid
    val amountInWords: String get() = numberToFrenchWords(total)
    val qrContent: String get() = "$documentTitle | $dateLabel $timeLabel | ${"%.2f".format(total)} DA"

    /** What the header prints where a value is missing, so a row never renders blank. */
    fun orDash(value: String?): String = value?.takeIf { it.isNotBlank() } ?: "-"
}

/**
 * The two colis columns, defined once for both renderers.
 *
 * The preview and [ReceiptPdfGenerator] each used to carry a private copy of these. They agreed,
 * but nothing made them: the printed receipt and the one on screen are the same document, and a
 * rule duplicated in two files is a rule that eventually is not.
 */
internal fun nbColisText(item: ReceiptLineItem): String =
    item.nbColis?.let { formatQty(it) } ?: "-"

/**
 * Only meaningful where someone could actually state it. The purchase form exposes the
 * "Nb colis × Unités/colis" editor for `pièce` products only; a carton line keeps the seeded 1,
 * which would print as a column of "1"s that nobody entered and that means nothing.
 */
internal fun unitePerColisText(item: ReceiptLineItem): String =
    if (item.unitLabel == "pièce") item.unitePerColis?.toString() ?: "-" else "-"

private val RECEIPT_ZONE = ZoneId.of("Africa/Algiers")

/** "vendredi 12/09/2026" — the day named, as asked, and the rest unchanged. */
private fun formatReceiptDate(createdAt: String?): String = try {
    if (createdAt.isNullOrEmpty()) "" else Instant.parse(createdAt)
        .atZone(RECEIPT_ZONE)
        .format(DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", Locale.FRENCH))
} catch (e: Exception) { createdAt ?: "" }

private fun formatReceiptTime(createdAt: String?): String = try {
    if (createdAt.isNullOrEmpty()) "" else Instant.parse(createdAt)
        .atZone(RECEIPT_ZONE)
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH))
} catch (e: Exception) { "" }

/** The stored `customer_type` code as it is written everywhere else in the app. */
private fun customerTypeLabel(code: String?): String? = when (code) {
    "wholesale" -> "Gros"
    "business"  -> "Société"
    "retail"    -> "Détail"
    else        -> null
}

/**
 * @param client the sale's client, looked up by `client_id`, for the header's right-hand column.
 *   Optional: a receipt still renders without it, falling back to the name snapshotted on the
 *   vente, which is what a sale to a since-deleted client has left.
 */
fun Vente.toReceiptData(
    context: android.content.Context,
    client : Client? = null
): ReceiptData = ReceiptData(
    documentTitle = "Vente #$id",
    partyLabel    = "Client",
    partyName     = client_name,
    dateLabel     = formatReceiptDate(created_at),
    timeLabel     = formatReceiptTime(created_at),
    items = (items ?: emptyList()).map { line ->
        ReceiptLineItem(
            name = line.product_name, quantity = line.quantity, unitLabel = line.unit_type,
            unitPrice = line.unit_price, totalPrice = line.total_price,
            // A vente stores no colis breakdown of its own the way a purchase order does, and it
            // does not need one: for everything not sold by the piece the quantity IS the number
            // of colis. That is the same identity a purchase writes down explicitly — its carton
            // lines all carry nb_colis == quantity — so the two documents agree by construction
            // rather than by a second stored copy that could drift.
            //
            // Sold by the piece the quantity is a count of pieces, and the colis it came out of is
            // not recoverable from anything the sale recorded, so it stays null and prints "-".
            nbColis = if (line.unit_type != "pièce") line.quantity else null
        )
    },
    total = total,
    paid  = montant_paye ?: 0.0,
    note  = note,
    businessName     = com.distrigo.app.data.BusinessSettingsStore.getBusinessName(context),
    businessPhone    = com.distrigo.app.data.BusinessSettingsStore.getBusinessPhone(context),
    businessLogoPath = com.distrigo.app.data.BusinessSettingsStore.getLogoFile(context)?.absolutePath,
    performedBy   = user_name,
    clientType    = customerTypeLabel(client?.customer_type),
    clientSecteur = client?.secteur_name
)

fun PurchaseOrder.toReceiptData(context: android.content.Context): ReceiptData = ReceiptData(
    documentTitle = "Bon d'achat #$id",
    partyLabel    = "Fournisseur",
    partyName     = supplier_name,
    dateLabel     = formatReceiptDate(created_at ?: date),
    timeLabel     = formatReceiptTime(created_at ?: date),
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
    businessPhone    = com.distrigo.app.data.BusinessSettingsStore.getBusinessPhone(context),
    businessLogoPath = com.distrigo.app.data.BusinessSettingsStore.getLogoFile(context)?.absolutePath
    // performedBy / clientType / clientSecteur stay null: a purchase order records neither an
    // operator nor a customer, and the header omits those rows rather than printing empty ones.
)
