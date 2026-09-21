package com.distrigo.app.ui.mouvements

/** Whether a movement was with a client or with a supplier. */
enum class MovementParty(val key: String, val label: String) {
    CLIENT("client", "Client"),
    FOURNISSEUR("fournisseur", "Fournisseur"),
}

/** A client or a supplier the filter can narrow to. */
data class PartyOption(val id: Int, val name: String)

/** The kinds of movement a product's ledger holds, in the words the screen uses. */
enum class MovementType(val key: String, val label: String) {
    ACHAT("achat", "Achat"),
    VENTE("vente", "Vente"),
    RETOUR_CLIENT("retour_client", "Retour client"),
    RETOUR_FOURNISSEUR("retour_fournisseur", "Retour fournisseur"),
    PERTE("perte", "Perte"),
    AJUSTEMENT("ajustement", "Ajustement"),
}

/**
 * What the Mouvements list is narrowed by.
 *
 * Empty [types] means every kind, as "Tous" does elsewhere. [party] on its own keeps the movements
 * that were with a client (or with a supplier) whoever they were; [partyId] narrows to one of them
 * and is dropped whenever [party] changes, since a client's id means nothing among suppliers.
 */
data class MovementFilters(
    val dateFrom    : String? = null,   // "yyyy-MM-dd"
    val dateTo      : String? = null,
    val direction   : String? = null,   // null = les deux | "entree" | "sortie"
    val emplacement : String? = null,   // null = les deux | "depot" | "camion"
    val types       : Set<MovementType> = emptySet(),
    val party       : MovementParty? = null,
    val partyId     : Int? = null,
) {
    /** A period that ends before it starts would silently return nothing; the sheet refuses it instead. */
    val periodIsBackwards: Boolean
        get() = dateFrom != null && dateTo != null && dateFrom > dateTo

    /** How many filters are set, for the badge on the filter button. */
    val activeCount: Int
        get() = listOf(
            dateFrom != null || dateTo != null,
            direction != null,
            emplacement != null,
            types.isNotEmpty(),
            party != null,
        ).count { it }
}
