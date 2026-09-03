package com.distrigo.app.data.repository

import com.distrigo.app.data.model.VenteDraftLine
import com.distrigo.app.data.model.VenteDraftSnapshot

/**
 * The Dépôt Vente form's canonical content, hashed through [DraftFingerprint].
 *
 * ### Why this fingerprints the *form*, not the stored vente
 *
 * The rule [DraftFingerprint] describes — both sides built from the form-shaped values — bites
 * harder here than it does for Achats, in two places:
 *
 *  - `montant_paye` is a `Double` on the vente and the raw text a person typed in the form.
 *  - `user_name` is written onto the **stock movements**, not onto the `ventes` row, so a
 *    committed vente has nothing to prefill it back from. The edit prefill therefore always
 *    produces `""`, and `VenteDraftRepository.prefilledUserName` returns `""` on the other side to
 *    match. Both sides agreeing is what matters, not which value they agree on: typing a name is
 *    then a real change that marks the draft dirty, and a resume gives it back.
 *
 * ### What is deliberately excluded
 *
 * The cart line's product snapshot. `VenteCartItem` carries a `Product` whose stock has this
 * sale's own deduction re-added to it for display, and that number moves whenever anything else in
 * the app touches stock. Hashing it would make every parked draft read as conflicted the moment an
 * unrelated sale went through. Only what a person actually chose is hashed: the product, how many,
 * and at what price.
 *
 * `status` is not part of the hash either. Marking a vente delivered is not an edit to the form's
 * content, and unlike Achats it is not a block on applying one — `ProductRepository.updateVente`
 * reverses its own stock effects, so a delivered vente stays editable. `total` is excluded as
 * redundant: it is the sum over the lines that are already hashed.
 */
object VenteFingerprint {

    fun of(snapshot: VenteDraftSnapshot): String =
        of(snapshot.clientId, snapshot.montantPaye, snapshot.note, snapshot.userName, snapshot.lines)

    fun of(
        clientId   : Int?,
        montantPaye: String,
        note       : String,
        userName   : String,
        lines      : List<VenteDraftLine>
    ): String {
        val canonical = buildString {
            append(clientId ?: -1).append('|')
            // Parsed, not raw: "12" and "12.0" are the same amount, and the form's own save path
            // reads this field with exactly this expression. Typing should not register as a
            // change just because the text differs.
            append(DraftFingerprint.num(montantPaye.toDoubleOrNull() ?: 0.0)).append('|')
            append(note.trim()).append('|')
            append(userName.trim()).append('|')
            // Sorted so that reordering the cart — which the form allows and the save path ignores
            // — is not mistaken for an edit.
            lines.sortedBy { it.product_id }.forEach { l ->
                append(l.product_id).append(',')
                append(DraftFingerprint.num(l.quantity)).append(',')
                append(DraftFingerprint.num(l.unit_price)).append(';')
            }
        }
        return DraftFingerprint.of(canonical)
    }
}
