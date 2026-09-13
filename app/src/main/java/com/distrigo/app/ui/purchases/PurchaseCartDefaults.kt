package com.distrigo.app.ui.purchases

import com.distrigo.app.data.model.Product

// How a new Achats cart line starts, and how its pieces follow its colis.
//
// Kept apart from the composables for two reasons: the rules can be unit-tested without Compose,
// and the two places that put a product in the cart — a tap on the product list, and the auto-add
// after "Nouveau produit" — cannot seed a line two different ways.

/**
 * The units per colis a new line starts from.
 *
 * Only a `pièce` product is counted by the colis. A carton is its own unit, so it stays at 1, as
 * it always has.
 *
 * `pack_size` is the product's usual packaging. 0 means it was never stated — every product saved
 * before the field existed holds 0 — not "zero per colis", and seeding it would make
 * `quantity = nbColis × 0` a line of nothing at 0.00 DA.
 */
internal fun Product.defaultUniteParColis(): Int =
    if (unit_type == "pièce") pack_size.takeIf { it > 0 } ?: 1 else 1

/**
 * A product's first line in the cart: one colis, at the catalogue purchase price.
 *
 * A starting point only. Every field stays editable for this bon, and nothing here is written back
 * to the product: the same product has been received as 2, 12, 25 and 50 to a colis, and a
 * supplier's one-off pack must not redefine the catalogue's packaging.
 *
 * Resumed drafts and edits of a committed bon never come through here. They carry their stored
 * values verbatim, so a later change to `pack_size` cannot reach a line that already exists.
 */
internal fun newPurchaseCartItem(product: Product): CartItem {
    val uniteParColis = product.defaultUniteParColis()
    return CartItem(
        product       = product,
        quantity      = 1.0 * uniteParColis,
        unitCost      = product.purchase_price,
        nbColis       = 1.0,
        uniteParColis = uniteParColis
    )
}

/** Another count of colis, the pieces following it: `quantity = nbColis × uniteParColis`. */
internal fun CartItem.withNbColis(nbColis: Double): CartItem =
    copy(nbColis = nbColis, quantity = nbColis * uniteParColis)

/** Another packaging for this bon only, the pieces recomputed the same way. */
internal fun CartItem.withUniteParColis(uniteParColis: Int): CartItem =
    copy(uniteParColis = uniteParColis, quantity = nbColis * uniteParColis)
