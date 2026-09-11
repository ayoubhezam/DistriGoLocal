package com.distrigo.app.data.image

import android.content.Context
import android.util.Log
import com.distrigo.app.data.local.dao.ImageBackfillDao
import com.distrigo.app.data.local.dao.LegacyImageRow
import com.distrigo.app.data.local.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Moves the base64 payloads already in the database out to [ImageStore], once.
 *
 * ### Why this is not a Room migration
 *
 * A `Migration` runs inside database open, so every one of these decodes and file writes would sit
 * between the user tapping the icon and the first screen appearing, with no way to show progress.
 * Worse, it would not be atomic: SQLite can roll its transaction back, the filesystem cannot, so a
 * failure part-way would leave rows pointing at files that were never finished — or files with no
 * rows. Splitting the two halves is what makes a half-finished state harmless.
 *
 * ### Why a half-finished run is fine
 *
 * The read path takes both forms. A row still holding `data:...` renders exactly as it always did;
 * a row already rewritten to `img:...` renders from disk. So this can stop anywhere — the process
 * dies, the user leaves, the device runs out of space — and the app is correct either way. The next
 * launch picks up from wherever it got to, because "still to do" is expressed as a property of the
 * rows themselves (`LIKE 'data:%'`) rather than as a position saved somewhere.
 *
 * That is also why the completion flag is only an optimisation: it saves seven cheap queries on
 * every subsequent launch. Losing it would cost a pointless walk, not correctness.
 *
 * ### What it does not do
 *
 * It does not run under WorkManager, so it does not survive the app being closed mid-run — it just
 * resumes next launch. For the database sizes this app has today that is the right trade: the work
 * is a few hundred rows and finishes in one sitting. A catalogue where it does not would be the
 * signal to schedule it properly.
 *
 * It also does not delete anything. A payload that will not decode is stepped over and left as it
 * is, still rendering through the legacy path, and counted in the log line at the end.
 */
object ImageBackfill {

    private const val TAG = "ImageBackfill"
    private const val PREFS = "image_backfill"
    private const val KEY_DONE = "done_v1"

    /** Rows per read. Small enough that a batch of payloads is never much heap at once. */
    private const val BATCH = 25

    fun isDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

    private fun markDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DONE, true).apply()
    }

    /**
     * Converts every remaining payload, then records that there is nothing left to do.
     *
     * Safe to call on every launch: it returns immediately once the flag is set, and is a no-op on
     * a database that never held any payloads.
     */
    suspend fun runIfNeeded(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        if (isDone(context)) return@withContext

        val dao = db.imageBackfillDao()
        var converted = 0
        var failed = 0

        try {
            val tables = listOf(
                Table("products", dao::products, dao::setProduct),
                Table("clients", dao::clients, dao::setClient),
                Table("suppliers", dao::suppliers, dao::setSupplier),
                Table("ventes", dao::ventes, dao::setVente),
                Table("purchase_orders", dao::purchaseOrders, dao::setPurchaseOrder),
                Table("inventory_items", dao::inventoryItems, dao::setInventoryItem),
                Table("pertes", dao::pertes, dao::setPerte),
            )
            for (table in tables) {
                val result = convert(context, table)
                converted += result.first
                failed += result.second
            }
            markDone(context)
            if (converted > 0 || failed > 0) {
                Log.i(TAG, "converted $converted payload(s) to files, $failed left as-is")
            }
        } catch (e: Exception) {
            // Deliberately swallowed. The flag stays unset, every row still renders through
            // whichever form it is in, and the next launch tries again.
            Log.w(TAG, "backfill interrupted after $converted row(s); will resume next launch", e)
        }
    }

    private class Table(
        val name: String,
        val read: suspend (Int, Int) -> List<LegacyImageRow>,
        val write: suspend (Int, String) -> Unit
    )

    /** Walks one table by id. Returns converted-count to failed-count. */
    private suspend fun convert(context: Context, table: Table): Pair<Int, Int> {
        var afterId = 0
        var converted = 0
        var failed = 0

        while (true) {
            val rows = table.read(afterId, BATCH)
            if (rows.isEmpty()) break

            for (row in rows) {
                // Stepping the cursor past the row *before* trying it is what guarantees the walk
                // ends: a row that cannot be converted still matches the filter, so a cursor that
                // only advanced on success would fetch it forever.
                afterId = row.id

                val bytes = ImageStore.legacyBytes(row.payload)
                val ref = bytes?.let { ImageStore.put(context, it) }
                if (ref != null) {
                    table.write(row.id, ref)
                    converted++
                } else {
                    failed++
                }
            }
        }
        return converted to failed
    }
}
