package com.distrigo.app.data.backup.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest

/** Why a cloud backup cannot be restored. Network failures stay [CloudBackupException]s (retry). */
class CloudRestoreException(val problem: Problem, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Problem {
        /** The account holds no key ring for this business: no cloud backup was ever made for it there. */
        NO_KEY_RING,

        /** The key ring or the backup was written by a newer DistriGo: update the app. */
        NEWER_APP,

        /** The key ring cannot be read. */
        KEY_RING_DAMAGED,

        /** The backup was sealed with an older key (before a new link): this ring cannot open it. */
        OTHER_KEY,

        /** The backup did not arrive whole, or was altered: try again; if it lasts, choose another backup. */
        DAMAGED,
    }
}

/** What fetching the photos did. */
data class PhotoFetch(
    /** Downloaded, checked and saved. */
    val fetched: Int,
    /** Already on the phone: not downloaded. */
    val alreadyHere: Int,
    /** Whose content does not match its name: not saved (altered in the cloud). */
    val rejected: Int,
)

/** How far a restore download is. */
sealed class CloudRestoreProgress {
    data class Downloading(val received: Long, val total: Long) : CloudRestoreProgress()
    data object Decrypting : CloudRestoreProgress()
    data class Photos(val done: Int, val total: Int) : CloudRestoreProgress()
}

/**
 * Restore on a new phone (plan §3.7), up to the point where the existing restore takes over:
 * 1. [keyRing], then [unlock] with the password or the recovery code;
 * 2. [download] the chosen backup: resumable (a cut download continues from the bytes already here), checked against
 *    the size and MD5 the provider reports, then decrypted segment by segment into a plain `.distrigo`;
 * 3. [fetchPhotos]: every photo the business keeps in the cloud that the phone lacks, each decrypted and checked
 *    against its name (a photo's name is a keyed hash of its content, so a swapped or altered photo is refused);
 * 4. the caller hands the `.distrigo` to `RestoreCoordinator` (inspection, schema check, stock rule, safety backup,
 *    restart). Cloud backups hold no photos, so the restore keeps the phone's photos folder, where step 3 put them.
 *
 * The caller then links the phone with [CloudBackupLink.join], so the daily backup carries on under the same key.
 */
class CloudRestore(
    private val providerFor: (account: String) -> CloudBackupProvider,
    private val stagingDir: File,
    private val imagesDir: File,
) {

    /** The account's key ring for [businessId]. Throws [CloudRestoreException] when there is none or it is unreadable. */
    suspend fun keyRing(account: String, businessId: String): BackupKeyRing {
        val bytes = providerFor(account).readKeyRing(businessId)
            ?: throw CloudRestoreException(CloudRestoreException.Problem.NO_KEY_RING, "no key ring for $businessId")
        return try {
            BackupKeyRing.fromJson(String(bytes, Charsets.UTF_8))
        } catch (e: BackupKeyRingException) {
            val problem = if (e.unsupportedVersion) CloudRestoreException.Problem.NEWER_APP else CloudRestoreException.Problem.KEY_RING_DAMAGED
            throw CloudRestoreException(problem, e.message ?: "key ring", e)
        }
    }

    /** The key, from the password or the recovery code; null when [secret] is neither. About a second. */
    suspend fun unlock(ring: BackupKeyRing, secret: String): BackupKey? = withContext(Dispatchers.Default) {
        ring.unlockWithPassword(secret) ?: ring.unlockWithRecoveryCode(secret)
    }

    /** The business's backups, newest first, each with whether [ring] can open it. */
    suspend fun backups(account: String, ring: BackupKeyRing): List<Pair<CloudBackupEntry, Boolean>> =
        providerFor(account).listBackups(ring.businessId).map { it to (it.meta.keyId == ring.keyId) }

    /**
     * Downloads and decrypts [entry]; returns the plain `.distrigo`, in the staging folder. A download cut before the
     * end leaves its bytes, and the next call continues from them.
     */
    suspend fun download(
        account: String,
        entry: CloudBackupEntry,
        key: BackupKey,
        onProgress: (CloudRestoreProgress) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        if (entry.meta.keyId != key.idText) {
            throw CloudRestoreException(CloudRestoreException.Problem.OTHER_KEY, "sealed with key ${entry.meta.keyId}")
        }
        stagingDir.mkdirs()
        val sealed = File(stagingDir, "restore-${safeName(entry.id)}.${EncryptedBackupFile.EXTENSION}")
        // Anything else in the staging folder is an older attempt at another backup.
        stagingDir.listFiles().orEmpty().filter { it != sealed }.forEach { it.deleteRecursively() }
        if (sealed.length() > entry.size) sealed.delete()

        val provider = providerFor(account)
        if (sealed.length() < entry.size) {
            provider.openBackup(entry, fromByte = sealed.length()).use { input ->
                FileOutputStream(sealed, true).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var received = sealed.length()
                    onProgress(CloudRestoreProgress.Downloading(received, entry.size))
                    while (true) {
                        val read = try {
                            input.read(buffer)
                        } catch (e: IOException) {
                            if (e is CloudBackupException) throw e
                            throw CloudBackupException(CloudBackupException.Problem.NETWORK, e.message ?: "download cut", e)
                        }
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        received += read
                        onProgress(CloudRestoreProgress.Downloading(received, entry.size))
                    }
                }
            }
        }
        if (sealed.length() != entry.size) {
            throw CloudBackupException(CloudBackupException.Problem.NETWORK, "received ${sealed.length()} of ${entry.size}")
        }
        entry.md5?.let { expected ->
            if (!GoogleDriveProvider.md5Hex(sealed).equals(expected, ignoreCase = true)) {
                sealed.delete()
                throw CloudRestoreException(CloudRestoreException.Problem.DAMAGED, "MD5 differs from the provider's")
            }
        }

        onProgress(CloudRestoreProgress.Decrypting)
        val plain = File(stagingDir, "restore.distrigo")
        try {
            sealed.inputStream().use { input ->
                EncryptedBackupFile.reader(input, key).use { reader -> plain.outputStream().use { reader.copyTo(it, 64 * 1024) } }
            }
        } catch (e: EncryptedBackupException) {
            plain.delete()
            sealed.delete()
            val problem = when (e.problem) {
                EncryptedBackupException.Problem.UNSUPPORTED_VERSION -> CloudRestoreException.Problem.NEWER_APP
                EncryptedBackupException.Problem.WRONG_KEY -> CloudRestoreException.Problem.OTHER_KEY
                else -> CloudRestoreException.Problem.DAMAGED
            }
            throw CloudRestoreException(problem, e.message ?: e.problem.name, e)
        }
        sealed.delete()
        plain
    }

    /**
     * Saves into the photos folder every cloud photo of the business the phone lacks. Safe to run again after a cut:
     * photos already saved are skipped. Network failures throw [CloudBackupException]; the photos saved so far stay.
     */
    suspend fun fetchPhotos(
        account: String,
        businessId: String,
        key: BackupKey,
        onProgress: (CloudRestoreProgress) -> Unit = {},
    ): PhotoFetch = withContext(Dispatchers.IO) {
        val provider = providerFor(account)
        val nameKey = PhotoNames.nameKey(key)
        imagesDir.mkdirs()
        val onPhone = imagesDir.listFiles().orEmpty()
            .filter { it.name.endsWith(".jpg") }
            .mapTo(HashSet()) { PhotoNames.name(nameKey, it.name.removeSuffix(".jpg")) }
        val blobs = provider.listBlobs(businessId)
        var fetched = 0
        var alreadyHere = 0
        var rejected = 0
        blobs.forEachIndexed { index, blob ->
            onProgress(CloudRestoreProgress.Photos(index, blobs.size))
            if (blob.name in onPhone) {
                alreadyHere++
                return@forEachIndexed
            }
            val temp = File(imagesDir, ".cloud-${safeName(blob.name)}.tmp")
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                try {
                    provider.openBlob(blob).use { input ->
                        EncryptedBackupFile.reader(input, key).use { reader ->
                            DigestInputStream(reader, digest).use { checked -> temp.outputStream().use { checked.copyTo(it, 64 * 1024) } }
                        }
                    }
                } catch (e: EncryptedBackupException) {
                    rejected++
                    return@forEachIndexed
                } catch (e: CloudBackupException) {
                    throw e
                } catch (e: IOException) {
                    throw CloudBackupException(CloudBackupException.Problem.NETWORK, e.message ?: "photo download cut", e)
                }
                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                if (PhotoNames.name(nameKey, sha256) != blob.name) {
                    rejected++
                    return@forEachIndexed
                }
                val target = File(imagesDir, "$sha256.jpg")
                if (target.isFile || temp.renameTo(target)) fetched++ else throw IOException("cannot save photo $sha256")
            } finally {
                temp.delete()
            }
        }
        onProgress(CloudRestoreProgress.Photos(blobs.size, blobs.size))
        PhotoFetch(fetched, alreadyHere, rejected)
    }

    /** Removes the restore's staging files (after the restore was scheduled, or given up). */
    fun clear() {
        stagingDir.deleteRecursively()
    }

    private fun safeName(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
}
