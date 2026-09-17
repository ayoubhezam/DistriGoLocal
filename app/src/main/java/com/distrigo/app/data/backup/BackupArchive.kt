package com.distrigo.app.data.backup

import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Writes and checks the ZIP itself. No Android here: what a valid file is is tested on the JVM.
 *
 * `manifest.json` is always the first entry, so a file can be checked in one pass from a stream — which
 * is all a file picker gives — without holding it or seeking in it.
 */
object BackupArchive {

    /** Hashes [file] into the entry the manifest lists for it under [name]. */
    fun describe(name: String, file: File): BackupEntry =
        BackupEntry(name, file.length(), file.inputStream().use(BackupFormat::sha256))

    /**
     * Writes a backup to [out]: the manifest, then each of its entries from [sources], in the manifest's
     * order. Every entry must have a source. Does not close [out].
     */
    fun write(manifest: BackupManifest, sources: Map<String, File>, out: OutputStream) {
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(BackupFormat.MANIFEST_ENTRY))
        zip.write(manifest.toJson().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        for (entry in manifest.entries) {
            val source = requireNotNull(sources[entry.name]) { "no file for ${entry.name}" }
            zip.putNextEntry(ZipEntry(entry.name))
            source.inputStream().use { it.copyTo(zip, BUFFER) }
            zip.closeEntry()
        }
        zip.finish()
        zip.flush()
    }

    /**
     * Reads a backup from [input] to its end and checks it is whole: the manifest first and valid, then
     * exactly the entries it lists, each once, with its size and SHA-256. Stops at the first problem.
     *
     * [onManifest] receives the manifest once it is read and valid, before any entry, so a caller can
     * refuse a file by throwing before unpacking it. [onEntry] receives each listed entry's bytes as they
     * are read, before they are checked, so a caller unpacking the file must treat what it wrote as
     * unconfirmed until this returns [Verification.Verified]. Does not close [input].
     */
    fun verify(
        input: InputStream,
        onManifest: ((BackupManifest) -> Unit)? = null,
        onEntry: ((BackupEntry, InputStream) -> Unit)? = null,
    ): Verification {
        val raw = TailStream(input)
        val zip = ZipInputStream(raw)
        try {
            val first = zip.nextEntry ?: return Verification.Failed(BackupProblem.NotABackup)
            if (first.name != BackupFormat.MANIFEST_ENTRY) return Verification.Failed(BackupProblem.NotABackup)
            val text = readLimited(zip, BackupFormat.MAX_MANIFEST_BYTES)
                ?: return Verification.Failed(BackupProblem.Damaged("manifest too large"))
            val manifest = when (val parsed = BackupManifest.parse(text.toString(Charsets.UTF_8))) {
                is ManifestResult.Invalid -> return Verification.Failed(parsed.problem)
                is ManifestResult.Valid -> parsed.manifest
            }
            onManifest?.invoke(manifest)

            val expected = manifest.entries.associateBy { it.name }
            val seen = mutableSetOf<String>()
            while (true) {
                val next = zip.nextEntry ?: break
                val entry = expected[next.name]
                    ?: return damaged(if (BackupFormat.isKnownEntry(next.name)) "unlisted ${next.name}" else "unknown entry ${next.name}")
                if (!seen.add(entry.name)) return damaged("${entry.name} twice")

                val checked = CheckedStream(zip, entry.size)
                if (onEntry != null) onEntry(entry, checked)
                checked.drain()
                if (checked.overflow) return damaged("${entry.name} larger than ${entry.size}")
                if (checked.count != entry.size) return damaged("${entry.name} is ${checked.count} bytes, not ${entry.size}")
                if (checked.sha256() != entry.sha256) return damaged("${entry.name} checksum")
            }
            val absent = expected.keys - seen
            if (absent.isNotEmpty()) return damaged("missing ${absent.sorted().joinToString()}")

            // Reading entries never reaches the index at the end of a ZIP, so a file cut short there would
            // pass. It must end with the end record the writer puts there, counting every entry.
            val buffer = ByteArray(BUFFER)
            while (raw.read(buffer, 0, buffer.size) >= 0) Unit
            if (!raw.endsWithEndRecord(entries = manifest.entries.size + 1)) return damaged("no end record")
            return Verification.Verified(manifest)
        } catch (e: ZipException) {
            return Verification.Failed(BackupProblem.Damaged("zip: ${e.message}"))
        } catch (e: IOException) {
            return Verification.Failed(BackupProblem.Damaged("read: ${e.message}"))
        }
    }

    private fun damaged(detail: String) = Verification.Failed(BackupProblem.Damaged(detail))

    /** Everything left in the current entry, or null if it is longer than [limit]. */
    private fun readLimited(input: InputStream, limit: Long): ByteArray? {
        val checked = CheckedStream(input, limit)
        val bytes = checked.readBytes()
        return if (checked.overflow) null else bytes
    }

    /**
     * Passes an entry through while hashing and counting it, and stops at one byte past [limit]: a ZIP
     * can claim any size, so what is read, not what is declared, is what is held to the manifest.
     */
    private class CheckedStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private val digest = MessageDigest.getInstance("SHA-256")
        var count = 0L
            private set
        var overflow = false
            private set

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (overflow) return -1
            val allowed = (limit + 1 - count).coerceAtMost(length.toLong()).toInt()
            val read = super.read(buffer, offset, allowed)
            if (read < 0) return -1
            count += read
            if (count > limit) {
                overflow = true
                return -1
            }
            digest.update(buffer, offset, read)
            return read
        }

        override fun skip(n: Long): Long = throw UnsupportedOperationException("read, so it is hashed")

        /** Reads whatever the caller left unread. */
        fun drain() {
            val buffer = ByteArray(BUFFER)
            while (read(buffer, 0, buffer.size) >= 0) Unit
        }

        fun sha256(): String = digest.digest().joinToString("") { "%02x".format(it) }

        /** The zip stream belongs to [verify]; a reader closing its entry must not close the file. */
        override fun close() = Unit
    }

    /**
     * Keeps the last bytes read, to check the ZIP's end-of-central-directory record: signature
     * `50 4B 05 06`, this disk's and the total entry count, and no comment — which is what
     * [ZipOutputStream] writes last.
     */
    private class TailStream(input: InputStream) : FilterInputStream(input) {
        private val tail = ByteArray(END_RECORD)
        private var total = 0L

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(buffer, offset, length)
            if (read <= 0) return read
            if (read >= END_RECORD) {
                System.arraycopy(buffer, offset + read - END_RECORD, tail, 0, END_RECORD)
            } else {
                System.arraycopy(tail, read, tail, 0, END_RECORD - read)
                System.arraycopy(buffer, offset, tail, END_RECORD - read, read)
            }
            total += read
            return read
        }

        override fun skip(n: Long): Long = throw UnsupportedOperationException("read, so the tail is kept")

        fun endsWithEndRecord(entries: Int): Boolean {
            if (total < END_RECORD) return false
            fun u16(at: Int) = (tail[at].toInt() and 0xFF) or ((tail[at + 1].toInt() and 0xFF) shl 8)
            return tail[0] == 0x50.toByte() && tail[1] == 0x4B.toByte() && tail[2] == 0x05.toByte() && tail[3] == 0x06.toByte() &&
                u16(8) == entries && u16(10) == entries && u16(20) == 0
        }

        private companion object {
            const val END_RECORD = 22
        }
    }

    private const val BUFFER = 64 * 1024
}

sealed class Verification {
    data class Verified(val manifest: BackupManifest) : Verification()
    data class Failed(val problem: BackupProblem) : Verification()
}
