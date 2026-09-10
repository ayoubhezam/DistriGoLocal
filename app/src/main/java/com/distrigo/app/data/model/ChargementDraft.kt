package com.distrigo.app.data.model

/**
 * One line of an unfinished stock movement.
 *
 * [target_camion] is what the user asked for — "15 in the truck" — not the delta that will be
 * written. The delta is `target_camion - product.camion_stock` computed at save time, against the
 * truck as it stands then. That is deliberate: a draft says what you wanted the truck to hold, and
 * if the truck reached that figure by itself while the draft sat unsaved, there is nothing left to
 * move and the save writes nothing.
 *
 * [product_name] and [unit_type] are stored so a card can render without a join, and so a
 * since-deleted product can still be named.
 */
data class ChargementDraftLine(
    val product_id   : Int,
    val product_name : String,
    val unit_type    : String,
    val target_camion: Double
)

/** A chargement draft with its lines parsed. What the UI layer consumes. */
data class ChargementDraft(
    val id             : Int,
    /**
     * Which kind of draft this is, and the whole reason there is one table rather than two.
     *
     * `null` — an ordinary Brouillon from "Nouveau chargement": listed, counted, resumable.
     *
     * Set — the private editing state of the single-product "Modifier" card. It exists only so a
     * crash, a battery death or a swipe-away cannot lose what someone typed, and it is deliberately
     * invisible: never in the Brouillons list, never in the count. It is restored silently when the
     * same product's card is opened again, finalised by "Enregistrer le mouvement", and thrown away
     * when the user chooses to discard.
     */
    val singleProductId: Int?,
    val lines          : List<ChargementDraftLine>,
    val note           : String,
    val userName       : String,
    val itemCount      : Int,
    val createdAt      : String,
    val updatedAt      : String
) {
    /** True for the invisible per-product editing state, false for a listed Brouillon. */
    val isSingleProduct: Boolean get() = singleProductId != null
}

/**
 * The form as it stands right now, handed to the repository for a write.
 *
 * `itemCount` is derived from [lines] on the way in, so a caller cannot desynchronise the
 * denormalised card field from the payload.
 */
data class ChargementDraftSnapshot(
    val singleProductId: Int?,
    val lines          : List<ChargementDraftLine>,
    val note           : String,
    val userName       : String
) {
    /**
     * Whether there is nothing here worth keeping.
     *
     * A chargement has no supplier or client, so unlike the other three flows there is no
     * "precondition, not content" field to exclude — everything here is something a person entered.
     *
     * A line whose target still equals the truck's current stock is *not* excluded, and that
     * matters most for the single-product card: opening it, nudging the stepper up and back down
     * again leaves a line with a zero delta, and the user has still touched something. Emptiness is
     * decided by the *caller*, which knows whether a line was ever added; what this rule catches is
     * a form nobody has typed into at all.
     */
    val isEmpty: Boolean
        get() = lines.isEmpty() && note.isBlank() && userName.isBlank()
}
