package com.distrigo.app.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.device.DeviceIdentity
import com.distrigo.app.data.repository.ProductRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The database knows which installation has it open, and each standalone row which one created it.
 *
 * A named file database rather than an in-memory one, so it can be closed and opened again by a
 * different "device". It is not the app's: [TEST_DB] is deleted before and after every test. The
 * device ids are fixed strings, and [DeviceIdentity] is only exercised on temporary directories, so
 * nothing here touches the app's own id either.
 */
@RunWith(AndroidJUnit4::class)
class DeviceTrackingTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase

    @Before
    fun startClean() {
        assertNotEquals("distrigo", TEST_DB)
        deleteTestDatabase()
    }

    @After
    fun deleteTestDatabase() {
        if (::db.isInitialized && db.isOpen) db.close()
        context.deleteDatabase(TEST_DB)
        File(context.getDatabasePath(TEST_DB).path + ".lck").delete()
    }

    private fun open(device: String) {
        db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .withMigrationPolicy()
            .withChangeTracking()
            .withDeviceIdentity { device }
            .build()
        sql = db.openHelper.writableDatabase
    }

    @Test
    fun theDatabaseKnowsItsIdAndWhoOpenedIt() {
        open(DEVICE_A)
        val databaseId = meta("database_id")!!
        assertTrue(databaseId, V4_UUID.matches(databaseId))
        assertEquals(DEVICE_A, meta("device_id"))
        db.close()

        // The same file opened by another installation: a copy. It keeps its id, and says who has it now.
        open(DEVICE_B)
        assertEquals(databaseId, meta("database_id"))
        assertEquals(DEVICE_B, meta("device_id"))
    }

    @Test
    fun newStandaloneRowsAreStampedWithTheirDevice() = runBlocking {
        open(DEVICE_A)
        val repository = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
        val product = repository.addProduct(mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0, "stock" to 40.0)).newId()
        val client = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()
        repository.createVente(
            client, null, "depot",
            listOf(mapOf("product_id" to product, "quantity" to 2.0, "unit_price" to 110.0)), null, 0.0
        )

        assertEquals(DEVICE_A, sql.text("SELECT origin_device_id FROM products WHERE id = $product"))
        assertEquals(DEVICE_A, sql.text("SELECT origin_device_id FROM clients WHERE id = $client"))
        assertEquals(DEVICE_A, sql.text("SELECT origin_device_id FROM ventes WHERE client_id = $client"))
        // Being stamped is not an edit.
        assertEquals(1L, sql.long("SELECT version FROM clients WHERE id = $client"))
        assertEquals(1L, sql.long("SELECT version FROM products WHERE id = $product"))
    }

    /** A row that arrives with an origin — as a synced row will — keeps it. */
    @Test
    fun anExplicitOriginIsKept() {
        open(DEVICE_A)
        sql.execSQL(
            "INSERT INTO clients (name, balance, customer_type, uuid, origin_device_id) " +
                "VALUES ('Supérette Nour', 0.0, 'retail', 'u-remote', '$DEVICE_B')"
        )
        assertEquals(DEVICE_B, sql.text("SELECT origin_device_id FROM clients WHERE uuid = 'u-remote'"))
    }

    @Test
    fun originTriggersSitOnTheVersionedTables() {
        open(DEVICE_A)
        val origins = OriginTriggers.originTables(sql)
        val versioned = UpdatedAtTriggers.trackedTables(sql).filter { "version" in UpdatedAtTriggers.columns(sql, it) }
        assertEquals(25, origins.size)
        assertEquals(versioned, origins)
        for (table in origins) {
            assertEquals(table, OriginTriggers.triggerSql(table), storedTriggerSql(sql, OriginTriggers.triggerName(table)))
        }
    }

    @Test
    fun tombstonesSayWhichDeviceDeleted() = runBlocking {
        open(DEVICE_A)
        val repository = ProductRepository(db.productDao(), db.categoryDao(), db.supplierDao(), db)
        val product = repository.addProduct(mapOf("name" to "Lait Candia 1L", "selling_price" to 110.0)).newId()
        val client = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()
        repository.createVente(
            client, null, "depot",
            listOf(mapOf("product_id" to product, "quantity" to 2.0, "unit_price" to 110.0)), null, 0.0
        )

        repository.deleteVente(sql.long("SELECT id FROM ventes").toInt())

        assertEquals(3L, sql.long("SELECT COUNT(*) FROM tombstones"))
        assertEquals(3L, sql.long("SELECT COUNT(*) FROM tombstones WHERE device_id = '$DEVICE_A'"))
    }

    /** Opened without an identity — as the other tests' in-memory databases are — origins stay unknown. */
    @Test
    fun withoutAnIdentityOriginsStayNull() = runBlocking {
        val memory = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).withChangeTracking().build()
        try {
            val repository = ProductRepository(memory.productDao(), memory.categoryDao(), memory.supplierDao(), memory)
            val client = repository.addClient(mapOf("name" to "Épicerie El Amel")).newId()
            memory.openHelper.writableDatabase
                .query("SELECT origin_device_id FROM clients WHERE id = $client")
                .use { it.moveToFirst(); assertTrue(it.isNull(0)) }
        } finally {
            memory.close()
        }
    }

    @Test
    fun deviceIdentityIsStableAndOnlyOneWholeId() {
        val dir = File(context.cacheDir, "device-identity-test-${System.nanoTime()}")
        try {
            val first = DeviceIdentity.readOrCreate(dir)
            assertTrue(first, V4_UUID.matches(first))
            assertEquals(first, DeviceIdentity.readOrCreate(dir))

            // Another installation's directory is another device.
            val other = File(dir, "other")
            assertNotEquals(first, DeviceIdentity.readOrCreate(other))

            // A damaged file is not read back as some other id: it is replaced by a whole new one.
            File(dir, "device_id").writeText("0f3a9c")
            val replaced = DeviceIdentity.readOrCreate(dir)
            assertTrue(replaced, V4_UUID.matches(replaced))
            assertNotEquals(first, replaced)
            assertEquals(replaced, DeviceIdentity.readOrCreate(dir))
            assertTrue(!File(dir, "device_id.tmp").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun meta(key: String): String? =
        sql.query("SELECT value FROM app_meta WHERE key = ?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun SupportSQLiteDatabase.long(query: String): Long =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getLong(0) }

    private fun SupportSQLiteDatabase.text(query: String): String =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getString(0) }

    private fun Map<String, Any>.newId(): Int = (this["id"] as Number).toInt()

    private companion object {
        const val TEST_DB = "device-tracking-test"
        const val DEVICE_A = "a0000000-0000-4000-8000-00000000000a"
        const val DEVICE_B = "b0000000-0000-4000-8000-00000000000b"
        val V4_UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    }
}
