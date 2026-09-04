package com.distrigo.app.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * What a form's session ViewModel must expose for [DraftAutosave] to keep its Brouillon current.
 *
 * The domain owns the shape of a snapshot and what "dirty" means for its own fields; everything
 * else — when to write, when to delete, and how not to write on open — is the same in every flow
 * and lives in [DraftAutosave].
 *
 * @param S the domain's form snapshot type (e.g. `DraftSnapshot` for Achats).
 */
interface DraftAutosaveHost<S> {

    /** The form exactly as it stands right now. */
    fun snapshot(): S

    /** True when this session is editing an already-committed record rather than creating one. */
    fun isEdit(snapshot: S): Boolean

    /** True when nothing has been entered yet. Only consulted for a non-edit session. */
    fun isEmpty(snapshot: S): Boolean

    /** The domain's canonical hash of [snapshot] — see `DraftFingerprint`. */
    fun fingerprintOf(snapshot: S): String

    /** The fingerprint recorded when this edit session began, or null for a new record. */
    val baseFingerprint: String?

    /**
     * The draft row this session owns, or null until the form is first dirtied.
     *
     * Backed by the session's `SavedStateHandle` in every implementation, which is what lets a
     * write survive process death and lets the row be found again rather than duplicated.
     */
    var draftRowId: Int?

    suspend fun upsertDraft(existing: Int?, snapshot: S): Int

    suspend fun deleteDraft(id: Int)
}

/**
 * Keeps a form's Brouillon in step with what the user is doing, without the form having to think
 * about it.
 *
 * Instantiated in a session ViewModel's `init` over that form's state flows. It is inert until
 * [arm] is called, which is the whole reason drafts are not born the moment a form opens: entry
 * (prefilling an edit, hydrating a resumed draft) writes to the same flows this watches, and none
 * of that is the user typing.
 *
 * @param signals every flow whose change means the form's content changed. A snapshot is taken
 *   from the host rather than from these values, so their element types do not matter.
 */
@OptIn(FlowPreview::class)
class DraftAutosave<S>(
    private val host      : DraftAutosaveHost<S>,
    private val scope     : CoroutineScope,
    signals               : List<Flow<*>>,
    debounceMs            : Long = DEFAULT_DEBOUNCE_MS
) {
    private var armed = false

    /**
     * Whether anything has actually changed since [arm].
     *
     * Being armed is not the same as having something to save. A session can be armed over a form
     * that was populated *before* arming — an edit prefill, a hydrated draft, or a record chosen on
     * the page the form was opened from — and none of that is the user entering anything. Without
     * this flag [flush] would happily write such a form out on the way past, which is how a stray
     * tap on "Nouvel achat" from a supplier page used to leave an empty Brouillon behind.
     *
     * Set before the debounce, so a change still counts even if the write itself never ran.
     */
    private var touched = false

    init {
        combine(signals) { host.snapshot() }
            // combine emits the current tuple the instant it is collected — here, during init.
            // The armed filter below would already stop it, but dropping it makes the intent
            // explicit: arming a session must never itself be a reason to write.
            .drop(1)
            .filter { armed }
            .onEach { touched = true }
            .debounce(debounceMs)
            .onEach { persist(it) }
            .launchIn(scope)
    }

    /** Starts honouring changes. Called once the session's own entry work has finished. */
    fun arm() { armed = true }

    /**
     * Stops honouring changes — after a commit, when the row this session owned is already gone.
     */
    fun disarm() { armed = false }

    /**
     * Writes the current form now instead of waiting out the debounce window. Called on
     * `Lifecycle.Event.ON_STOP`, which is the real durability point: `debounce` drops its tail if
     * the process dies inside the window. ON_STOP is not guaranteed on a hard kill, but it covers
     * home-then-killed, which is nearly all of it.
     *
     * The in-flight debounced write is not cancelled — [persist] is idempotent, so a later
     * duplicate is harmless.
     *
     * Does nothing for a form nobody has touched since [arm] — see [touched].
     */
    fun flush() {
        if (!armed || !touched) return
        scope.launch { persist(host.snapshot()) }
    }

    private suspend fun persist(snap: S) {
        val dirty = if (host.isEdit(snap)) {
            // An edit form is prefilled, so it is non-empty from its first emission and the
            // emptiness rule below would fire on sight. Comparing against the base recorded at
            // edit-start answers the question that actually matters: has anything changed yet?
            host.fingerprintOf(snap) != host.baseFingerprint
        } else {
            !host.isEmpty(snap)
        }

        val existing = host.draftRowId
        if (dirty) {
            host.draftRowId = host.upsertDraft(existing, snap)
        } else if (existing != null) {
            // Edited, then undone back to the original values — leave nothing behind.
            host.deleteDraft(existing)
            host.draftRowId = null
        }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 500L
    }
}
