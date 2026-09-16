package com.distrigo.app.data.model

/**
 * One cart line inside a Dépôt Vente draft. Ids and user-entered values only — never a [Product].
 *
 * `unit_price` is seeded from `product.selling_price` but is editable, so re-reading the product on
 * restore would silently overwrite a negotiated price. `product_name` and `unit_type` are stored so
 * a card can render without a join, and so a since-deleted product can still be named and shown
 * with its unit. On restore the live product wins for everything else.
 *
 * Deliberately absent: `VenteCartItem.originalReservedQty`. That is not something a person entered
 * — it is this sale's own stock deduction, re-added to the live product snapshot so the form shows
 * what was available when the sale was made. It is derived from the *committed* vente, which may
 * have moved while the draft sat unsaved, so a resume must re-derive it rather than trust a stale
 * copy.
 */
data class VenteDraftLine(
    val product_id  : Int,
    val product_name: String,
    val unit_type   : String,
    val quantity    : Double,
    val unit_price  : Double
)

/** A Dépôt Vente draft with its lines parsed. What the UI layer consumes. */
data class VenteDraft(
    val id             : Int,
    val clientId       : Int?,
    val clientName     : String?,
    val lines          : List<VenteDraftLine>,
    val note           : String,
    val montantPaye    : String,
    val userName       : String,
    val itemCount      : Int,
    val total          : Double,
    val lastStep       : String,
    val createdAt      : String,
    val updatedAt      : String,
    val sourceVenteId  : Int?,
    val baseFingerprint: String?,
    val baseCapturedAt : String?,
    /**
     * Only ever [DraftBlock.NONE] or [DraftBlock.DELETED] — Dépôt Vente has no equivalent of the
     * received-bon block, because `ProductRepository.updateVente` reverses its own stock effects
     * inside the save transaction. It can never report that the vente's *contents* moved; that
     * needs the items, so it is resolved at resume by `VenteDraftRepository.resolveBaseState`.
     */
    val blockState     : DraftBlock = DraftBlock.NONE,
    /** The edited vente's number as shown — "#26" or "V-6DED-000027"; null for a new vente. */
    val sourceNumber   : String? = null
) {
    val isEdit: Boolean get() = sourceVenteId != null
}

/**
 * The form as it stands right now, handed to the repository for a write.
 *
 * `total` and `itemCount` are derived from [lines] on the way in, so a caller cannot desynchronise
 * the denormalised card fields from the payload.
 */
data class VenteDraftSnapshot(
    val clientId       : Int?,
    val clientName     : String?,
    val lines          : List<VenteDraftLine>,
    val note           : String,
    val montantPaye    : String,
    val userName       : String,
    val lastStep       : String,
    val sourceVenteId  : Int?    = null,
    val baseFingerprint: String? = null
) {
    /**
     * Whether there is nothing here worth keeping.
     *
     * The client is deliberately *not* counted, matching Achats' supplier. Choosing one is the
     * precondition for entering anything about the vente — the form refuses to go further without
     * it — not content in itself, and counting it meant that opening a new vente, picking a client
     * and pressing Back left a Brouillon behind with no product, no quantity and no note in it.
     *
     * What does count is what a person actually entered about the vente: its lines, its note, the
     * amount paid, or who made the sale. `userName` counts for a reason the others do not need:
     * it is written onto the stock movements rather than onto the `ventes` row, so if it is not
     * kept here it cannot be recovered from anywhere.
     *
     * Only consulted for a new vente. An edit is measured against its base fingerprint instead,
     * since it is non-empty from its very first emission.
     */
    val isEmpty: Boolean
        get() = lines.isEmpty() && note.isBlank() && montantPaye.isBlank() && userName.isBlank()

    val total: Double get() = lines.sumOf { it.quantity * it.unit_price }
}
