package com.distrigo.app.data.backup.cloud

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.auth.KeystoreSessionCipher
import com.distrigo.app.data.backup.BackupCreator
import com.distrigo.app.data.backup.BackupFormat
import com.distrigo.app.data.backup.Preparation
import com.distrigo.app.data.backup.PreparedRestore
import com.distrigo.app.data.backup.RestorePreparer
import com.distrigo.app.data.license.DataBusiness
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.database.withChangeTracking
import com.distrigo.app.data.local.database.withDeviceIdentity
import com.distrigo.app.data.local.database.withMigrationPolicy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.KeyStore
import java.util.UUID

/**
 * Restore on a new phone, on the phone: phone A links with the password (real key derivation, key in the real
 * Keystore) and backs up a real database with a product photo; phone B joins with the recovery code only and backs up
 * the same data. A third and a fourth phone restore A's backup with the password and B's with the recovery code; each
 * goes through the app's own restore checks ([RestorePreparer], in a test folder) and must hold exactly the source
 * data, with the photo back byte for byte. Nothing of the app's own data, keys or restore state is touched.
 */
@RunWith(AndroidJUnit4::class)
class CloudRestoreDeviceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, TEST_DIR)
    private val images = File(root, "images")
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var creator: BackupCreator
    private val cloud = Cloud()
    private val account = "owner@gmail.com"
    private val password = "Tournee2026!"

    /** One Drive account, in memory, shared by every phone. */
    private class Cloud : CloudBackupProvider {
        override val id = "memory"
        val backups = LinkedHashMap<String, Pair<CloudBackupEntry, ByteArray>>()
        val blobs = LinkedHashMap<String, Pair<CloudBlob, ByteArray>>()
        var ring: ByteArray? = null
        private var next = 1

        override suspend fun account() = "owner@gmail.com"
        override suspend fun listBackups(businessId: String) =
            backups.values.map { it.first }.filter { it.meta.businessId == businessId }.sortedByDescending { it.meta.createdAtMillis }
        override suspend fun uploadBackup(
            file: File, meta: CloudBackupMeta, resume: String?, onSession: (String) -> Unit, onProgress: (Long, Long) -> Unit,
        ): CloudBackupEntry {
            val bytes = file.readBytes()
            return CloudBackupEntry("b${next++}", bytes.size.toLong(), null, meta).also { backups[it.id] = it to bytes }
        }
        override suspend fun openBackup(entry: CloudBackupEntry, fromByte: Long): InputStream =
            backups.getValue(entry.id).second.let { ByteArrayInputStream(it, fromByte.toInt(), it.size - fromByte.toInt()) }
        override suspend fun deleteBackup(entry: CloudBackupEntry) {
            backups.remove(entry.id)
        }
        override suspend fun readKeyRing(businessId: String) = ring
        override suspend fun writeKeyRing(businessId: String, bytes: ByteArray) {
            ring = bytes
        }
        override suspend fun listBlobs(businessId: String) = blobs.values.map { it.first }
        override suspend fun putBlob(businessId: String, name: String, file: File) =
            CloudBlob("p${next++}", name, file.length()).also { blobs[it.id] = it to file.readBytes() }
        override suspend fun openBlob(blob: CloudBlob): InputStream = ByteArrayInputStream(blobs.getValue(blob.id).second)
        override suspend fun deleteBlob(blob: CloudBlob) {
            blobs.remove(blob.id)
        }
    }

    private val business = object : DataBusiness {
        override fun read() = BUSINESS
        override fun bind(businessId: String) = Unit
    }

    /** A phone that backs up the source data: its own key store (test Keystore alias), state and staging. */
    private inner class BackingPhone(name: String) {
        val dir = File(root, name)
        val alias = "distrigo_cloud_restore_devicetest_$name"
        val keys = BackupKeyStore(File(dir, "keys"), KeystoreSessionCipher(alias))
        val store = CloudBackupStore(File(dir, "state"))
        val link = CloudBackupLink(store, keys, { cloud })
        val engine = CloudBackupEngine(
            store, keys, { cloud }, AppCloudBackupSource(db, creator, business, images), File(dir, "staging"), "9.9 (99)",
            CloudPhotoIndex(File(dir, "index")),
        )
    }

    /** A new phone: nothing but the account and a secret. */
    private inner class NewPhone(name: String) {
        val dir = File(root, name)
        val images = File(dir, "images")
        val restore = CloudRestore({ cloud }, File(dir, "restore"), images)
        val preparer = RestorePreparer(context, context.contentResolver, File(dir, "prepare"), { "device-$name" })
    }

    @Before
    fun setUp() {
        cleanUp()
        db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .withMigrationPolicy()
            .withChangeTracking()
            .withDeviceIdentity { "7e57de71-0000-4000-8000-000000000030" }
            .build()
        sql = db.openHelper.writableDatabase
        creator = BackupCreator(db, context.getDatabasePath(TEST_DB), images, File(root, "work"), context.contentResolver, "9.9 (99)", "Test Phone")
    }

    @After
    fun cleanUp() {
        if (::db.isInitialized && db.isOpen) db.close()
        context.deleteDatabase(TEST_DB)
        root.deleteRecursively()
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        listOf("phone-a", "phone-b").forEach { keystore.deleteEntry("distrigo_cloud_restore_devicetest_$it") }
    }

    private fun product(name: String, price: Double, image: String? = null) = sql.execSQL(
        "INSERT INTO products (name, selling_price, purchase_price, stock, min_stock, unit_type, packages, pack_size, " +
            "has_expiry, camion_stock, image_uri, uuid) VALUES (?, ?, 100.0, 12, 0, 'piece', 0, 1, 0, 0, ?, ?)",
        arrayOf(name, price, image, UUID.randomUUID().toString()),
    )

    /** The products, every column that is data (not the row id), in a stable order. */
    private fun products(database: File): List<String> =
        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY).use { read ->
            read.rawQuery("SELECT uuid, name, selling_price, purchase_price, stock, image_uri FROM products ORDER BY uuid", null).use { c ->
                buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { c.getString(it) ?: "null" }) }
            }
        }

    /** Restores [entry] on [phone] with [secret], through the app's own restore checks; returns what it would install. */
    private fun restoreOn(phone: NewPhone, entry: CloudBackupEntry, secret: String): PreparedRestore = runBlocking {
        val started = System.nanoTime()
        val ring = phone.restore.keyRing(account, BUSINESS)
        val key = phone.restore.unlock(ring, secret)!!
        val file = phone.restore.download(account, entry, key)
        val photos = phone.restore.fetchPhotos(account, BUSINESS, key)
        assertEquals(PhotoFetch(fetched = 1, alreadyHere = 0, rejected = 0), photos)
        val prepared = (phone.preparer.prepare(Uri.fromFile(file)) as Preparation.Ready).restore
        Log.i(TAG, "restore with ${if (secret == password) "password" else "recovery code"}: ${(System.nanoTime() - started) / 1_000_000} ms")
        prepared
    }

    @Test
    fun backupsMadeWithThePasswordAndWithTheRecoveryCodeRestoreToTheSameData() = runBlocking {
        val photoBytes = ByteArray(150_000) { (it * 7 % 251).toByte() }
        val photoHash = BackupFormat.sha256(photoBytes.inputStream())
        images.mkdirs()
        File(images, "$photoHash.jpg").writeBytes(photoBytes)
        product("Lait Candia 1L", 120.0, "img:$photoHash")
        product("Café Bonal 250g", 340.0)
        product("Yaourt Soummam", 35.0)
        val source = products(context.getDatabasePath(TEST_DB).also { sql.query("PRAGMA wal_checkpoint(FULL)").close() })
        assertEquals(3, source.size)

        // Phone A links with the password and backs up.
        val phoneA = BackingPhone("phone-a")
        val code = phoneA.link.createKey(account, BUSINESS, password)
        val byPassword = (phoneA.engine.run() as CloudBackupOutcome.Saved).entry

        // A new phone restores it with the password.
        val restoredByPassword = restoreOn(NewPhone("phone-c"), byPassword, password)

        // Phone B joins with the recovery code only, and backs up the same data.
        val phoneB = BackingPhone("phone-b")
        assertTrue(phoneB.link.join(account, phoneB.link.existingRing(account, BUSINESS)!!, code))
        val byCode = (phoneB.engine.run(force = true) as CloudBackupOutcome.Saved).entry
        assertEquals("both under the one key", byPassword.meta.keyId, byCode.meta.keyId)

        // Another new phone restores it with the recovery code.
        val phoneD = NewPhone("phone-d")
        val restoredByCode = restoreOn(phoneD, byCode, code)

        assertEquals("password backup: the source data", source, products(restoredByPassword.database))
        assertEquals("recovery-code backup: the source data", source, products(restoredByCode.database))
        assertEquals(restoredByPassword.manifest.rowCounts, restoredByCode.manifest.rowCounts)
        assertFalse(restoredByCode.manifest.photosIncluded)
        assertArrayEquals("the photo, byte for byte", photoBytes, File(phoneD.images, "$photoHash.jpg").readBytes())
    }

    private companion object {
        const val TAG = "CloudRestoreDeviceTest"
        const val TEST_DB = "cloud-restore-device-test"
        const val TEST_DIR = "cloud-restore-device-test"
        const val BUSINESS = "biz-restore-test"
    }
}
