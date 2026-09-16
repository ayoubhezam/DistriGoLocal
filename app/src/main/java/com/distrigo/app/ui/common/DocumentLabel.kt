package com.distrigo.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState

/**
 * A document's number as shown — "#26" or "V-6DED-000027" — for a screen that only holds its id,
 * such as a form title. See ProductRepository.documentLabel.
 *
 * Null until the lookup returns, so a title reads "Modifier la vente" for that moment rather than
 * showing the local id and then changing to a different number.
 */
@Composable
fun documentLabel(sourceType: String, id: Int?, lookup: suspend (String, Int) -> String): String? {
    if (id == null) return null
    val label by produceState<String?>(initialValue = null, sourceType, id) { value = lookup(sourceType, id) }
    return label
}
