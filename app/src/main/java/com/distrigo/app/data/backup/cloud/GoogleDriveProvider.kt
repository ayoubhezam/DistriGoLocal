package com.distrigo.app.data.backup.cloud

import com.distrigo.app.data.backup.cloud.CloudBackupException.Problem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Cloud backups in the user's own Google Drive, in the hidden `appDataFolder` (plan §1.3, §3.2).
 *
 * Every file carries `appProperties` naming its kind and business, so a list is one query and two businesses (or
 * two phones) on one Google account never mix. File names are for people looking at the Drive API, not relied on.
 */
class GoogleDriveProvider(private val drive: DriveClient) : CloudBackupProvider {

    override val id = ID

    override suspend fun account(): String? = drive.accountEmail()

    override suspend fun listBackups(businessId: String): List<CloudBackupEntry> =
        drive.list(kindQuery(KIND_BACKUP, businessId))
            .mapNotNull { file -> metaFrom(file.appProperties)?.let { CloudBackupEntry(file.id, file.size, file.md5, it) } }
            .sortedByDescending { it.meta.createdAtMillis }

    override suspend fun uploadBackup(
        file: File,
        meta: CloudBackupMeta,
        resume: String?,
        onSession: (String) -> Unit,
        onProgress: (Long, Long) -> Unit,
    ): CloudBackupEntry {
        val properties = propertiesOf(meta)
        val name = "backup-${meta.businessId}-${meta.createdAtMillis}.${EncryptedBackupFile.EXTENSION}"
        val stored = drive.uploadResumable(file, name, properties, resume, onSession, onProgress)
        verify(stored, file)
        return CloudBackupEntry(stored.id, stored.size, stored.md5, meta)
    }

    override suspend fun openBackup(entry: CloudBackupEntry, fromByte: Long): InputStream = drive.open(entry.id, fromByte)

    override suspend fun deleteBackup(entry: CloudBackupEntry) = drive.delete(entry.id)

    override suspend fun readKeyRing(businessId: String): ByteArray? =
        keyRingFile(businessId)?.let { drive.download(it.id) }

    override suspend fun writeKeyRing(businessId: String, bytes: ByteArray) {
        val existing = keyRingFile(businessId)
        if (existing != null) {
            drive.updateSmall(existing.id, bytes)
        } else {
            drive.createSmall("keyring-$businessId.json", mapOf(KIND to KIND_KEYRING, BUSINESS to businessId), bytes)
        }
    }

    override suspend fun listBlobs(businessId: String): List<CloudBlob> =
        drive.list(kindQuery(KIND_BLOB, businessId)).mapNotNull { file ->
            file.appProperties[BLOB_NAME]?.let { CloudBlob(file.id, it, file.size) }
        }

    override suspend fun putBlob(businessId: String, name: String, file: File): CloudBlob {
        val properties = mapOf(KIND to KIND_BLOB, BUSINESS to businessId, BLOB_NAME to name)
        checkProperties(properties)
        val fileName = "blob-$name"
        val stored = if (file.length() <= DriveClient.SMALL_FILE_LIMIT) {
            drive.createSmall(fileName, properties, withContext(Dispatchers.IO) { file.readBytes() })
        } else {
            drive.uploadResumable(file, fileName, properties, null, {}, { _, _ -> })
        }
        verify(stored, file)
        return CloudBlob(stored.id, name, stored.size)
    }

    override suspend fun openBlob(blob: CloudBlob): InputStream = drive.open(blob.id, 0)

    override suspend fun deleteBlob(blob: CloudBlob) = drive.delete(blob.id)

    /** The newest ring if a race ever left two; the others are left for the user to never see. */
    private suspend fun keyRingFile(businessId: String): DriveFile? =
        drive.list(kindQuery(KIND_KEYRING, businessId)).maxByOrNull { it.name + it.id }

    /** Drive's MD5 of what it stored against the file sent; a mismatch removes the bad copy (plan §3.2). */
    private suspend fun verify(stored: DriveFile, file: File) {
        val local = md5Hex(file)
        if (stored.md5 == null || !stored.md5.equals(local, ignoreCase = true)) {
            runCatching { drive.delete(stored.id) }
            throw CloudBackupException(Problem.UPLOAD_CORRUPTED, "Drive holds other bytes than sent")
        }
    }

    companion object {
        const val ID = "gdrive"

        // Short keys: Drive allows 124 bytes per property, key and value together.
        internal const val KIND = "kind"
        internal const val BUSINESS = "biz"
        private const val DEVICE = "dev"
        private const val CREATED = "at"
        private const val SCHEMA = "schema"
        private const val APP = "app"
        private const val PLAIN_SIZE = "size"
        private const val KEY = "key"
        private const val BLOB_NAME = "blob"
        internal const val KIND_BACKUP = "backup"
        internal const val KIND_KEYRING = "keyring"
        internal const val KIND_BLOB = "blob"
        private const val PROPERTY_LIMIT = 124

        private fun kindQuery(kind: String, businessId: String) =
            "appProperties has { key='$KIND' and value=${DriveClient.quote(kind)} } and " +
                "appProperties has { key='$BUSINESS' and value=${DriveClient.quote(businessId)} }"

        internal fun propertiesOf(meta: CloudBackupMeta): Map<String, String> = mapOf(
            KIND to KIND_BACKUP,
            BUSINESS to meta.businessId,
            DEVICE to meta.deviceId,
            CREATED to meta.createdAtMillis.toString(),
            SCHEMA to meta.schemaVersion.toString(),
            APP to meta.appVersion,
            PLAIN_SIZE to meta.plainSize.toString(),
            KEY to meta.keyId,
        ).also(::checkProperties)

        /** Null for a file missing a field: written by something else, it is not offered for restore. */
        internal fun metaFrom(properties: Map<String, String>): CloudBackupMeta? {
            return CloudBackupMeta(
                businessId = properties[BUSINESS] ?: return null,
                deviceId = properties[DEVICE] ?: return null,
                createdAtMillis = properties[CREATED]?.toLongOrNull() ?: return null,
                schemaVersion = properties[SCHEMA]?.toIntOrNull() ?: return null,
                appVersion = properties[APP] ?: return null,
                plainSize = properties[PLAIN_SIZE]?.toLongOrNull() ?: return null,
                keyId = properties[KEY] ?: return null,
            )
        }

        private fun checkProperties(properties: Map<String, String>) {
            properties.forEach { (key, value) ->
                require(key.toByteArray().size + value.toByteArray().size <= PROPERTY_LIMIT) { "Property $key too long" }
            }
        }

        internal suspend fun md5Hex(file: File): String = withContext(Dispatchers.IO) {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
