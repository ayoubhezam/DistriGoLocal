package com.distrigo.app.data.backup.cloud

import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Where cloud backups are kept (plan §3.1). The provider only moves bytes: what goes in a backup, its encryption,
 * retention and scheduling are written once, above it. Google Drive today ([GoogleDriveProvider]); another store can
 * be added later without touching the rest.
 *
 * Every call may throw [CloudBackupException]. Calls block on the network: run them from a worker or `Dispatchers.IO`.
 */
interface CloudBackupProvider {
    /** Stable id, recorded with the link: `gdrive`. */
    val id: String

    /** The linked account as the user knows it (an email), for the status card. */
    suspend fun account(): String?

    /** This business's backups, newest first. */
    suspend fun listBackups(businessId: String): List<CloudBackupEntry>

    /**
     * Sends [file] (an encrypted `.dgenc`) and returns it once the provider holds exactly these bytes.
     *
     * Resumable: [onSession] receives a session to save; after a cut connection or a killed app, passing it back as
     * [resume] continues where the provider stopped. A session the provider no longer knows starts over by itself.
     */
    suspend fun uploadBackup(
        file: File,
        meta: CloudBackupMeta,
        resume: String? = null,
        onSession: (String) -> Unit = {},
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    ): CloudBackupEntry

    /** The backup's bytes from [fromByte], to resume a cut download. The caller closes the stream. */
    suspend fun openBackup(entry: CloudBackupEntry, fromByte: Long = 0): InputStream

    /** Already gone counts as deleted. */
    suspend fun deleteBackup(entry: CloudBackupEntry)

    /** The key ring's bytes (`keyring.json`), or null if this business has none there. */
    suspend fun readKeyRing(businessId: String): ByteArray?

    /** Creates or replaces the key ring. */
    suspend fun writeKeyRing(businessId: String, bytes: ByteArray)

    /** Content-named files shared by backups (photos, plan §3.5). */
    suspend fun listBlobs(businessId: String): List<CloudBlob>

    suspend fun putBlob(businessId: String, name: String, file: File): CloudBlob

    suspend fun openBlob(blob: CloudBlob): InputStream

    /** Already gone counts as deleted. */
    suspend fun deleteBlob(blob: CloudBlob)
}

/** What each backup records about itself, readable without downloading it (Drive's `appProperties`). */
data class CloudBackupMeta(
    val businessId: String,
    val deviceId: String,
    val createdAtMillis: Long,
    val schemaVersion: Int,
    val appVersion: String,
    /** The `.distrigo` size before encryption, shown in the restore list. */
    val plainSize: Long,
    /** The backup key's id ([BackupKey.idText]). */
    val keyId: String,
)

data class CloudBackupEntry(
    /** The provider's own id for the file. */
    val id: String,
    /** Encrypted size, as stored. */
    val size: Long,
    /** MD5 of the stored bytes, hex, when the provider gives one. */
    val md5: String?,
    val meta: CloudBackupMeta,
)

data class CloudBlob(val id: String, val name: String, val size: Long)

/** Why a cloud call failed, in the terms the engine and the screens act on. */
class CloudBackupException(val problem: Problem, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Problem(val retryable: Boolean) {
        /** No Google Play services on this phone: the cloud backup cannot be offered. */
        UNAVAILABLE(false),

        /** Access was revoked or must be granted again on screen: « Reconnecter Google Drive ». */
        NEEDS_CONSENT(false),

        /** The account's storage is full: stop, alert, never retry in a loop (owner's decision §9.2). */
        QUOTA_EXCEEDED(false),

        /** Too many requests: try later. */
        RATE_LIMITED(true),

        /** The provider failed (5xx): try later. */
        SERVER(true),

        /** No connection, or it was cut: try later. */
        NETWORK(true),

        /** The stored bytes are not the bytes sent: the bad copy is removed, send again. */
        UPLOAD_CORRUPTED(true),

        /** The file is not there (deleted on Drive, or by another phone). */
        NOT_FOUND(false),

        /** Any other refusal: a bug or an unexpected answer, not worth retrying. */
        REJECTED(false),
    }
}
