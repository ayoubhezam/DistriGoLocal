package com.distrigo.app.data.repository

import com.distrigo.app.data.model.DraftLine
import com.distrigo.app.data.model.DraftSnapshot
import java.security.MessageDigest
import java.util.Locale

/**
 * A stable hash of a purchase form's editable content.
 *
 * Used for two things, which is why it lives on its own:
 *
 *  1. **Conflict detection.** An edit draft records the fingerprint of the form as it was first
 *     prefilled from its bon. At resume we prefill from the *current* bon and compare — a
 *     difference means the bon moved underneath while the draft sat unsaved. The fingerprint only
 *     ever detects that; it is never used to merge, so a resume can only ever offer states a
 *     person actually authored.
 *
 *  2. **Dirty-checking.** An edit form is non-empty from its very first emission, so the emptiness
 *     rule that stops new-purchase drafts being born on open does nothing here. Comparing the live
 *     form against the same recorded fingerprint answers "has anything actually changed yet?" —
 *     which keeps untouched edits out of the Brouillons list, and deletes the draft again if the
 *     user undoes back to the original values.
 *
 * ### Why this fingerprints the *form*, not the stored bon
 *
 * The two are not the same thing. `PurchaseFormNavGraph`'s edit prefill rebuilds each cart line as
 * `nbColis = item.quantity, uniteParColis = 1`, discarding the stored `nb_colis`/`unite_par_colis`
 * breakdown, and the `PurchaseOrderItem` domain model drops `has_expiry`/`expiry_date` altogether.
 * Fingerprinting what is *stored* would therefore make a freshly opened, untouched edit form read
 * as dirty immediately — manufacturing exactly the phantom drafts the dirty-check exists to
 * prevent. So both sides of every comparison go through the form-shaped values, via
 * `PurchaseDraftRepository.prefilledLinesFor`.
 *
 * ### What is deliberately excluded
 *
 * `status` is not part of the hash. Reception is not an edit to the form's content, and it is
 * already handled as its own hard block off the bon's status column — folding it in here would
 * make the dirty-check trip on a change the user did not make. `total` is excluded as redundant:
 * it is the sum over the lines that are already hashed.
 */
object DraftFingerprint {

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
            append(num(montantPaye.toDoubleOrNull() ?: 0.0)).append('|')
            append(note.trim()).append('|')
            // Sorted so that reordering the cart — which the form allows and the API ignores —
            // is not mistaken for an edit.
            lines.sortedBy { it.product_id }.forEach { l ->
                append(l.product_id).append(',')
                append(num(l.quantity)).append(',')
                append(num(l.unit_cost)).append(',')
                append(num(l.nb_colis)).append(',')
                append(l.unite_par_colis).append(',')
                append(l.has_expiry).append(',')
                append(l.expiry_date.orEmpty()).append(';')
            }
        }
        return sha256(canonical)
    }

    /**
     * Locale.ROOT is not optional here: the app runs in French, where the default `"%.6f"` yields
     * "1,500000". A fingerprint that changes with the device locale would report every draft as
     * conflicted the first time someone switched language.
     */
    private fun num(v: Double): String = String.format(Locale.ROOT, "%.6f", v)

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format(Locale.ROOT, "%02x", it) }
}
