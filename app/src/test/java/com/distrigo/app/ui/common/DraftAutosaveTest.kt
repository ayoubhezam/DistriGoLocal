package com.distrigo.app.ui.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What autosave writes, and — around the moment a form commits — what it must not.
 *
 * On virtual time: `runTest`'s scheduler runs the debounce, so a test says "200 ms passed" and means
 * it. Real delays here would only measure how fast the collector happened to start.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DraftAutosaveTest {

    private val debounce = 500L

    /** Records what autosave asked of its host. */
    private class Host(private val edit: Boolean = false, private val base: String? = null) : DraftAutosaveHost<String> {
        val form = MutableStateFlow("")
        val upserts = mutableListOf<Pair<Int?, String>>()
        val deletes = mutableListOf<Int>()
        private var nextId = 1
        override var draftRowId: Int? = null
        override val baseFingerprint: String? get() = base
        override fun snapshot() = form.value
        override fun isEdit(snapshot: String) = edit
        override fun isEmpty(snapshot: String) = snapshot.isEmpty()
        override fun fingerprintOf(snapshot: String) = snapshot
        override suspend fun upsertDraft(existing: Int?, snapshot: String): Int {
            upserts += existing to snapshot
            return existing ?: nextId++
        }
        override suspend fun deleteDraft(id: Int) { deletes += id }
    }

    @Test
    fun `a change is written once the debounce passes`() = runTest {
        val host = Host()
        val autosave = DraftAutosave(host, backgroundScope, listOf(host.form), debounce)
        autosave.arm()
        advanceTimeBy(1)   // the collector starts on the first tick, before anything is typed

        host.form.value = "Lait Candia 1L"
        advanceTimeBy(debounce + 1)
        advanceUntilIdle()

        assertEquals(listOf<Pair<Int?, String>>(null to "Lait Candia 1L"), host.upserts)
        assertEquals(1, host.draftRowId)
    }

    /**
     * The bug this test was written for: a change typed just before « Valider » was still inside the
     * debounce window when the bon committed. The commit deleted the draft row and the session
     * disarmed, but the pending write ran anyway — and, finding no row left to update, inserted a
     * second Brouillon holding exactly what had just been validated.
     */
    @Test
    fun `a change still in the debounce window is dropped when the form commits`() = runTest {
        val host = Host()
        val autosave = DraftAutosave(host, backgroundScope, listOf(host.form), debounce)
        autosave.arm()
        advanceTimeBy(1)   // the collector starts on the first tick, before anything is typed
        host.draftRowId = 7

        host.form.value = "Lait Candia 1L, 12 cartons"   // the user's last keystroke...
        advanceTimeBy(debounce / 2)                      // ...still inside the window when
        autosave.disarm()                                // the bon commits and deletes draft 7
        host.draftRowId = null
        advanceTimeBy(debounce * 4)
        advanceUntilIdle()

        assertEquals(emptyList<Pair<Int?, String>>(), host.upserts)
        assertEquals(emptyList<Int>(), host.deletes)
    }

    /** Nor does a later change, from a form still on screen while the committed bon is navigated away from. */
    @Test
    fun `a change after the commit is ignored`() = runTest {
        val host = Host()
        val autosave = DraftAutosave(host, backgroundScope, listOf(host.form), debounce)
        autosave.arm()
        advanceTimeBy(1)   // the collector starts on the first tick, before anything is typed

        autosave.disarm()
        host.form.value = "typed after the commit"
        advanceTimeBy(debounce * 4)
        advanceUntilIdle()

        assertEquals(emptyList<Pair<Int?, String>>(), host.upserts)
    }

    /** A flush after the commit writes nothing either, whatever the form still holds. */
    @Test
    fun `a flush after the commit writes nothing`() = runTest {
        val host = Host()
        val autosave = DraftAutosave(host, backgroundScope, listOf(host.form), debounce)
        autosave.arm()
        advanceTimeBy(1)   // the collector starts on the first tick, before anything is typed
        host.form.value = "Lait Candia 1L"
        advanceTimeBy(debounce + 1)
        advanceUntilIdle()
        val writtenBeforeCommit = host.upserts.toList()

        autosave.disarm()
        host.draftRowId = null
        autosave.flush()
        advanceUntilIdle()

        assertEquals(writtenBeforeCommit, host.upserts)
    }

    /** Until a change arrives, an armed session writes nothing — a form merely opened leaves no Brouillon. */
    @Test
    fun `arming alone writes nothing`() = runTest {
        val host = Host()
        val autosave = DraftAutosave(host, backgroundScope, listOf(host.form), debounce)
        autosave.arm()
        advanceTimeBy(debounce * 4)
        autosave.flush()
        advanceUntilIdle()

        assertEquals(emptyList<Pair<Int?, String>>(), host.upserts)
    }

    /** Undoing an edit back to what the bon holds removes the draft it left behind. */
    @Test
    fun `an edit undone back to its base deletes the draft`() = runTest {
        val host = Host(edit = true, base = "as committed")
        val autosave = DraftAutosave(host, backgroundScope, listOf(host.form), debounce)
        autosave.arm()
        advanceTimeBy(1)   // the collector starts on the first tick, before anything is typed

        host.form.value = "changed"
        advanceTimeBy(debounce + 1)
        advanceUntilIdle()
        assertEquals(1, host.draftRowId)

        host.form.value = "as committed"
        advanceTimeBy(debounce + 1)
        advanceUntilIdle()

        assertEquals(listOf(1), host.deletes)
        assertEquals(null, host.draftRowId)
    }
}
