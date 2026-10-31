package com.distrigo.app.ui.common

import com.distrigo.app.R
import com.distrigo.app.core.text.UiText
import com.distrigo.app.data.model.ProductUnit
import com.distrigo.app.data.model.Quantity
import kotlin.math.abs
import kotlin.math.floor

/**
 * A quantity and its unit as the app's language writes them: « 3 cartons », « 1,5 kg », « 3 كراتين ». The screens'
 * [ProductUnit.plural] in resources; documents keep the French one (D3). A unit the app does not know is written as
 * it is stored.
 */
fun quantityText(quantity: Double, unit: String?): UiText {
    val n = Quantity.format(quantity)
    val id = when (unit) {
        ProductUnit.PIECE -> R.plurals.common_qty_piece
        ProductUnit.KG -> R.plurals.common_qty_kg
        null, ProductUnit.CARTON -> R.plurals.common_qty_carton
        else -> return UiText.Raw("$n ${ProductUnit.plural(unit, quantity)}")
    }
    return UiText.Plural(id, pluralCount(quantity), listOf(n))
}

/**
 * The unit alone, agreeing with [quantity], for a figure set apart from its unit: « cartons », « علب ». A unit the app
 * does not know is written as [quantityText] writes it.
 */
fun unitWordText(quantity: Double, unit: String?): UiText {
    val id = when (unit) {
        ProductUnit.PIECE -> R.plurals.common_unit_word_piece
        ProductUnit.KG -> R.plurals.common_unit_word_kg
        null, ProductUnit.CARTON -> R.plurals.common_unit_word_carton
        else -> return UiText.Raw(ProductUnit.plural(unit, quantity))
    }
    return UiText.Plural(id, pluralCount(quantity), emptyList())
}

/**
 * The count a plural form is chosen by. A whole quantity is itself; a fraction reads as French does today (« 0,5
 * carton », « 1,5 cartons ») and takes Arabic's « other » form, as CLDR gives fractions: 1 up to one, else 100.
 */
internal fun pluralCount(quantity: Double): Int {
    val q = abs(quantity)
    return when {
        q == floor(q) && q < 1_000_000 -> q.toInt()
        q <= 1.0 -> 1
        else -> 100
    }
}
