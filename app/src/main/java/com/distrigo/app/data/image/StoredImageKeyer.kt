package com.distrigo.app.data.image

import coil.key.Keyer
import coil.request.Options
import java.io.File

/**
 * Coil's memory cache key for a photo in [ImageStore]'s folder: its path, and nothing read from disk. Coil's own key
 * for a File adds the file's last-modified date, a disk read on the main thread for every image drawn (StrictMode, in a
 * list of avatars once per row). A stored photo is named by its content (`<sha256>.jpg`), so its path never stands for
 * other bytes and the date adds nothing. Any other file — the business logo, rewritten under one name — returns null
 * here and keeps Coil's key, date included. Coil still adds the requested size to the key.
 */
class StoredImageKeyer(private val imagesDir: File) : Keyer<File> {
    override fun key(data: File, options: Options): String? =
        if (data.parentFile == imagesDir) data.path else null
}
