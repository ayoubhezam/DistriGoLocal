package com.distrigo.app.ui.common

/**
 * Where a form session is in its lifecycle. Drives what its graph is allowed to do.
 *
 * Shared by every flow that keeps Brouillons: the three states are a property of how a draft
 * session starts, not of any one form's fields.
 */
enum class SessionPhase {
    /** Nothing decided yet. The graph must call the session's `beginOrResumeSession`. */
    UNDECIDED,

    /**
     * A fresh edit of a committed record. The graph still has to prefill the form from it, and
     * autosave stays disarmed until that signals completion — an empty form measured against a
     * non-empty base would read as a wholesale edit and be persisted as one.
     */
    PREFILLING_EDIT,

    /** The form holds what it should. Autosave is armed. */
    READY
}
