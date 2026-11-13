package com.distrigo.app.data.image

import java.io.File

/**
 * Deletes the photo files nothing names any more, and the temp files a write killed midway left behind (DB-02, audit
 * 2026-11-09).
 *
 * A file goes only once it has been found unreferenced by two sweeps at least [GRACE_MS] apart. One look is not
 * enough: a photo just taken is on disk before the form that holds it is saved; a cloud backup reads the database
 * first and uploads its photos after; a cloud restore downloads its photos before its database is installed. Each of
 * these names the file within the day, so a file still unreferenced a day later is one no row, draft or backup in
 * progress needs. The `.distrigo` backups and safety copies carry their own photos, and the cloud copies their own
 * blobs, so a file gone from the phone stays in each of them.
 *
 * What it remembers between sweeps — each unreferenced file and when it was first seen so — is in [stateFile].
 */
class ImageSweep(private val imagesDir: File, private val stateFile: File) {

    data class Outcome(val photosDeleted: Int, val tempsDeleted: Int, val waiting: Int)

    /** [referenced]: every hash the database names, read before the folder is listed. */
    fun run(referenced: Set<String>, nowMs: Long): Outcome {
        val files = imagesDir.listFiles().orEmpty()
        var temps = 0
        for (file in files) {
            // put*.tmp (ImageStore.put) and .cloud-*.tmp (a cloud restore): a day old, no write is still on it
            if (file.isFile && file.name.endsWith(".tmp") && file.lastModified() < nowMs - GRACE_MS && file.delete()) temps++
        }

        val seenBefore = readState()
        val seenNow = mutableMapOf<String, Long>()
        var deleted = 0
        for (file in files) {
            val hash = PHOTO.matchEntire(file.name)?.groupValues?.get(1) ?: continue
            if (hash in referenced || !file.isFile) continue
            val since = seenBefore[hash]?.takeIf { it <= nowMs } ?: nowMs
            if (since <= nowMs - GRACE_MS) {
                if (file.delete()) deleted++ else seenNow[hash] = since
            } else {
                seenNow[hash] = since
            }
        }
        writeState(seenNow)
        return Outcome(deleted, temps, seenNow.size)
    }

    private fun readState(): Map<String, Long> = try {
        if (!stateFile.isFile) emptyMap()
        else stateFile.readLines().mapNotNull { line ->
            val (hash, since) = line.split(' ').takeIf { it.size == 2 } ?: return@mapNotNull null
            since.toLongOrNull()?.let { hash to it }
        }.toMap()
    } catch (e: java.io.IOException) {
        emptyMap()
    }

    private fun writeState(seen: Map<String, Long>) {
        stateFile.parentFile?.mkdirs()
        val temp = File(stateFile.parentFile, stateFile.name + ".new")
        temp.writeText(seen.entries.joinToString("") { "${it.key} ${it.value}\n" })
        if (!temp.renameTo(stateFile)) {
            stateFile.delete()
            temp.renameTo(stateFile)
        }
    }

    companion object {
        /** How long a file must stay unreferenced, and a temp file untouched, before it goes. */
        const val GRACE_MS = 24L * 60 * 60 * 1000

        private val PHOTO = Regex("([0-9a-f]{64})\\.jpg")
    }
}
