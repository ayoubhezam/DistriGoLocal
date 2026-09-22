package com.distrigo.app.data.repository

import android.content.Context
import android.net.Uri
import com.distrigo.app.data.image.ImageStore
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.data.local.entity.BusinessSettingsEntity
import com.distrigo.app.data.model.BusinessSettings
import com.distrigo.app.ui.common.ImageCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The business identity receipts print, kept in the `business_settings` row.
 *
 * ### Moving in from SharedPreferences
 *
 * Until now the name and phone were preferences ([prefsName]) and the logo a loose file
 * ([legacyLogoFile]). The first time settings are read after the upgrade, [importLegacyIfNeeded] copies
 * them into the row and then removes the old copies — the logo into the ImageStore, as a reference —
 * so there is only ever one place to read. It runs once: once the row exists, the old storage is never
 * consulted again. The report-charges preference, which nothing ever read, is not carried over.
 *
 * The parameters exist so tests can import from their own preferences and files instead of the app's.
 */
class BusinessSettingsRepository(
    private val db: AppDatabase,
    private val context: Context,
    private val prefsName: String = "business_settings",
    private val legacyLogoFile: File = File(context.filesDir, "business_logo.jpg"),
) {
    private val dao = db.businessSettingsDao()
    private val importLock = Mutex()

    /** The settings, and every change to them. Creates the row, importing the old settings, if there is none. */
    fun observe(): Flow<BusinessSettings> = flow {
        importLegacyIfNeeded()
        emitAll(dao.observe().map { it.toModel() })
    }

    suspend fun get(): BusinessSettings {
        importLegacyIfNeeded()
        return dao.get().toModel()
    }

    suspend fun saveIdentity(name: String, phone: String) {
        importLegacyIfNeeded()
        dao.updateIdentity(name.trim().ifBlank { null }, phone.trim().ifBlank { null })
    }

    /**
     * Downscales the picked image, stores it and makes it the logo. False if it could not be read or
     * written, in which case the current logo stays.
     */
    suspend fun saveLogo(uri: Uri): Boolean {
        val bytes = ImageCapture.compressLogoFromUri(context, uri) ?: return false
        return saveLogoBytes(bytes)
    }

    /**
     * Clears the logo, so receipts print without one.
     *
     * Only the reference is dropped; the stored image is left alone. The ImageStore keys by content
     * hash and is shared with every product, client and supplier photo, so the same bytes may well be
     * some other row's picture — deleting the file to tidy up here is how an unrelated photo goes
     * missing. An orphaned blob costs a few kilobytes; a vanished product photo costs a support call.
     */
    suspend fun removeLogo() {
        importLegacyIfNeeded()
        dao.updateLogo(null)
    }

    internal suspend fun saveLogoBytes(bytes: ByteArray): Boolean {
        importLegacyIfNeeded()
        val ref = withContext(Dispatchers.IO) { ImageStore.put(context, bytes) } ?: return false
        dao.updateLogo(ref)
        return true
    }

    internal suspend fun importLegacyIfNeeded() = importLock.withLock {
        if (dao.get() != null) return@withLock
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val logoRef = legacyLogoFile.takeIf { it.isFile }
                ?.let { runCatching { it.readBytes() }.getOrNull() }
                ?.let { ImageStore.put(context, it) }

            dao.insertIfAbsent(
                BusinessSettingsEntity(
                    business_name  = prefs.getString("business_name", null)?.takeIf { it.isNotBlank() },
                    business_phone = prefs.getString("business_phone", null)?.takeIf { it.isNotBlank() },
                    logo_ref       = logoRef,
                )
            )
            // Only once the row holds them. A logo that could not be copied keeps its file.
            prefs.edit().clear().commit()
            if (logoRef != null) legacyLogoFile.delete()
        }
    }

    private fun BusinessSettingsEntity?.toModel(): BusinessSettings = BusinessSettings(
        name     = this?.business_name?.takeIf { it.isNotBlank() } ?: BusinessSettings.DEFAULT_NAME,
        phone    = this?.business_phone?.takeIf { it.isNotBlank() },
        logoRef  = this?.logo_ref,
        logoPath = this?.logo_ref?.let { ImageStore.fileFor(context, it) }?.takeIf { it.isFile }?.absolutePath,
    )
}
