package com.distrigo.app.data

import android.content.Context
import android.net.Uri
import com.distrigo.app.ui.common.ImageCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BusinessSettingsStore {
    private const val PREFS_NAME = "business_settings"
    private const val KEY_NAME = "business_name"
    private const val LOGO_FILENAME = "business_logo.jpg"
    private const val KEY_INCLUDE_CHARGES_IN_REPORT = "include_charges_in_report"
    private const val KEY_PHONE = "business_phone"

    fun getBusinessName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() } ?: "DISTRIGO"
    }

    fun saveBusinessName(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_NAME, name).apply()
    }

    /**
     * The number printed on receipts. Null rather than a placeholder when unset, so the receipt
     * decides how to render its absence (it shows "-") instead of inheriting one from here.
     */
    fun getBusinessPhone(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PHONE, null)?.takeIf { it.isNotBlank() }
    }

    fun saveBusinessPhone(context: Context, phone: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PHONE, phone).apply()
    }

    fun getLogoFile(context: Context): File? {
        val file = File(context.filesDir, LOGO_FILENAME)
        return if (file.exists()) file else null
    }

    /**
     * Stores the picked image as the business logo and returns its file, or null if it could not be
     * read or written, in which case the previous logo is left as it was.
     *
     * The image goes through [ImageCapture.compressLogoFromUri], off the main thread: at most 1024 px
     * on its longest edge, and PNG when it has transparency. It used to be copied byte for byte from
     * the picker, on the main thread, so a camera photo chosen as the logo was stored, and then
     * decoded, whole. The file keeps its name whatever the format inside: decoders read the content,
     * and a new name would strand logos already saved.
     */
    suspend fun saveLogo(context: Context, sourceUri: Uri): File? = withContext(Dispatchers.IO) {
        val bytes = ImageCapture.compressLogoFromUri(context, sourceUri) ?: return@withContext null
        val destFile = File(context.filesDir, LOGO_FILENAME)
        val tmpFile = File(context.filesDir, "$LOGO_FILENAME.tmp")
        try {
            // Written beside the logo and renamed over it, so a failed write never leaves half a file.
            tmpFile.writeBytes(bytes)
            if (tmpFile.renameTo(destFile)) destFile else null
        } catch (e: Exception) {
            null
        } finally {
            if (tmpFile.exists()) tmpFile.delete()
        }
    }

    fun clearLogo(context: Context) {
        File(context.filesDir, LOGO_FILENAME).let { if (it.exists()) it.delete() }
    }

    fun getIncludeChargesInReport(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_INCLUDE_CHARGES_IN_REPORT, true)
    }

    fun saveIncludeChargesInReport(context: Context, include: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_INCLUDE_CHARGES_IN_REPORT, include).apply()
    }
}