package com.distrigo.app.data.model

// The two states a draft's base record can be in, shared by every flow that keeps Brouillons.
// The cheap one is resolved for the whole list at once; the full one needs the record's lines and
// is resolved at resume, where it is the only moment it changes what happens.

/** What a drafts list can tell about an edit draft's source record without loading its lines. */
enum class DraftBlock {
    /** A new-record draft, or an edit whose source record is still present and editable. */
    NONE,

    /**
     * The source record has passed the point where a form edit can still be applied — for Achats,
     * a bon that has been received, because reception already moved stock.
     *
     * A flow whose update path reverses its own effects inside the save transaction has no such
     * state and never produces this. Depot Vente is one: `ProductRepository.updateVente` reverses
     * every old line before applying the new ones, so even a delivered vente stays editable.
     */
    RECEIVED,

    /** The source record is gone. Nothing left to edit. */
    DELETED
}

/** The full answer at resume time, once the source record's lines have been read. */
enum class DraftBaseState {
    /** A new-record draft — there is no base. Resume silently. */
    NONE,

    /** The record is exactly as it was when the edit began. Resume silently. */
    UNCHANGED,

    /** The record still exists and is editable, but its contents moved. Ask before opening the form. */
    CHANGED,

    /** Hard block — see [DraftBlock.RECEIVED]. */
    RECEIVED,

    /** Hard block — see [DraftBlock.DELETED]. */
    DELETED
}
