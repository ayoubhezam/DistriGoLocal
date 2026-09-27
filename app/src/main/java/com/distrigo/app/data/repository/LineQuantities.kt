package com.distrigo.app.data.repository

import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.model.Quantity

/**
 * A document line's quantity, as a repository writes it: rounded to the thousandth (see Quantity), so a
 * value typed with more decimals, or computed as colis × units, reaches the ledger exact. The totals are
 * computed from it too, so a line's total always matches the quantity it shows.
 */
internal fun Map<String, Any?>.lineQuantity(key: String = "quantity"): Double =
    Quantity.normalize((this[key] as Number).toDouble())

/** Why [quantity] of a product named [name] cannot be written, or null: a pièce product moves by whole units. */
internal fun unitError(name: String, unitType: String?, quantity: Double): String? =
    if (Quantity.fitsUnit(quantity, unitType)) null
    else "« $name » se compte à la pièce : ${Quantity.format(quantity)} n'est pas un nombre entier."

/** Refuses part of a piece, for the repositories that report failures by throwing. */
internal fun requireFitsUnit(name: String, unitType: String?, quantity: Double) {
    unitError(name, unitType, quantity)?.let { throw IllegalStateException(it) }
}

/**
 * The first line of [items] that asks for part of a piece, as a message; null when every line fits its
 * unit. For the repositories that report failures as an `"error"` entry, checked before they write.
 */
internal suspend fun AppDatabase.unitErrorIn(items: List<Map<String, Any?>>): String? {
    for (line in items) {
        val product = productDao().getProductByIdIncludingBin((line["product_id"] as Number).toInt()) ?: continue
        unitError(product.name, product.unit_type, line.lineQuantity())?.let { return it }
    }
    return null
}
