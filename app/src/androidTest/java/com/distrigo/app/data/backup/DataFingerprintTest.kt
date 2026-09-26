package com.distrigo.app.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.database.withDeviceIdentity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * The fingerprint changes whenever the data does, and only then. On an in-memory database with the app's
 * triggers, so each change is stamped the way the app stamps it.
 */
@RunWith(AndroidJUnit4::class)
class DataFingerprintTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .withChangeTracking()
            .withDeviceIdentity { "7e57de71-0000-4000-8000-000000000008" }
            .build()
        sql = db.openHelper.writableDatabase
    }

    @After
    fun close() {
        db.close()
    }

    private fun fingerprint() = DataFingerprint.of(sql)

    private fun product(name: String): String {
        val uuid = UUID.randomUUID().toString()
        sql.execSQL(
            "INSERT INTO products (name, selling_price, purchase_price, stock, min_stock, unit_type, packages, pack_size, " +
                "has_expiry, camion_stock, uuid) VALUES (?, 120.0, 100.0, 0, 0, 'piece', 0, 1, 0, 0, ?)",
            arrayOf(name, uuid)
        )
        return uuid
    }

    /** Asserts [change] moves the fingerprint, and returns the new one. */
    private fun assertChanges(what: String, change: () -> Unit): String {
        val before = fingerprint()
        Thread.sleep(2) // the triggers stamp milliseconds
        change()
        val after = fingerprint()
        assertNotEquals(what, before, after)
        return after
    }

    @Test
    fun theSameDataGivesTheSameFingerprint() {
        product("Lait Candia 1L")
        val first = fingerprint()
        assertEquals(first, fingerprint())
        assertEquals(64, first.length)
    }

    @Test
    fun insertingEditingAndDeletingEachChangeIt() {
        val uuid = product("Lait Candia 1L")
        assertChanges("an insert") { product("Yaourt Soummam") }
        assertChanges("an edit") { sql.execSQL("UPDATE products SET selling_price = 130.0 WHERE uuid = ?", arrayOf(uuid)) }
        assertChanges("a soft delete") { sql.execSQL("UPDATE products SET deleted_at = 1789600000000 WHERE uuid = ?", arrayOf(uuid)) }
        assertChanges("a delete") { sql.execSQL("DELETE FROM products WHERE uuid = ?", arrayOf(uuid)) }
    }

    /** One row deleted and another added between two backups: the count is the same, the fingerprint is not. */
    @Test
    fun aDeleteAndAnInsertTogetherStillChangeIt() {
        product("Lait Candia 1L")
        val last = product("Yaourt Soummam")
        assertChanges("a row replaced by another") {
            sql.execSQL("DELETE FROM products WHERE uuid = ?", arrayOf(last))
            product("Fromage Président")
        }
    }

    /** An edit stamped with an earlier time than the newest row, as after the phone's clock is set back. */
    @Test
    fun anEditStampedInThePastStillChangesIt() {
        val first = product("Lait Candia 1L")
        product("Yaourt Soummam")
        assertChanges("an edit dated before the newest row") {
            sql.execSQL("UPDATE products SET updated_at = 1000 WHERE uuid = ?", arrayOf(first))
        }
    }

    @Test
    fun stockMovementsDraftsAndCountersChangeIt() {
        product("Lait Candia 1L")
        assertChanges("a stock movement") {
            sql.execSQL(
                "INSERT INTO stock_movements (product_id, product_name, type, direction, quantity, emplacement, source_label, " +
                    "source_type, source_id, total_value, created_at, uuid) VALUES (1, 'Lait Candia 1L', 'vente', 'sortie', 1.0, " +
                    "'depot', 'test', 'vente', 1, 0.0, '2026-09-18T08:00:00Z', 'u-movement')"
            )
        }
        assertChanges("a draft") {
            sql.execSQL(
                "INSERT INTO purchase_drafts (items_json, note, montant_paye, item_count, total, last_step, created_at, updated_at) " +
                    "VALUES ('[]', '', '0', 0, 0, 'items', '2026-09-18T08:00:00Z', '2026-09-18T08:00:00Z')"
            )
        }
        assertChanges("a draft edited") {
            sql.execSQL("UPDATE purchase_drafts SET items_json = '[1]', updated_at = '2026-09-18T08:05:00Z'")
        }
        assertChanges("a document counter") {
            sql.execSQL("INSERT OR REPLACE INTO app_meta (key, value) VALUES ('numbering.ventes', '12')")
        }
    }

    /** Recording a backup or a restore is not a change to the data. */
    @Test
    fun backupAndRestoreRecordsDoNotChangeIt() {
        product("Lait Candia 1L")
        val before = fingerprint()
        sql.execSQL(
            "INSERT OR REPLACE INTO app_meta (key, value) VALUES " +
                "('${BackupCreator.KEY_LAST_AT}', '2026-09-18T03:00:00Z'), ('${BackupCreator.KEY_LAST_NAME}', 'DistriGo-auto.distrigo'), " +
                "('${BackupCreator.KEY_LAST_SIZE}', '317405'), ('${RestoreInstaller.KEY_RESTORED_AT}', '2026-09-18T03:00:00Z')"
        )
        assertEquals(before, fingerprint())
    }

    /** The app hands WorkManager Hilt's worker factory, so automatic backup workers get their dependencies. */
    @Test
    fun workManagerIsConfiguredWithHiltsWorkerFactory() {
        val app = context.applicationContext
        assertTrue(app is Configuration.Provider)
        assertTrue((app as Configuration.Provider).workManagerConfiguration.workerFactory is HiltWorkerFactory)
    }
}
