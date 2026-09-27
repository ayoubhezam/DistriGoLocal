package com.distrigo.app.data.model

/**
 * The units a product is counted in — `products.unit_type` — written once.
 *
 *  - **carton**: counted in cartons; half a carton is a quantity.
 *  - **pièce**: counted in pieces, whole ones only; bought by the colis ("Nombre de colis" ×
 *    "Unités/colis"), since that is how a warehouse receives them.
 *  - **kg**: weighed; any weight to the gram (1.250 kg). Bought as one total weight, like a carton
 *    product's number of cartons: bulk weight has no fixed colis.
 *
 * Which of them take fractions is [Quantity.allowsFractions]. The values are stored as they are here;
 * never rename one, it is in every product row and every backup.
 */
object ProductUnit {

    const val CARTON = "carton"
    const val PIECE = Quantity.UNIT_PIECE
    const val KG = "kg"

    /** In the order the forms and filters offer them. */
    val ALL = listOf(CARTON, PIECE, KG)

    /** "Carton", "Pièce", "Kg": a unit as a choice or a filter names it. */
    fun label(unit: String?): String = when (unit) {
        PIECE -> "Pièce"
        KG    -> "Kg"
        else  -> "Carton"
    }

    /** The unit after a quantity: "1 carton", "3 cartons", "1.25 kg" — kg does not take an "s". */
    fun plural(unit: String, quantity: Double): String =
        if (unit == KG || quantity <= 1.0 || unit.endsWith("s")) unit else "${unit}s"

    /** Bought by the colis: the purchase form's "Nombre de colis" × "Unités/colis". */
    fun isBoughtByColis(unit: String?): Boolean = unit == PIECE
}
