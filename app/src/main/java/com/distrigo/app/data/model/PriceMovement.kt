package com.distrigo.app.data.model

/** Which side of the trade a price came from. */
enum class PriceMovementKind(val label: String) {
    ACHAT("Achat"),
    VENTE("Vente");

    val sourceType: String get() = if (this == ACHAT) "purchase_order" else "vente"
}

/**
 * One price the product actually changed hands at: a line of a bon d'achat, or a line of a vente.
 *
 * ### Why the documents rather than `price_history`
 *
 * `price_history` records a purchase price per bon line, but it has no document id — so an entry
 * cannot say which bon it came from, nor be opened — and editing a bon appends a second row rather
 * than correcting the first. Reading the lines themselves gives both sides the same shape, an entry
 * that names its document, and a history that follows an edited bon instead of accumulating.
 *
 * [delta] is the change from the previous movement **of the same kind** — a purchase is compared
 * with the purchase before it, never with a sale — and is null for the first one of its kind.
 */
data class PriceMovement(
    val kind       : PriceMovementKind,
    /** The bon or the vente this line belongs to. */
    val documentId : Int,
    /** How that document is numbered — `BA-6DED-000031`, or `#26` for one from before numbering. */
    val documentLabel : String,
    /** The supplier bought from, or the client sold to. */
    val party      : String,
    /** When it happened: an ISO instant, or a calendar date for an older bon. */
    val date       : String,
    val unitPrice  : Double,
    val quantity   : Double,
    val delta      : Double? = null,
)

/**
 * Fills in each movement's [PriceMovement.delta] from the one before it of the same kind.
 *
 * [movements] must be newest first, which is how the query returns them; the entry compared against
 * is therefore the *next* one of that kind in the list.
 */
fun List<PriceMovement>.withDeltas(): List<PriceMovement> {
    val lastPriceOfKind = mutableMapOf<PriceMovementKind, Double>()
    // Walked oldest first, so each movement meets its predecessor before it is measured.
    val oldestFirst = asReversed().map { movement ->
        val previous = lastPriceOfKind.put(movement.kind, movement.unitPrice)
        movement.copy(delta = previous?.let { movement.unitPrice - it })
    }
    return oldestFirst.asReversed()
}
