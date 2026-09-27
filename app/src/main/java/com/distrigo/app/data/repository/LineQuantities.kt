package com.distrigo.app.data.repository

import com.distrigo.app.data.model.Quantity

/**
 * A document line's quantity, as a repository writes it: rounded to the thousandth (see Quantity), so a
 * value typed with more decimals, or computed as colis × units, reaches the ledger exact. The totals are
 * computed from it too, so a line's total always matches the quantity it shows.
 */
internal fun Map<String, Any?>.lineQuantity(key: String = "quantity"): Double =
    Quantity.normalize((this[key] as Number).toDouble())
