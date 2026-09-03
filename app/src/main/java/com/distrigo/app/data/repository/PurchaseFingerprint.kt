package com.distrigo.app.data.repository

import com.distrigo.app.data.model.DraftLine
import com.distrigo.app.data.model.DraftSnapshot

/**
 * The Achats purchase form's canonical content, hashed through [DraftFingerprint].
 *
 * ### Why this fingerprints the *form*, not the stored bon
 *
 * Both sides of every comparison go through the form-shaped values — the live form on one side,
 * and `PurchaseDraftRepository.prefilledLinesFor` / `montantPayeText` rebuilding the same shape
 * from the bon on the other. That is the invariant [DraftFingerprint] describes, and it holds here
 * for one reason the fields no longer show: `montant_paye` is a `Double` on the bon and the raw
 * text a person typed in the form, so only one of the two representations can be hashed and it has
 * to be the same one on both sides.
 *
 * The line fields used to need the same care for a second reason — the edit prefill rebuilt each
 * cart line as `nbColis = item.quantity, uniteParColis = 1` and the domain model dropped
 * `has_expiry`/`expiry_date` altogether, so what was stored and what was prefilled genuinely
 * differed. F14 fixed that: the prefill now carries every line field across verbatim. Going
 * through the prefill is therefore no longer compensating for a lossy conversion, but it stays the
 * rule rather than an accident — a future prefill change must not be able to make every parked
 * draft read as conflicted.
 *
 * ### What is deliberately excluded
 *
 * `status` is not part of the hash. Reception is not an edit to the form's content, and it is
 * already handled as its own hard block off the bon's status column — folding it in here would
 * make the dirty-check trip on a change the user did not make. `total` is excluded as redundant:
 * it is the sum over the lines that are already hashed.
 */
object PurchaseFingerprint {

    fun of(snapshot: DraftSnapshot): String =
        of(snapshot.supplierId, snapshot.montantPaye, snapshot.note, snapshot.lines)

    fun of(
        supplierId : Int?,
        montantPaye: String,
        note       : String,
        lines      : List<DraftLine>
    ): String {
        val canonical = buildString {
            append(supplierId ?: -1).append('|')
            // Parsed, not raw: "12" and "12.0" are the same amount, and the form's own save path
            // reads this field with exactly this expression. Typing should not register as a
            // change just because the text differs.
            append(DraftFingerprint.num(montantPaye.toDoubleOrNull() ?: 0.0)).append('|')
            append(note.trim()).append('|')
            // Sorted so that reordering the cart — which the form allows and the API ignores —
            // is not mistaken for an edit.
            lines.sortedBy { it.product_id }.forEach { l ->
                append(l.product_id).append(',')
                append(DraftFingerprint.num(l.quantity)).append(',')
                append(DraftFingerprint.num(l.unit_cost)).append(',')
                append(DraftFingerprint.num(l.nb_colis)).append(',')
                append(l.unite_par_colis).append(',')
                append(l.has_expiry).append(',')
                append(l.expiry_date.orEmpty()).append(';')
            }
        }
        return DraftFingerprint.of(canonical)
    }
}
