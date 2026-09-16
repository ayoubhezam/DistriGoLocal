package com.distrigo.app.data.model

/**
 * How a document's number is shown: `V-6DED-000124` for a document numbered since version 49, and
 * `#26` for one created before, exactly as it has always been shown.
 *
 * A number made only of digits is an old document's, which stored its id as its number (see
 * DocumentNumberTriggers); it keeps the `#`. With no number at all — a document created without a
 * device identity — the local id stands in, which is also how it looked before.
 *
 * On a receipt the `#` is dropped, as it always was: "N° 26", "N° V-6DED-000124" ([receiptNumber]).
 */
fun numberLabel(numero: String?, id: Int): String = when {
    numero.isNullOrBlank()          -> "#$id"
    numero.all { it.isDigit() }     -> "#$numero"
    else                            -> numero
}

/** The number as printed after "N°" on a receipt. */
fun receiptNumber(numero: String?, id: Int): String = numberLabel(numero, id).removePrefix("#")

val Vente.numberLabel: String get() = numberLabel(numero, id)
val PurchaseOrder.numberLabel: String get() = numberLabel(numero, id)
val RetourClient.numberLabel: String get() = numberLabel(numero, id)
val RetourFournisseur.numberLabel: String get() = numberLabel(numero, id)
val ClientTransaction.numberLabel: String get() = numberLabel(numero, id)
val SupplierTransaction.numberLabel: String get() = numberLabel(numero, id)

/**
 * The same label in SQL, for searches that run in the database: `#26`, `V-6DED-000124`, or `#id`.
 * Keeps the ledger searches finding "#26" as they did, and the new numbers by any part of them.
 */
const val NUMBER_LABEL_SQL =
    "COALESCE(CASE WHEN numero GLOB '[0-9]*' AND numero NOT GLOB '*[^0-9]*' THEN '#' || numero " +
        "ELSE numero END, '#' || CAST(id AS TEXT))"
