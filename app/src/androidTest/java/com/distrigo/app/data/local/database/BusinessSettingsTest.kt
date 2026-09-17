package com.distrigo.app.data.local.database

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.distrigo.app.data.image.ImageStore
import com.distrigo.app.data.local.entity.BUSINESS_SETTINGS_UUID
import com.distrigo.app.data.model.BusinessSettings
import com.distrigo.app.data.repository.BusinessSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The business identity lives in its database row, arrives there once from the old preferences and
 * logo file, and every edit to it is tracked.
 *
 * Sandboxed: the repository reads its own preferences file and writes photos under a temporary files
 * directory, so the app's real settings and photos are never read, imported or removed.
 */
@RunWith(AndroidJUnit4::class)
class BusinessSettingsTest {

    private val base: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefsName = "business_settings_test_${System.nanoTime()}"
    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private lateinit var repository: BusinessSettingsRepository

    /** The app's context, but with a private files directory. */
    private class Sandbox(base: Context, private val files: File) : ContextWrapper(base) {
        override fun getFilesDir(): File = files
        override fun getApplicationContext(): Context = this
    }

    @Before
    fun open() {
        filesDir = File(base.cacheDir, "business-settings-test-${System.nanoTime()}").apply { mkdirs() }
        context = Sandbox(base, filesDir)
        db = Room.inMemoryDatabaseBuilder(base, AppDatabase::class.java).withChangeTracking().build()
        sql = db.openHelper.writableDatabase
        repository = BusinessSettingsRepository(db, context, prefsName, File(filesDir, "business_logo.jpg"))
    }

    @After
    fun close() {
        db.close()
        base.deleteSharedPreferences(prefsName)
        filesDir.deleteRecursively()
    }

    @Test
    fun aFreshInstallGetsTheDefaults() = runBlocking {
        val settings = repository.get()

        assertEquals(BusinessSettings.DEFAULT_NAME, settings.name)
        assertNull(settings.phone)
        assertNull(settings.logoPath)
        assertEquals(BUSINESS_SETTINGS_UUID, sql.text("SELECT uuid FROM business_settings WHERE id = 1"))
        assertEquals(1L, sql.long("SELECT COUNT(*) FROM business_settings"))
    }

    /** What was in the preferences and the logo file moves into the row once, and the old copies go. */
    @Test
    fun theOldSettingsAreImportedOnceAndRemoved() = runBlocking {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().putString("business_name", "Épicerie Centrale").putString("business_phone", "0555123456")
            .putBoolean("include_charges_in_report", false).commit()
        val logoBytes = "a logo".toByteArray()
        val legacyLogo = File(filesDir, "business_logo.jpg").apply { writeBytes(logoBytes) }

        val settings = repository.get()

        assertEquals("Épicerie Centrale", settings.name)
        assertEquals("0555123456", settings.phone)
        assertTrue(settings.logoRef.orEmpty(), ImageStore.isStoredRef(settings.logoRef))
        assertTrue(File(settings.logoPath!!).readBytes().contentEquals(logoBytes))
        assertTrue(prefs.all.isEmpty())
        assertFalse(legacyLogo.exists())

        // Once the row exists the old storage is never read again.
        prefs.edit().putString("business_name", "Autre nom").commit()
        assertEquals("Épicerie Centrale", repository.get().name)
    }

    /** Saving is an edit like any other: stamped and versioned, a blank name stored as unset. */
    @Test
    fun savingTheIdentityIsATrackedEdit() = runBlocking {
        repository.get()
        val before = sql.long("SELECT updated_at FROM business_settings")
        sql.execSQL("UPDATE business_settings SET updated_at = 1000")

        repository.saveIdentity("  ", " 0661 22 33 44 ")

        val settings = repository.get()
        assertEquals(BusinessSettings.DEFAULT_NAME, settings.name)
        assertEquals("0661 22 33 44", settings.phone)
        assertTrue(sql.text("SELECT COALESCE(business_name, 'null') FROM business_settings") == "null")
        assertEquals(2L, sql.long("SELECT version FROM business_settings"))
        assertTrue(sql.long("SELECT updated_at FROM business_settings") > 1000 && before > 0)
    }

    @Test
    fun aNewLogoIsStoredAndTheSameOneChangesNothing() = runBlocking {
        val bytes = "another logo".toByteArray()

        assertTrue(repository.saveLogoBytes(bytes))
        val first = repository.get()
        assertTrue(File(first.logoPath!!).readBytes().contentEquals(bytes))
        val version = sql.long("SELECT version FROM business_settings")

        assertTrue(repository.saveLogoBytes(bytes))
        assertEquals(first.logoRef, repository.get().logoRef)
        assertEquals(version, sql.long("SELECT version FROM business_settings"))
    }

    /** A logo whose file is not on this device — a restored database without its photos — prints none. */
    @Test
    fun aLogoWithoutItsFileHasNoPath() = runBlocking {
        repository.get()
        sql.execSQL("UPDATE business_settings SET logo_ref = 'img:${"0".repeat(64)}'")
        val settings = repository.get()
        assertEquals("img:${"0".repeat(64)}", settings.logoRef)
        assertNull(settings.logoPath)
    }

    @Test
    fun observersSeeEachChange() = runBlocking {
        assertEquals(BusinessSettings.DEFAULT_NAME, repository.observe().first().name)
        repository.saveIdentity("Épicerie Centrale", "")
        val changed = withTimeout(5_000) { repository.observe().first { it.name == "Épicerie Centrale" } }
        assertNull(changed.phone)
    }

    private fun SupportSQLiteDatabase.long(query: String): Long =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getLong(0) }

    private fun SupportSQLiteDatabase.text(query: String): String =
        query(query).use { assertTrue(query, it.moveToFirst()); it.getString(0) }
}
