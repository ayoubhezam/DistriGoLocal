package com.distrigo.app.data.repository

import java.security.MessageDigest
import java.util.Locale

/**
 * The hashing core every form domain's draft fingerprint is built on.
 *
 * A fingerprint is a stable hash of a form's editable content, and each domain answers two
 * questions with it:
 *
 *  1. **Conflict detection.** An edit draft records the fingerprint of the form as it was first
 *     prefilled from its source record. At resume the form is prefilled again from the *current*
 *     record and the two are compared — a difference means the record moved underneath while the
 *     draft sat unsaved. The fingerprint only ever detects that; it is never used to merge, so a
 *     resume can only ever offer states a person actually authored.
 *
 *  2. **Dirty-checking.** An edit form is non-empty from its very first emission, so the emptiness
 *     rule that stops new-record drafts being born on open does nothing here. Comparing the live
 *     form against the same recorded fingerprint answers "has anything actually changed yet?" —
 *     which keeps untouched edits out of the Brouillons list, and deletes the draft again if the
 *     user undoes back to the original values.
 *
 * What is domain-specific is only the *canonical string*: which fields are in it, in what order,
 * and how lines are sorted. That lives with each domain (see [PurchaseFingerprint]). What lives
 * here is everything that must not vary between them — the digest, and the number formatting.
 *
 * ### The rule every domain's canonical builder must follow
 *
 * Both sides of every comparison must be built from the **form-shaped** values, never from the
 * stored row directly. The stored row and the form are two representations of the same content
 * (a paid amount is a `Double` in the database and the raw text a person typed in the form), and
 * hashing one against the other reports a difference that nobody made — manufacturing exactly the
 * phantom drafts the dirty-check exists to prevent.
 */
object DraftFingerprint {

    /** SHA-256 of a domain's canonical string. */
    fun of(canonical: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { String.format(Locale.ROOT, "%02x", it) }

    /**
     * Locale.ROOT is not optional here: the app runs in French, where the default `"%.6f"` yields
     * "1,500000". A fingerprint that changes with the device locale would report every draft as
     * conflicted the first time someone switched language.
     */
    fun num(v: Double): String = String.format(Locale.ROOT, "%.6f", v)
}
