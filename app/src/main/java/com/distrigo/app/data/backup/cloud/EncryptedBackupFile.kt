package com.distrigo.app.data.backup.cloud

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The encrypted backup file (`.dgenc`) sent to the cloud: a `.distrigo` file, sealed on the phone with the business's
 * backup key. Plan: `docs/cloud_backup_drive_plan.md` §3.2.
 *
 * Layout, big-endian:
 * - header ([EncryptedBackupHeader.SIZE] bytes): magic `DGEN`, format version, key id (16), plain segment size (4),
 *   nonce prefix (8);
 * - segments: each up to `segmentSize` plain bytes sealed with AES-256-GCM, followed by its 16-byte tag. Every segment
 *   but the last holds exactly `segmentSize` bytes; the last may be shorter, or empty when the file is.
 *
 * Segment `i` uses nonce `prefix ‖ i` and authenticates `header ‖ i ‖ last`: a segment cannot be moved, a header
 * cannot be changed, and a file cut at a segment boundary is caught (its new last segment was not sealed as last).
 *
 * Streaming both ways: memory stays at about two segments whatever the backup's size. Plain JVM code, so the unit
 * tests run exactly what the phone runs.
 */
object EncryptedBackupFile {
    /** 8 MB: few segments for a large backup, and a small enough buffer for any phone. */
    const val DEFAULT_SEGMENT_SIZE = 8 * 1024 * 1024
    const val EXTENSION = "dgenc"
    internal const val TAG_SIZE = 16
    internal const val MAX_SEGMENT_SIZE = 64 * 1024 * 1024

    /** Encrypts everything written to it into [out]; [OutputStream.close] seals the last segment and closes [out]. */
    fun writer(
        out: OutputStream,
        key: BackupKey,
        segmentSize: Int = DEFAULT_SEGMENT_SIZE,
        random: SecureRandom = SecureRandom(),
    ): OutputStream = EncryptedBackupWriter(out, key, segmentSize, random)

    /**
     * Decrypts [input]. Reading the header happens here, so a file that is not an encrypted backup, or was sealed
     * with another key, fails at once. Every later read throws [EncryptedBackupException] if a segment is damaged
     * or the file ends early: the plain bytes handed out were all authenticated, but a caller must read to the end
     * before trusting the whole.
     */
    fun reader(input: InputStream, key: BackupKey): InputStream = EncryptedBackupReader(input, key)

    /** The header alone, e.g. to see which key a downloaded file needs before asking for the password. */
    fun readHeader(input: InputStream): EncryptedBackupHeader = EncryptedBackupHeader.read(input)
}

/** Why an encrypted backup cannot be read. An [IOException], so it travels through any stream code unchanged. */
class EncryptedBackupException(val problem: Problem, message: String) : IOException(message) {
    enum class Problem {
        /** Not a `.dgenc` file at all. */
        NOT_ENCRYPTED_BACKUP,

        /** Written by a newer app: update DistriGo. */
        UNSUPPORTED_VERSION,

        /** Sealed with another backup key (another business, or a key that was replaced). */
        WRONG_KEY,

        /** A segment does not match its tag: the file was altered or damaged. */
        DAMAGED,

        /** The file stops before its last segment: an upload or download was cut. */
        TRUNCATED,
    }
}

class EncryptedBackupHeader internal constructor(
    val version: Int,
    val keyId: ByteArray,
    val segmentSize: Int,
    internal val noncePrefix: ByteArray,
) {
    internal fun bytes(): ByteArray = ByteBuffer.allocate(SIZE)
        .put(MAGIC).put(version.toByte()).put(keyId).putInt(segmentSize).put(noncePrefix)
        .array()

    companion object {
        const val VERSION = 1
        const val SIZE = 4 + 1 + BackupKey.ID_SIZE + 4 + 8
        private val MAGIC = byteArrayOf('D'.code.toByte(), 'G'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte())

        internal fun read(input: InputStream): EncryptedBackupHeader {
            val bytes = ByteArray(SIZE)
            val count = readFully(input, bytes)
            if (count < MAGIC.size || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
                throw EncryptedBackupException(EncryptedBackupException.Problem.NOT_ENCRYPTED_BACKUP, "No DGEN magic")
            }
            if (count < 5) throw EncryptedBackupException(EncryptedBackupException.Problem.TRUNCATED, "Header cut")
            val version = bytes[4].toInt() and 0xFF
            if (version != VERSION) {
                throw EncryptedBackupException(EncryptedBackupException.Problem.UNSUPPORTED_VERSION, "Format $version")
            }
            if (count < SIZE) throw EncryptedBackupException(EncryptedBackupException.Problem.TRUNCATED, "Header cut")
            val buffer = ByteBuffer.wrap(bytes, 5, SIZE - 5)
            val keyId = ByteArray(BackupKey.ID_SIZE).also { buffer.get(it) }
            val segmentSize = buffer.int
            val prefix = ByteArray(8).also { buffer.get(it) }
            if (segmentSize < 1 || segmentSize > EncryptedBackupFile.MAX_SEGMENT_SIZE) {
                throw EncryptedBackupException(EncryptedBackupException.Problem.DAMAGED, "Segment size $segmentSize")
            }
            return EncryptedBackupHeader(version, keyId, segmentSize, prefix)
        }
    }
}

private const val TRANSFORMATION = "AES/GCM/NoPadding"

private fun segmentCipher(mode: Int, key: BackupKey, header: EncryptedBackupHeader, headerBytes: ByteArray, index: Int, last: Boolean): Cipher {
    val nonce = ByteBuffer.allocate(12).put(header.noncePrefix).putInt(index).array()
    return Cipher.getInstance(TRANSFORMATION).apply {
        init(mode, SecretKeySpec(key.material, "AES"), GCMParameterSpec(EncryptedBackupFile.TAG_SIZE * 8, nonce))
        updateAAD(headerBytes)
        updateAAD(ByteBuffer.allocate(5).putInt(index).put(if (last) 1 else 0).array())
    }
}

/** Reads until [buffer] is full or the stream ends; returns how many bytes were read. */
private fun readFully(input: InputStream, buffer: ByteArray): Int {
    var total = 0
    while (total < buffer.size) {
        val read = input.read(buffer, total, buffer.size - total)
        if (read < 0) break
        total += read
    }
    return total
}

private class EncryptedBackupWriter(
    private val out: OutputStream,
    private val key: BackupKey,
    segmentSize: Int,
    random: SecureRandom,
) : OutputStream() {
    private val header: EncryptedBackupHeader
    private val headerBytes: ByteArray
    private val buffer: ByteArray
    private var filled = 0
    private var index = 0
    private var closed = false

    init {
        require(segmentSize in 1..EncryptedBackupFile.MAX_SEGMENT_SIZE) { "Segment size $segmentSize" }
        header = EncryptedBackupHeader(
            EncryptedBackupHeader.VERSION, key.id.copyOf(), segmentSize, ByteArray(8).also(random::nextBytes),
        )
        headerBytes = header.bytes()
        buffer = ByteArray(segmentSize)
        out.write(headerBytes)
    }

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        check(!closed) { "Closed" }
        var offset = off
        var remaining = len
        while (remaining > 0) {
            // A full segment is sealed only once more bytes arrive: until then it may still be the last one.
            if (filled == buffer.size) seal(last = false)
            val count = minOf(remaining, buffer.size - filled)
            System.arraycopy(b, offset, buffer, filled, count)
            filled += count
            offset += count
            remaining -= count
        }
    }

    private fun seal(last: Boolean) {
        if (index == Int.MAX_VALUE) throw IOException("Backup too large")
        val cipher = segmentCipher(Cipher.ENCRYPT_MODE, key, header, headerBytes, index, last)
        out.write(cipher.doFinal(buffer, 0, filled))
        index++
        filled = 0
    }

    override fun flush() = out.flush()

    override fun close() {
        if (closed) return
        closed = true
        try {
            seal(last = true)
            buffer.fill(0)
        } finally {
            out.close()
        }
    }
}

private class EncryptedBackupReader(input: InputStream, private val key: BackupKey) : InputStream() {
    private val input = PushbackInputStream(input, 1)
    private val header = EncryptedBackupHeader.read(this.input)
    private val headerBytes = header.bytes()
    private val sealed = ByteArray(header.segmentSize + EncryptedBackupFile.TAG_SIZE)
    private var plain = ByteArray(0)
    private var position = 0
    private var index = 0
    private var finished = false

    init {
        if (!header.keyId.contentEquals(key.id)) {
            throw EncryptedBackupException(EncryptedBackupException.Problem.WRONG_KEY, "Sealed with another key")
        }
    }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (position == plain.size) {
            if (finished) return -1
            openNextSegment()
        }
        val count = minOf(len, plain.size - position)
        System.arraycopy(plain, position, b, off, count)
        position += count
        return count
    }

    override fun available(): Int = plain.size - position

    private fun openNextSegment() {
        val count = readFully(input, sealed)
        if (count < EncryptedBackupFile.TAG_SIZE) {
            // Nothing (or less than a tag) where a segment was due: the previous one was not the last.
            throw EncryptedBackupException(EncryptedBackupException.Problem.TRUNCATED, "Segment $index missing")
        }
        // A segment is the last one exactly when the file ends after it.
        val last = count < sealed.size || endOfInput()
        plain = try {
            open(count, last)
        } catch (e: AEADBadTagException) {
            throw diagnose(count, last)
        }
        position = 0
        index++
        finished = last
    }

    private fun endOfInput(): Boolean {
        val next = input.read()
        if (next < 0) return true
        input.unread(next)
        return false
    }

    private fun open(count: Int, last: Boolean): ByteArray =
        segmentCipher(Cipher.DECRYPT_MODE, key, header, headerBytes, index, last).doFinal(sealed, 0, count)

    /** A bad tag: tells a file cut at a segment boundary, or with bytes added after its end, from a damaged one. */
    private fun diagnose(count: Int, assumedLast: Boolean): EncryptedBackupException {
        val opensOtherwise = try {
            open(count, !assumedLast)
            true
        } catch (e: AEADBadTagException) {
            false
        }
        return when {
            opensOtherwise && assumedLast ->
                EncryptedBackupException(EncryptedBackupException.Problem.TRUNCATED, "File ends after segment $index")
            opensOtherwise ->
                EncryptedBackupException(EncryptedBackupException.Problem.DAMAGED, "Bytes after the last segment")
            else -> EncryptedBackupException(EncryptedBackupException.Problem.DAMAGED, "Segment $index altered")
        }
    }

    override fun close() {
        plain.fill(0)
        input.close()
    }
}
