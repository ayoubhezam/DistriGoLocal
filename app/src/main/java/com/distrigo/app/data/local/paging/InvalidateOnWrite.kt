package com.distrigo.app.data.local.paging

import androidx.paging.PagingSource
import androidx.room.InvalidationTracker
import com.distrigo.app.data.local.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps a hand-written [PagingSource] live, as Room's generated ones are: when any of [tables] is
 * written, from any screen, the source invalidates itself and Paging loads a fresh one.
 *
 * Without it a source that merges several tables by hand shows the pages it loaded for as long as it
 * lives, and every command that changed those tables had to remember to call `refresh()` on the list
 * — the client and supplier ledgers remembered after adding a payment, not after deleting or editing
 * one, so a deleted versement stayed on screen.
 *
 * The observer is registered on the first load, not in the constructor: registering reads the
 * database, which Paging may create sources on the main thread to do. It is removed when the source is
 * invalidated, so a replaced source never outlives its list.
 */
internal class InvalidateOnWrite(
    private val db: AppDatabase,
    tables: Array<String>,
    source: PagingSource<*, *>,
) {
    private val observer = object : InvalidationTracker.Observer(tables) {
        override fun onInvalidated(tables: Set<String>) = source.invalidate()
    }
    private val observing = AtomicBoolean(false)

    init {
        source.registerInvalidatedCallback {
            if (observing.get()) db.invalidationTracker.removeObserver(observer)
        }
    }

    /** Called at the start of every load; registers once. */
    suspend fun start() {
        if (observing.compareAndSet(false, true)) {
            withContext(Dispatchers.IO) { db.invalidationTracker.addObserver(observer) }
        }
    }

    companion object {
        /** Past the row in view by half an initial load, so a refresh keeps the list where it was. */
        fun refreshCount(anchor: Int?, initialLoadSize: Int): Int? =
            anchor?.let { (it + initialLoadSize / 2).coerceAtMost(MAX_REFRESH_ROWS) }

        private const val MAX_REFRESH_ROWS = 5_000
    }
}
