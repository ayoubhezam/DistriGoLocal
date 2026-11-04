package com.distrigo.app.data.backup.cloud

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * An in-memory [CloudBackupProvider] for engine tests. Held to the same contract as [GoogleDriveProvider]
 * ([CloudBackupProviderContract]); [failNext] makes the next call throw, as Drive would.
 */
internal class FakeCloudBackupProvider(private val email: String = "owner@gmail.com") : CloudBackupProvider {
    override val id = "fake"

    private class Stored(val id: String, val bytes: ByteArray, val meta: CloudBackupMeta)

    private val backups = LinkedHashMap<String, Stored>()
    private val rings = HashMap<String, ByteArray>()
    private val blobs = LinkedHashMap<String, Pair<String, CloudBlob>>() // id → (business, blob)
    private val blobBytes = HashMap<String, ByteArray>()
    private var nextId = 1

    /** Thrown by the next call, then cleared. */
    var failNext: CloudBackupException? = null

    private fun maybeFail() {
        failNext?.let {
            failNext = null
            throw it
        }
    }

    override suspend fun account(): String? = email.also { maybeFail() }

    override suspend fun listBackups(businessId: String): List<CloudBackupEntry> {
        maybeFail()
        return backups.values.filter { it.meta.businessId == businessId }
            .map { CloudBackupEntry(it.id, it.bytes.size.toLong(), FakeDriveServer.md5(it.bytes), it.meta) }
            .sortedByDescending { it.meta.createdAtMillis }
    }

    override suspend fun uploadBackup(
        file: File,
        meta: CloudBackupMeta,
        resume: String?,
        onSession: (String) -> Unit,
        onProgress: (Long, Long) -> Unit,
    ): CloudBackupEntry {
        maybeFail()
        val bytes = file.readBytes()
        onSession("fake-session-$nextId")
        onProgress(bytes.size.toLong(), bytes.size.toLong())
        val stored = Stored("b${nextId++}", bytes, meta)
        backups[stored.id] = stored
        return CloudBackupEntry(stored.id, bytes.size.toLong(), FakeDriveServer.md5(bytes), meta)
    }

    override suspend fun openBackup(entry: CloudBackupEntry, fromByte: Long): InputStream {
        maybeFail()
        val bytes = backups[entry.id]?.bytes ?: throw CloudBackupException(CloudBackupException.Problem.NOT_FOUND, entry.id)
        return ByteArrayInputStream(bytes, fromByte.toInt(), bytes.size - fromByte.toInt())
    }

    override suspend fun deleteBackup(entry: CloudBackupEntry) {
        maybeFail()
        backups.remove(entry.id)
    }

    override suspend fun readKeyRing(businessId: String): ByteArray? = rings[businessId]?.copyOf().also { maybeFail() }

    override suspend fun writeKeyRing(businessId: String, bytes: ByteArray) {
        maybeFail()
        rings[businessId] = bytes.copyOf()
    }

    override suspend fun listBlobs(businessId: String): List<CloudBlob> {
        maybeFail()
        return blobs.values.filter { it.first == businessId }.map { it.second }
    }

    override suspend fun putBlob(businessId: String, name: String, file: File): CloudBlob {
        maybeFail()
        val blob = CloudBlob("p${nextId++}", name, file.length())
        blobs[blob.id] = businessId to blob
        blobBytes[blob.id] = file.readBytes()
        return blob
    }

    override suspend fun openBlob(blob: CloudBlob): InputStream {
        maybeFail()
        return ByteArrayInputStream(blobBytes[blob.id] ?: throw CloudBackupException(CloudBackupException.Problem.NOT_FOUND, blob.id))
    }

    override suspend fun deleteBlob(blob: CloudBlob) {
        maybeFail()
        blobs.remove(blob.id)
        blobBytes.remove(blob.id)
    }

    /** Test hook: the key ring alone deleted on Drive. */
    fun deleteKeyRing(businessId: String) {
        rings.remove(businessId)
    }

    /** Test hook: what the user's « delete hidden app data » in Drive does. */
    fun wipe() {
        backups.clear()
        rings.clear()
        blobs.clear()
        blobBytes.clear()
    }
}
