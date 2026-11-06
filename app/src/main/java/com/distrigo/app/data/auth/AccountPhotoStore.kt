package com.distrigo.app.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * The account's profile picture, on this phone only: `no_backup/account_photo/<userId>-<hash>.jpg`.
 *
 * Kept with the account it was taken for, like [com.distrigo.app.data.license.BusinessProfileStore]: another account
 * signed in here never sees it, and the same account signing back in finds it again. It never leaves the phone: the
 * server does not receive it, and `no_backup` keeps it out of Android's backup, as the session is.
 *
 * Named by its content, so a new picture is a new path: Coil cannot serve the old one from its cache, and
 * [com.distrigo.app.data.image.StoredImageKeyer] keys it by path without reading its date on the main thread.
 * One file per account: saving removes the one it replaces.
 */
class AccountPhotoStore(private val dir: File) {

    data class Saved(val userId: String, val file: File)

    private val _saved = MutableStateFlow<Saved?>(null)
    /** The picture [load] found or [save] wrote; null until then, or when the account has none. */
    val saved: StateFlow<Saved?> = _saved.asStateFlow()

    /** The account's picture, if it has one. File I/O: off the main thread. */
    fun load(userId: String): File? = synchronized(LOCK) {
        // Two only after a kill between writing the new one and removing the old: the newer is the picture
        val file = filesOf(userId).maxByOrNull { it.lastModified() }
        _saved.value = file?.let { Saved(userId, it) }
        file
    }

    /** Writes [jpeg] as the account's picture, replacing the one it had; null when it could not be written. */
    fun save(userId: String, jpeg: ByteArray): File? = synchronized(LOCK) {
        if (!isSafeId(userId) || jpeg.isEmpty()) return null
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val target = File(dir, "$userId-${sha256(jpeg).take(HASH_CHARS)}$EXTENSION")
        if (!(target.isFile && target.length() == jpeg.size.toLong())) {
            val temp = try {
                File.createTempFile("photo", ".tmp", dir)
            } catch (e: IOException) {
                return null
            }
            try {
                temp.writeBytes(jpeg)
                // Whole or not at all: a kill mid-write keeps the previous picture
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: Exception) {
                return null
            } finally {
                temp.delete()
            }
        }
        filesOf(userId).filter { it != target }.forEach { it.delete() }
        _saved.value = Saved(userId, target)
        target
    }

    /** « Supprimer la photo », and the account deleted. */
    fun delete(userId: String) {
        synchronized(LOCK) {
            filesOf(userId).forEach { it.delete() }
            if (_saved.value?.userId == userId) _saved.value = null
        }
    }

    /** This account's files, and only its own: the name is the id, a dash, the hash and the extension, nothing else. */
    private fun filesOf(userId: String): List<File> {
        if (!isSafeId(userId)) return emptyList()
        val name = Regex(Regex.escape(userId) + "-[0-9a-f]{$HASH_CHARS}" + Regex.escape(EXTENSION))
        return dir.listFiles()?.filter { it.isFile && name.matches(it.name) }.orEmpty()
    }

    companion object {
        private val LOCK = Any()
        private const val DIR_NAME = "account_photo"
        private const val EXTENSION = ".jpg"
        private const val HASH_CHARS = 16

        /** An id goes into a file name: letters, digits and dashes only (Supabase ids are UUIDs), never a path. */
        internal fun isSafeId(userId: String): Boolean =
            userId.isNotEmpty() && userId.length <= 64 && userId.all { it.isLetterOrDigit() && it.code < 128 || it == '-' }

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        fun dirFor(context: android.content.Context): File =
            File(context.applicationContext.noBackupFilesDir, DIR_NAME)

        fun forApp(context: android.content.Context) = AccountPhotoStore(dirFor(context))
    }
}
