package com.distrigo.app.data.backup.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.time.Instant

/**
 * Linking a cloud account to this phone's business (plan §3.3, §6), after Google's consent:
 * - the account has no key ring for the business yet: [createKey] makes the key, seals it with the password and a new
 *   recovery code, sends the ring, and returns the code to show once;
 * - it has one (an earlier link, another phone): [join] opens it with the password or the recovery code.
 *
 * Either way the key ends up in this phone's Keystore-sealed store and the state says linked. The screens then call
 * [CloudBackupScheduler.apply] and [CloudBackupScheduler.backupNow].
 */
class CloudBackupLink(
    private val store: CloudBackupStore,
    private val keys: BackupKeyStore,
    private val providerFor: (account: String) -> CloudBackupProvider,
    private val clock: () -> Instant = Instant::now,
    private val random: SecureRandom = SecureRandom(),
    private val passwordIterations: Int = BackupKeyRing.PASSWORD_ITERATIONS,
    private val recoveryIterations: Int = BackupKeyRing.RECOVERY_ITERATIONS,
) {

    /** The account's key ring for [businessId], or null if it has none. Throws [BackupKeyRingException] if unreadable. */
    suspend fun existingRing(account: String, businessId: String): BackupKeyRing? =
        providerFor(account).readKeyRing(businessId)?.let { BackupKeyRing.fromJson(String(it, Charsets.UTF_8)) }

    /** A new key for [businessId]; returns the recovery code, formatted, to show the user once. */
    suspend fun createKey(account: String, businessId: String, password: String): String = withContext(Dispatchers.Default) {
        val key = BackupKey.generate(random)
        val code = RecoveryCode.generate(random)
        val ring = BackupKeyRing.create(
            businessId, key, password, code, clock().toEpochMilli(), random, passwordIterations, recoveryIterations,
        )
        // On the account first: a ring only on the phone could never open a backup elsewhere.
        providerFor(account).writeKeyRing(businessId, ring.toJson().toByteArray(Charsets.UTF_8))
        withContext(Dispatchers.IO) { keep(account, businessId, key, ring) }
        code
    }

    /** Opens [ring] with the password or the recovery code. False when [secret] is neither. */
    suspend fun join(account: String, ring: BackupKeyRing, secret: String): Boolean = withContext(Dispatchers.Default) {
        val key = ring.unlockWithPassword(secret) ?: ring.unlockWithRecoveryCode(secret) ?: return@withContext false
        withContext(Dispatchers.IO) { keep(account, ring.businessId, key, ring) }
        true
    }

    /** « Changer le mot de passe »: the same key and recovery code, so every backup stays readable. */
    suspend fun changePassword(newPassword: String) = withContext(Dispatchers.Default) {
        val state = store.read()
        val account = checkNotNull(state.account) { "Not linked" }
        val businessId = checkNotNull(state.businessId) { "Not linked" }
        val key = checkNotNull(withContext(Dispatchers.IO) { keys.loadKey(businessId) }) { "No key on this phone" }
        val ring = checkNotNull(withContext(Dispatchers.IO) { keys.loadKeyRing(businessId) }) { "No key ring on this phone" }
        val changed = ring.withNewPassword(key, newPassword, random)
        providerFor(account).writeKeyRing(businessId, changed.toJson().toByteArray(Charsets.UTF_8))
        withContext(Dispatchers.IO) { keys.saveKeyRing(changed) }
    }

    /**
     * « Délier »: the phone forgets the key, the ring and the link. The backups and the ring stay in the account, still
     * restorable with the password or the code. The settings (Wi-Fi only, photos) are kept for a later link.
     */
    fun forget() {
        keys.clear()
        store.update {
            CloudBackupState(enabled = it.enabled, allowMobileData = it.allowMobileData, includePhotos = it.includePhotos)
        }
    }

    private fun keep(account: String, businessId: String, key: BackupKey, ring: BackupKeyRing) {
        keys.saveKey(businessId, key)
        keys.saveKeyRing(ring)
        store.update {
            it.copy(
                linked = true,
                providerId = providerFor(account).id,
                account = account,
                businessId = businessId,
                linkedAt = clock(),
                // A new link is a new start: nothing recorded about another account's backups applies.
                lastFingerprint = null,
                lastAt = null,
                lastEntryId = null,
                lastSize = null,
                problemStreak = 0,
                lastProblem = null,
                pending = null,
            )
        }
    }
}
