package com.distrigo.app.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.File
import java.security.MessageDigest

/**
 * Where entity photos live, now that they no longer live in the database.
 *
 * Until now a product, client or supplier photo was a `data:image/jpeg;base64,...` string stored
 * in the row itself, and copied again into `ventes.client_image_uri`,
 * `purchase_orders.supplier_image_uri`, `inventory_items.product_image_uri` and
 * `pertes.product_image_uri`. A 400px JPEG is ~11 KB, which base64 inflates to ~14 KB on disk and
 * ~29 KB on the heap as a UTF-16 String — per row carrying one. The history tables are unbounded,
 * so that cost grew with every sale rather than with the number of clients.
 *
 * The columns keep their names and their `TEXT` type. What changes is what goes in them.
 *
 * ### Content addressing
 *
 * A file is named for the SHA-256 of its own bytes, and a column holds `img:<hash>`. Three things
 * follow from that, and they are the reason for the choice:
 *
 *  - **The four denormalised copies collapse into one file.** Ten thousand sales to the same
 *    client reference one hash, so the bytes exist once no matter how many rows point at them.
 *  - **Snapshots stay correct without being copies.** A client who replaces their photo gets a new
 *    hash; every past invoice still names the old one, and the old file is still there under its
 *    own name. That is the snapshot behaviour the base64 columns had, without the duplication —
 *    and it cannot be broken by a later edit.
 *  - **Writes are idempotent.** Saving the same image twice is a no-op the second time.
 *
 * The cost is that nothing is ever deleted: a file whose last referring row is gone stays on disk.
 * There are no foreign keys to hang a cascade on yet, and a sweep needs one, so reclaiming them is
 * a later change rather than a correctness problem — but the arithmetic that made it easy to defer
 * has moved. At the 400 px the store shipped with, an orphan cost ~11 KB; at 1024 px it costs six
 * to ten times that, and a few days of ordinary use leaves several behind.
 *
 * ### Durability
 *
 * [put] writes to a temporary file and renames it into place, so a kill mid-write cannot leave a
 * short file sitting under a hash that claims to describe it. Readers therefore never have to
 * verify what they read.
 *
 * Files are excluded from cloud backup (`data_extraction_rules.xml`) while the database is not:
 * a photo can be taken again, a ledger cannot, and shipping the image corpus is what would put the
 * app past the auto-backup quota and silently disable backup altogether.
 */
object ImageStore {

    /** Marks a column value as a reference into this store rather than a legacy payload. */
    const val REF_PREFIX = "img:"

    private const val DIR_NAME = "images"
    private const val EXTENSION = ".jpg"

    /** True for a value this store wrote. */
    fun isStoredRef(value: String?): Boolean =
        value != null && value.startsWith(REF_PREFIX)

    /** True for a value written before images moved to disk — still readable, see the resolver. */
    fun isLegacyDataUri(value: String?): Boolean =
        value != null && value.startsWith("data:")

    fun imagesDir(context: Context): File =
        File(context.filesDir, DIR_NAME)

    /**
     * The file a reference names, or null if [ref] is not one of ours.
     *
     * The file is **not** guaranteed to exist: a restore that brought the database back without
     * the image directory leaves every reference dangling, which is exactly why every caller has
     * to treat a missing file as "no photo" rather than as an error.
     */
    fun fileFor(context: Context, ref: String?): File? {
        if (!isStoredRef(ref)) return null
        val hash = ref!!.removePrefix(REF_PREFIX)
        // A reference is only ever a hex digest; anything else is corrupt and resolves to nothing
        // rather than to a path built out of it.
        if (hash.length != 64 || !hash.all { it in '0'..'9' || it in 'a'..'f' }) return null
        return File(imagesDir(context), hash + EXTENSION)
    }

    /** Whether [ref] names a file that is actually on disk right now. */
    fun exists(context: Context, ref: String?): Boolean =
        fileFor(context, ref)?.isFile == true

    /**
     * Stores [jpeg] and returns the `img:<hash>` reference for it, or null if it could not be
     * written.
     *
     * Callers are expected to be off the main thread already — this does file I/O.
     */
    fun put(context: Context, jpeg: ByteArray): String? {
        val hash = sha256(jpeg)
        val dir = imagesDir(context)
        if (!dir.isDirectory && !dir.mkdirs()) return null

        val target = File(dir, hash + EXTENSION)
        if (target.isFile && target.length() > 0L) return REF_PREFIX + hash

        return try {
            val temp = File.createTempFile("put", ".tmp", dir)
            temp.writeBytes(jpeg)
            // renameTo is atomic within a filesystem, so a reader either sees no file or sees all
            // of it — never a partial one under a hash that promises the whole.
            if (temp.renameTo(target) || target.isFile) {
                REF_PREFIX + hash
            } else {
                temp.delete()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The JPEG bytes behind a legacy `data:image/jpeg;base64,...` column value, or null if it is
     * not one or cannot be decoded.
     *
     * Needed in two places: the resolver, so rows written before this change still render, and the
     * backfill, which turns those rows into files.
     */
    fun legacyBytes(value: String?): ByteArray? {
        if (!isLegacyDataUri(value)) return null
        return try {
            Base64.decode(value!!.substringAfter("base64,"), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves either form of column value to a bitmap, or null.
     *
     * Null is the answer for every failure — an unknown format, a reference whose file is gone, a
     * payload that will not decode. Callers already draw a placeholder when there is no photo, so
     * a missing file degrades to "this entity has no picture" instead of to a crash. That matters
     * most after a restore: the database is backed up and the image directory is not, so every
     * reference on a restored device dangles until the photos are taken again.
     */
    fun loadBitmap(context: Context, ref: String?): Bitmap? = when {
        ref.isNullOrBlank() -> null

        isStoredRef(ref) -> {
            val file = fileFor(context, ref)
            if (file != null && file.isFile) {
                try {
                    BitmapFactory.decodeFile(file.absolutePath)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }

        isLegacyDataUri(ref) -> legacyBytes(ref)?.let { bytes ->
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        }

        else -> null
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
