package com.distrigo.app.ui.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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
        /** Draft rows a save deleted: an upsert naming one writes nothing, as the repositories do (DATA-01). */
        val gone = mutableSetOf<Int>()
        /** How long an upsert takes, for a write still running when the save starts. */
        var writeTime = 0L
        /** When the last upsert finished, on the test clock. */
        var lastWriteDoneAt = -1L
        var clock: () -> Long = { 0L }
        private var nextId = 1
        override var draftRowId: Int? = null
        override val baseFingerprint: String? get() = base
        override fun snapshot() = form.value
        override fun isEdit(snapshot: String) = edit
        override fun isEmpty(snapshot: String) = snapshot.isEmpty()
        override fun fingerprintOf(snapshot: String) = snapshot
        override suspend fun upsertDraft(existing: Int?, snapshot: String): Int? {
            if (writeTime > 0) kotlinx.coroutines.delay(writeTime)
            lastWriteDoneAt = clock()
            if (existing != null && existing in gone) return null
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

    /**
     * DATA-01, seen on the phone: the debounced write was already running when « Confirmer » started the save. It
     * finished after the save deleted the draft and inserted it again. [DraftAutosave.hold] now waits for it.
     */
    @Test
    fun `a write running when the save starts finishes before the save`() = runTest {
        val host = Host().apply { writeTime = 300; clock = { testScheduler.currentTime } }
        val autosave = DraftAutosave(host, backgroundScope, listOf(host.form), debounce)
        autosave.arm()
        advanceTimeBy(1)

        host.form.value = "Lait Candia 1L"
        advanceTimeBy(debounce + 50)        // the write has started and takes 300 ms
        val heldAt = async { autosave.hold(); testScheduler.currentTime }
        advanceTimeBy(debounce)             // not advanceUntilIdle: it stops while only backgroundScope work is left

        assertEquals(1, host.upserts.size)
        assert(heldAt.await() >= host.lastWriteDoneAt) { "the save started before the write ended" }
    }

    @Test
    fun `a change waiting in the debounce is dropped by hold and written by release`() = runTest {
        val host = Host()
        val autosave = DraftAutosave(host, backgroundScope, listOf(host.form), debounce)
        autosave.arm()
        advanceTimeBy(1)
        host.draftRowId = 7

        host.form.value = "Lait Candia 1L, 12 cartons"
        advanceTimeBy(debounce / 2)
        autosave.hold()                      // « Confirmer »
        advanceTimeBy(debounce * 4)
        advanceUntilIdle()
        assertEquals(emptyList<Pair<Int?, String>>(), host.upserts)

        autosave.release()                   // the save was refused: the form is still the user's
        advanceTimeBy(1)                     // the write runs in backgroundScope, which advanceUntilIdle leaves alone
        assertEquals(listOf<Pair<Int?, String>>(7 to "Lait Candia 1L, 12 cartons"), host.upserts)
    }

    @Test
    fun `release after the commit writes nothing`() = runTest {
        val host = Host()
        val autosave = DraftAutosave(host, backgroundScope, listOf(host.form), debounce)
        autosave.arm()
        advanceTimeBy(1)
        host.form.value = "Lait"
        advanceTimeBy(debounce + 1)
        advanceUntilIdle()
        autosave.hold()
        autosave.disarm()
        autosave.release()
        advanceUntilIdle()
        assertEquals(1, host.upserts.size)
    }

    @Test
    fun `a draft deleted by the save is not written again`() = runTest {
        val host = Host()
        val autosave = DraftAutosave(host, backgroundScope, listOf(host.form), debounce)
        autosave.arm()
        advanceTimeBy(1)
        host.draftRowId = 7
        host.gone += 7                       // the sale's transaction deleted it

        host.form.value = "Lait"
        advanceTimeBy(debounce + 1)
        host.form.value = "Lait, 2"
        advanceTimeBy(debounce + 1)
        advanceUntilIdle()

        assertEquals(emptyList<Pair<Int?, String>>(), host.upserts)
        assertEquals(7, host.draftRowId)     // kept, so no later write inserts a new one
    }
}
