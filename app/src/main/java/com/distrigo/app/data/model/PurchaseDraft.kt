package com.distrigo.app.data.model

/**
 * One cart line inside a draft. Ids and user-entered values only — never a [Product].
 *
 * Everything here is something a person typed or stepped, and none of it can be recomputed from
 * the catalogue on restore:
 *  - `unit_cost` is seeded from `product.purchase_price` but is editable, so re-reading the
 *    product would silently overwrite a negotiated price.
 *  - `unite_par_colis` is seeded to 1, not `product.pack_size`, so it is not derivable at all.
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

/** What the drafts list can tell about an edit draft's bon without loading its items. */
enum class DraftBlock {
    /** A new-purchase draft, or an edit whose bon is still present and pending. */
    NONE,

    /** The bon was received; reception already moved stock, so the draft can no longer be applied. */
    RECEIVED,

    /** The bon is gone. Nothing left to edit. */
    DELETED
}

/** The full answer at resume time, once the bon's items have been read. */
enum class DraftBaseState {
    /** A new-purchase draft — there is no base. Resume silently. */
    NONE,

    /** The bon is exactly as it was when the edit began. Resume silently. */
    UNCHANGED,

    /** The bon still exists and is pending, but its contents moved. Ask before opening the form. */
    CHANGED,

    /** Hard block — see [DraftBlock.RECEIVED]. */
    RECEIVED,

    /** Hard block — see [DraftBlock.DELETED]. */
    DELETED
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
    val isEmpty: Boolean get() = supplierId == null && lines.isEmpty()
    val total: Double get() = lines.sumOf { it.quantity * it.unit_cost }
}
