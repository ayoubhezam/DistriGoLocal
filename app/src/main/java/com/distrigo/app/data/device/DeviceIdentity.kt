package com.distrigo.app.data.device

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * Who this installation is: a random (version 4) UUID, created the first time it is asked for and
 * the same every time after, until the app is uninstalled or its data cleared.
 *
 * ### Why not in the database or SharedPreferences
 *
 * Both are backed up. With `allowBackup` on, Auto Backup restores the database and preferences onto
 * a new phone, and a device-to-device transfer copies them — so an id stored there would be cloned,
 * and two phones would carry the same identity into a sync. `noBackupFilesDir` is the one place
 * Android leaves out of both. A restored or transferred app therefore gets a new id, which is exactly
 * what it is: a new device, holding a copy of another device's data.
 *
 * The database records which id last opened it (`app_meta.device_id`), so that copy can be told apart
 * from the original.
 */
object DeviceIdentity {

    private const val FILE_NAME = "device_id"

    @Volatile
    private var cached: String? = null

    /** This installation's id. Does file I/O on first call; call it off the main thread. */
    fun id(context: Context): String =
        cached ?: synchronized(this) {
            cached ?: readOrCreate(context.applicationContext.noBackupFilesDir).also { cached = it }
        }

    /**
     * The id stored in [dir], creating it if there is none or what is there is not a UUID.
     *
     * Written to a temporary file and renamed into place, so a kill mid-write leaves either no id or a
     * whole one — never a truncated id that would be read back as a different device.
     */
    internal fun readOrCreate(dir: File): String {
        val file = File(dir, FILE_NAME)
        file.takeIf { it.isFile }?.readText()?.trim()?.let { stored ->
            if (runCatching { UUID.fromString(stored) }.isSuccess) return stored
        }
        dir.mkdirs()
        val id = UUID.randomUUID().toString()
        val temp = File(dir, "$FILE_NAME.tmp")
        temp.writeText(id)
        if (!temp.renameTo(file)) {
            file.delete()
            check(temp.renameTo(file)) { "Could not store the device id in $dir" }
        }
        return id
    }
}
