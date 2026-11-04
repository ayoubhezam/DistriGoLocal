package com.distrigo.app.data.backup.cloud

import android.util.Log
import com.distrigo.app.data.backup.BackupFailedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId

/** The data a cloud backup is taken from. On the phone: [AppCloudBackupSource]; in tests, a few lines. */
interface CloudBackupSource {
    /** The business the data belongs to (`app_meta.business_id`), or null before any license. */
    fun businessId(): String?

    /** [com.distrigo.app.data.backup.DataFingerprint] of the live data, to skip a day without changes. */
    fun fingerprint(): String

    /** Writes a `.distrigo` of the data into [file]. Throws [BackupFailedException] like `BackupCreator`. */
    fun create(file: File): PlainBackup
}

/** What [CloudBackupSource.create] wrote. */
data class PlainBackup(
    /** Of the copy that was saved, not of the live data after it (see `CreatedBackup.fingerprint`). */
    val fingerprint: String,
    val schemaVersion: Int,
    val deviceId: String,
    val createdAt: Instant,
)

sealed class CloudBackupOutcome {
    /** No cloud backup is linked on this phone. */
    data object NotLinked : CloudBackupOutcome()

    /** Nothing changed since the last backup, which is still in the cloud. */
    data object Unchanged : CloudBackupOutcome()

    /** A backup is in the cloud. [resumed]: it was one a previous run had started. [removed]: old ones deleted. */
    data class Saved(val entry: CloudBackupEntry, val resumed: Boolean, val removed: Int) : CloudBackupOutcome()

    /** [retryable]: worth trying again soon (network); otherwise the next scheduled run, or the user, fixes it. */
    data class Failed(val problem: CloudBackupProblem, val retryable: Boolean, val detail: String) : CloudBackupOutcome()
}

/** How far a run is, for « Sauvegarder maintenant »'s progress. */
sealed class CloudBackupProgress {
    data object Preparing : CloudBackupProgress()
    data object Encrypting : CloudBackupProgress()
    data class Uploading(val sent: Long, val total: Long) : CloudBackupProgress()
}

/**
 * One cloud backup run (plan §3.4–3.6):
 * 1. a backup a previous run sealed but could not finish sending is resumed first;
 * 2. a day without changes is skipped, once the last backup is seen still in the cloud (the user may have deleted
 *    the app's hidden data in Drive);
 * 3. otherwise the data is backed up, sealed with the business's key, and sent; the sealed file and the provider's
 *    session are kept until the provider holds it whole, so a cut connection or a killed app resumes;
 * 4. after a confirmed upload only: the key ring is put back if it is missing, and old backups beyond the retention
 *    rule are deleted. Neither can undo the backup just made: their failures wait for the next run.
 *
 * Nothing here reads a clock or a zone of its own, so tests drive days and weeks.
 */
class CloudBackupEngine(
    private val store: CloudBackupStore,
    private val keys: BackupKeyStore,
    private val providerFor: (account: String) -> CloudBackupProvider,
    private val source: CloudBackupSource,
    private val stagingDir: File,
    private val appVersion: String,
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val random: SecureRandom = SecureRandom(),
    private val segmentSize: Int = EncryptedBackupFile.DEFAULT_SEGMENT_SIZE,
    /** Android's log on the phone; tests on the JVM pass their own. */
    private val warn: (String, Throwable?) -> Unit = { message, e -> Log.w(TAG, message, e) },
) {

    /** [force]: back up even when nothing changed (« Sauvegarder maintenant »). One run at a time. */
    suspend fun run(force: Boolean = false, onProgress: (CloudBackupProgress) -> Unit = {}): CloudBackupOutcome =
        LOCK.withLock {
            withContext(Dispatchers.IO) {
                store.update { it.copy(lastAttemptAt = clock()) }
                try {
                    attempt(force, onProgress)
                } catch (e: CancellationException) {
                    // WorkManager stopped the job (time limit, constraint lost): the pending upload resumes next time.
                    throw e
                } catch (e: CloudBackupException) {
                    if (e.problem == CloudBackupException.Problem.UPLOAD_CORRUPTED) {
                        // The bad copy is gone; its finished session would only return it again.
                        store.update { s -> s.copy(pending = s.pending?.copy(session = null)) }
                    }
                    CloudBackupOutcome.Failed(CloudBackupProblem.of(e.problem), e.problem.retryable, e.message ?: e.problem.name)
                } catch (e: BackupFailedException) {
                    CloudBackupOutcome.Failed(CloudBackupProblem.of(e.reason), false, e.message ?: e.reason.name)
                } catch (e: IOException) {
                    // Staging: the phone's own storage refused the sealed copy.
                    warn("cloud backup could not be prepared", e)
                    CloudBackupOutcome.Failed(CloudBackupProblem.NO_SPACE, false, e.message ?: "staging")
                }
            }
        }

    /**
     * Records a run's final outcome: the problem streak the alerts read. Called once per run, not per retry, so a
     * night of retries counts as one failure.
     */
    fun record(outcome: CloudBackupOutcome, scheduled: Boolean): CloudBackupState = store.update {
        val problem = (outcome as? CloudBackupOutcome.Failed)?.problem
        it.copy(
            problemStreak = if (problem == null) 0 else it.problemStreak + 1,
            lastProblem = problem,
            lastScheduledRunAt = if (scheduled) clock() else it.lastScheduledRunAt,
        )
    }

    private suspend fun attempt(force: Boolean, onProgress: (CloudBackupProgress) -> Unit): CloudBackupOutcome {
        val state = store.read()
        val account = state.account
        if (!state.linked || account == null) return CloudBackupOutcome.NotLinked
        val businessId = source.businessId() ?: return failed(CloudBackupProblem.NO_BUSINESS, "no business")
        if (businessId != state.businessId) return failed(CloudBackupProblem.OTHER_BUSINESS, "linked for another business")
        val key = keys.loadKey(businessId) ?: return failed(CloudBackupProblem.NEEDS_PASSWORD, "no key on this phone")
        val provider = providerFor(account)

        state.pending?.let { pending ->
            val sealed = File(stagingDir, pending.fileName)
            if (pending.keyId == key.idText && sealed.isFile) return upload(provider, businessId, pending, sealed, resumed = true, onProgress)
            store.update { it.copy(pending = null) }
        }

        val fingerprint = source.fingerprint()
        if (!force && fingerprint == state.lastFingerprint && state.lastEntryId != null) {
            if (provider.listBackups(businessId).any { it.id == state.lastEntryId }) {
                afterUpload(provider, businessId, justSent = null)
                return CloudBackupOutcome.Unchanged
            }
            warn("the last cloud backup is gone: backing up again", null)
        }

        onProgress(CloudBackupProgress.Preparing)
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()
        val plain = File(stagingDir, "backup.distrigo")
        val created = source.create(plain)
        onProgress(CloudBackupProgress.Encrypting)
        val sealed = File(stagingDir, "backup-${created.createdAt.toEpochMilli()}.${EncryptedBackupFile.EXTENSION}")
        val plainSize = plain.length()
        try {
            plain.inputStream().use { input ->
                EncryptedBackupFile.writer(sealed.outputStream(), key, segmentSize, random).use { input.copyTo(it, 64 * 1024) }
            }
        } catch (e: Exception) {
            // Only a whole sealed file is ever recorded as pending.
            sealed.delete()
            throw e
        } finally {
            plain.delete()
        }
        val pending = PendingUpload(
            fileName = sealed.name,
            fingerprint = created.fingerprint,
            createdAtMillis = created.createdAt.toEpochMilli(),
            schemaVersion = created.schemaVersion,
            deviceId = created.deviceId,
            plainSize = plainSize,
            keyId = key.idText,
        )
        store.update { it.copy(pending = pending) }
        return upload(provider, businessId, pending, sealed, resumed = false, onProgress)
    }

    private suspend fun upload(
        provider: CloudBackupProvider,
        businessId: String,
        pending: PendingUpload,
        sealed: File,
        resumed: Boolean,
        onProgress: (CloudBackupProgress) -> Unit,
    ): CloudBackupOutcome {
        val meta = CloudBackupMeta(
            businessId = businessId,
            deviceId = pending.deviceId,
            createdAtMillis = pending.createdAtMillis,
            schemaVersion = pending.schemaVersion,
            appVersion = appVersion,
            plainSize = pending.plainSize,
            keyId = pending.keyId,
        )
        onProgress(CloudBackupProgress.Uploading(0, sealed.length()))
        val entry = provider.uploadBackup(
            sealed,
            meta,
            resume = pending.session,
            onSession = { session -> store.update { s -> s.copy(pending = s.pending?.copy(session = session)) } },
            onProgress = { sent, total -> onProgress(CloudBackupProgress.Uploading(sent, total)) },
        )
        store.update {
            it.copy(
                lastFingerprint = pending.fingerprint,
                lastAt = Instant.ofEpochMilli(pending.createdAtMillis),
                lastEntryId = entry.id,
                lastSize = entry.size,
                pending = null,
            )
        }
        sealed.delete()
        return CloudBackupOutcome.Saved(entry, resumed, afterUpload(provider, businessId, justSent = entry.id))
    }

    /** Puts the key ring back if it is missing, then applies retention. Failures wait for the next run. */
    private suspend fun afterUpload(provider: CloudBackupProvider, businessId: String, justSent: String?): Int {
        try {
            if (provider.readKeyRing(businessId) == null) {
                keys.loadKeyRing(businessId)?.let { provider.writeKeyRing(businessId, it.toJson().toByteArray(Charsets.UTF_8)) }
            }
        } catch (e: CloudBackupException) {
            warn("could not check the key ring", e)
        }
        if (justSent == null) return 0
        return try {
            val old = CloudRetention.toDelete(provider.listBackups(businessId), clock(), zone(), justSent)
            old.count { entry ->
                try {
                    provider.deleteBackup(entry)
                    true
                } catch (e: CloudBackupException) {
                    warn("could not delete an old cloud backup", e)
                    false
                }
            }
        } catch (e: CloudBackupException) {
            warn("could not apply retention", e)
            0
        }
    }

    private fun failed(problem: CloudBackupProblem, detail: String) = CloudBackupOutcome.Failed(problem, false, detail)

    private companion object {
        const val TAG = "CloudBackupEngine"

        /** One run at a time, « maintenant » and the daily job alike. */
        val LOCK = Mutex()
    }
}
