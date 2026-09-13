package com.distrigo.app.data.model

/**
 * One cart line inside a Tournée Vente draft. Ids and user-entered values only — never a [Product].
 *
 * Shaped exactly like [VenteDraftLine], and for the same reasons: `unit_price` is seeded from the
 * product but editable, so re-reading it on restore would overwrite a negotiated price, and the
 * name and unit are stored so a card can render without a join and a since-deleted product can
 * still be named.
 */
data class TourneeVenteDraftLine(
    val product_id  : Int,
    val product_name: String,
    val unit_type   : String,
    val quantity    : Double,
    val unit_price  : Double
)

/**
 * A Tournée Vente draft with its lines parsed. What the UI layer consumes.
 *
 * Two things are absent that both other draft flows carry, and their absence is the point:
 *
 *  - **No source record.** The tournée vente form is create-only — its graph takes no `venteId`
 *    and it calls `createVente`, never `updateVente`. There is therefore no committed vente for a
 *    draft to be an unsaved edit *of*, which removes the base fingerprint, the conflict check and
 *    the OBSOLÈTE state along with it.
 *  - **No block state.** With nothing to diverge from, the only thing that can stop a resume is
 *    the catalogue or the truck moving underneath it, and both of those are resolved in the form
 *    at hydrate time rather than badged on the card.
 *
 * What it carries instead is [tourneeId]: a sale from the van belongs to the day's round, so a
 * draft does too, and the Brouillons list is scoped to one tournée rather than global.
 */
data class TourneeVenteDraft(
    val id         : Int,
    val tourneeId  : Int,
    val clientId   : Int?,
    val clientName : String?,
    val lines      : List<TourneeVenteDraftLine>,
    val note       : String,
    val montantPaye: String,
    val userName   : String,
    val itemCount  : Int,
    val total      : Double,
    val lastStep   : String,
    val createdAt  : String,
    val updatedAt  : String
)

/**
 * The form as it stands right now, handed to the repository for a write.
 *
 * `total` and `itemCount` are derived from [lines] on the way in, so a caller cannot desynchronise
 * the denormalised card fields from the payload.
 */
data class TourneeVenteDraftSnapshot(
    val tourneeId  : Int,
    val clientId   : Int?,
    val clientName : String?,
    val lines      : List<TourneeVenteDraftLine>,
    val note       : String,
    val montantPaye: String,
    val userName   : String,
    val lastStep   : String
) {
    /**
     * Whether there is nothing here worth keeping.
     *
     * The client is deliberately not counted, matching the supplier in Achats and the client in
     * Dépôt Vente. Choosing one is the precondition for entering anything about the sale — the
     * form refuses to go further without it — not content in itself. Counting it is what left
     * empty Brouillons behind when someone opened a form, picked a name and pressed Back.
     *
     * [userName] counts, exactly as it does in Dépôt Vente: someone who typed who is driving has
     * entered something about the sale, and losing it on Back is the same small betrayal as losing
     * a note.
     */
    val isEmpty: Boolean
        get() = lines.isEmpty() && note.isBlank() && montantPaye.isBlank() && userName.isBlank()

    val total: Double get() = lines.sumOf { it.quantity * it.unit_price }
}
