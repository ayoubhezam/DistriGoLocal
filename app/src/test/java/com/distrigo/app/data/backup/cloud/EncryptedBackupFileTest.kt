package com.distrigo.app.data.backup.cloud

import com.distrigo.app.data.backup.cloud.EncryptedBackupException.Problem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom

/** A cloud backup opens only whole, unaltered, and with its own key. */
class EncryptedBackupFileTest {

    private val key = BackupKey.generate()
    private val segment = 64
    private val sealedSegment = segment + EncryptedBackupFile.TAG_SIZE
    private val header = EncryptedBackupHeader.SIZE

    private fun plain(size: Int) = ByteArray(size) { (it * 31 + 7).toByte() }

    private fun seal(data: ByteArray, with: BackupKey = key, segmentSize: Int = segment): ByteArray {
        val out = ByteArrayOutputStream()
        EncryptedBackupFile.writer(out, with, segmentSize).use { it.write(data) }
        return out.toByteArray()
    }

    private fun open(sealed: ByteArray, with: BackupKey = key): ByteArray =
        EncryptedBackupFile.reader(ByteArrayInputStream(sealed), with).use { it.readBytes() }

    private fun assertProblem(expected: Problem, block: () -> Unit) {
        try {
            block()
            fail("Expected $expected")
        } catch (e: EncryptedBackupException) {
            assertEquals(expected, e.problem)
        }
    }

    @Test
    fun roundTripsEverySizeAroundSegmentBoundaries() {
        for (size in listOf(0, 1, segment - 1, segment, segment + 1, 3 * segment, 3 * segment + 5)) {
            val data = plain(size)
            val sealed = seal(data)
            assertArrayEquals("size $size", data, open(sealed))
            val segments = maxOf(1, (size + segment - 1) / segment)
            assertEquals("size $size", header + size + segments * EncryptedBackupFile.TAG_SIZE, sealed.size)
        }
    }

    @Test
    fun roundTripsWithTheRealSegmentSizeAndByteByByteWrites() {
        val data = plain(EncryptedBackupFile.DEFAULT_SEGMENT_SIZE + 1000)
        val out = ByteArrayOutputStream()
        EncryptedBackupFile.writer(out, key).use { writer ->
            data.copyOfRange(0, 500).forEach { writer.write(it.toInt()) }
            writer.write(data, 500, data.size - 500)
        }
        assertArrayEquals(data, open(out.toByteArray()))
    }

    @Test
    fun readsByteByByte() {
        val data = plain(3 * segment + 9)
        val reader = EncryptedBackupFile.reader(ByteArrayInputStream(seal(data)), key)
        val read = ByteArrayOutputStream()
        while (true) {
            val b = reader.read()
            if (b < 0) break
            read.write(b)
        }
        assertArrayEquals(data, read.toByteArray())
    }

    @Test
    fun sealingTwiceGivesDifferentBytes() {
        val data = plain(200)
        assertFalse(seal(data).contentEquals(seal(data)))
    }

    @Test
    fun headerTellsTheKey() {
        val read = EncryptedBackupFile.readHeader(ByteArrayInputStream(seal(plain(10))))
        assertArrayEquals(key.id, read.keyId)
        assertEquals(segment, read.segmentSize)
    }

    @Test
    fun anotherKeyIsRefusedBeforeDecrypting() {
        val sealed = seal(plain(100))
        assertProblem(Problem.WRONG_KEY) { open(sealed, BackupKey.generate()) }
    }

    @Test
    fun sameIdButOtherMaterialIsDamaged() {
        val sealed = seal(plain(100))
        val impostor = BackupKey(key.id, ByteArray(BackupKey.SIZE).also(SecureRandom()::nextBytes))
        assertProblem(Problem.DAMAGED) { open(sealed, impostor) }
    }

    @Test
    fun otherFilesAreNotEncryptedBackups() {
        assertProblem(Problem.NOT_ENCRYPTED_BACKUP) { open(ByteArray(0)) }
        assertProblem(Problem.NOT_ENCRYPTED_BACKUP) { open("PK\u0003\u0004 a zip file".toByteArray()) }
    }

    @Test
    fun newerFormatAsksForAnUpdate() {
        val sealed = seal(plain(10)).also { it[4] = 2 }
        assertProblem(Problem.UNSUPPORTED_VERSION) { open(sealed) }
    }

    @Test
    fun alteredByteInASegmentIsDamaged() {
        val sealed = seal(plain(3 * segment))
        sealed[header + sealedSegment + 5] = (sealed[header + sealedSegment + 5] + 1).toByte()
        assertProblem(Problem.DAMAGED) { open(sealed) }
    }

    @Test
    fun alteredHeaderIsDamaged() {
        // The nonce prefix: authenticated by every segment.
        val sealed = seal(plain(100)).also { it[header - 1] = (it[header - 1] + 1).toByte() }
        assertProblem(Problem.DAMAGED) { open(sealed) }
    }

    @Test
    fun swappedSegmentsAreDamaged() {
        val sealed = seal(plain(3 * segment))
        val first = sealed.copyOfRange(header, header + sealedSegment)
        val second = sealed.copyOfRange(header + sealedSegment, header + 2 * sealedSegment)
        System.arraycopy(second, 0, sealed, header, sealedSegment)
        System.arraycopy(first, 0, sealed, header + sealedSegment, sealedSegment)
        assertProblem(Problem.DAMAGED) { open(sealed) }
    }

    @Test
    fun fileCutAtASegmentBoundaryIsTruncated() {
        val sealed = seal(plain(3 * segment + 10))
        assertProblem(Problem.TRUNCATED) { open(sealed.copyOf(header + sealedSegment)) }
        assertProblem(Problem.TRUNCATED) { open(sealed.copyOf(header + 2 * sealedSegment)) }
    }

    @Test
    fun fileWithOnlyItsHeaderIsTruncated() {
        val sealed = seal(plain(100))
        assertProblem(Problem.TRUNCATED) { open(sealed.copyOf(header)) }
        assertProblem(Problem.TRUNCATED) { open(sealed.copyOf(header - 3)) }
    }

    @Test
    fun fileCutInsideASegmentIsRefused() {
        val sealed = seal(plain(3 * segment))
        assertProblem(Problem.DAMAGED) { open(sealed.copyOf(header + sealedSegment + 20)) }
    }

    @Test
    fun bytesAddedAfterTheEndAreRefused() {
        val sealed = seal(plain(3 * segment + 10)) + byteArrayOf(1, 2, 3)
        assertProblem(Problem.DAMAGED) { open(sealed) }
        // Full segments, then a whole extra segment's worth: the real last segment is read as a middle one.
        val padded = seal(plain(3 * segment)) + ByteArray(sealedSegment)
        assertProblem(Problem.DAMAGED) { open(padded) }
    }
}
