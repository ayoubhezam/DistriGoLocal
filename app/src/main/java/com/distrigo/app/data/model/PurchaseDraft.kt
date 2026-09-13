package com.distrigo.app.data.model

/**
 * One cart line inside a draft. Ids and user-entered values only — never a [Product].
 *
 * Everything here is something a person typed or stepped, and none of it can be recomputed from
 * the catalogue on restore:
 *  - `unit_cost` is seeded from `product.purchase_price` but is editable, so re-reading the
 *    product would silently overwrite a negotiated price.
 *  - `unite_par_colis` is seeded from `product.pack_size` for a "pièce" product (1 when that is 0,
 *    i.e. never stated, and always 1 otherwise) but is editable per bon: the same product has been
 *    received as 2, 12, 25 and 50 to a colis. Re-reading the product would overwrite the packaging
 *    this delivery actually came in, and would pick up any later change to the catalogue value.
 *  - `has_expiry` is seeded false, independent of `product.has_expiry`.
 *  - `quantity` is `nb_colis * unite_par_colis` for "pièce" and `nb_colis` otherwise — a branch on
 *    `product.unit_type`, which is live data that may have changed since.
 *
 * `product_name` is stored only so a card can render without a join, and so a since-deleted
 * product can still be named. On restore the live product wins for everything else.
 */
data class DraftLine(
    val product_id      : Int,
    val product_name    : String,
    val quantity        : Double,
    val unit_cost       : Double,
    val nb_colis        : Double,
    val unite_par_colis : Int,
    val has_expiry      : Boolean,
    val expiry_date     : String?
)

/** A draft with its lines parsed. What the UI layer consumes. */
data class PurchaseDraft(
    val id             : Int,
    val supplierId     : Int?,
    val supplierName   : String?,
    val lines          : List<DraftLine>,
    val note           : String,
    val montantPaye    : String,
    val itemCount      : Int,
    val total          : Double,
    val lastStep       : String,
    val createdAt      : String,
    val updatedAt      : String,
    val sourceOrderId  : Int?,
    val baseFingerprint: String?,
    val baseCapturedAt : String?,
    /**
     * Only ever [DraftBlock.NONE], [DraftBlock.RECEIVED] or [DraftBlock.DELETED] — the cheap
     * state the list query can establish from the bon's status alone. It can never report that
     * the bon's *contents* moved; that needs the items, so it is resolved at resume by
     * `PurchaseDraftRepository.resolveBaseState`.
     */
    val blockState     : DraftBlock = DraftBlock.NONE
) {
    val isEdit: Boolean get() = sourceOrderId != null
}

/**
 * The form as it stands right now, handed to the repository for a write.
 *
 * `total` and `itemCount` are derived from [lines] on the way in, so a caller cannot desynchronise
 * the denormalised card fields from the payload.
 */
data class DraftSnapshot(
    val supplierId     : Int?,
    val supplierName   : String?,
    val lines          : List<DraftLine>,
    val note           : String,
    val montantPaye    : String,
    val lastStep       : String,
    val sourceOrderId  : Int?    = null,
    val baseFingerprint: String? = null
) {
    /**
     * Whether there is nothing here worth keeping.
     *
     * The supplier is deliberately *not* counted. Choosing one is the precondition for entering
     * anything about the bon — the form refuses to go further without it — not content in itself,
     * and counting it meant that opening "Nouveau bon", picking a supplier and pressing Back left a
     * Brouillon behind with no product, no quantity, no note and no amount in it.
     *
     * What does count is what a person actually entered about the bon: its lines, its note, or the
     * amount paid. Any one of them makes the draft worth resuming.
     *
     * Only consulted for a new bon. An edit is measured against its base fingerprint instead, since
     * it is non-empty from its very first emission.
     */
    val isEmpty: Boolean get() = lines.isEmpty() && note.isBlank() && montantPaye.isBlank()
    val total: Double get() = lines.sumOf { it.quantity * it.unit_cost }
}
