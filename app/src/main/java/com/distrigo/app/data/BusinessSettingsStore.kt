package com.distrigo.app.data

import android.content.Context
import android.net.Uri
import java.io.File

object BusinessSettingsStore {
    private const val PREFS_NAME = "business_settings"
    private const val KEY_NAME = "business_name"
    private const val LOGO_FILENAME = "business_logo.jpg"

    fun getBusinessName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() } ?: "DISTRIGO"
    }

    fun saveBusinessName(context: Context, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_NAME, name).apply()
    }

    fun getLogoFile(context: Context): File? {
        val file = File(context.filesDir, LOGO_FILENAME)
        return if (file.exists()) file else null
    }

    fun saveLogo(context: Context, sourceUri: Uri): File {
        val destFile = File(context.filesDir, LOGO_FILENAME)
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        return destFile
    }

    fun clearLogo(context: Context) {
        File(context.filesDir, LOGO_FILENAME).let { if (it.exists()) it.delete() }
    }
}