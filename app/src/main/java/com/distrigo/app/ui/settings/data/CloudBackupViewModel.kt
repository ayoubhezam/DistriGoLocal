package com.distrigo.app.ui.settings.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.distrigo.app.R
import com.distrigo.app.data.backup.cloud.BackupKey
import com.distrigo.app.data.backup.cloud.BackupKeyRing
import com.distrigo.app.data.backup.cloud.BackupKeyRingException
import com.distrigo.app.data.backup.cloud.BackupKeyStore
import com.distrigo.app.data.backup.cloud.BackupPassword
import com.distrigo.app.data.backup.cloud.CloudBackupEntry
import com.distrigo.app.data.backup.cloud.CloudBackupException
import com.distrigo.app.data.backup.cloud.CloudBackupLink
import com.distrigo.app.data.backup.cloud.CloudBackupProblem
import com.distrigo.app.data.backup.cloud.CloudBackupScheduler
import com.distrigo.app.data.backup.cloud.CloudBackupState
import com.distrigo.app.data.backup.cloud.CloudBackupStore
import com.distrigo.app.data.backup.cloud.CloudBackupWorker
import com.distrigo.app.data.backup.cloud.CloudRestore
import com.distrigo.app.data.backup.cloud.CloudRestoreException
import com.distrigo.app.data.backup.cloud.CloudRestoreProgress
import com.distrigo.app.data.backup.cloud.DriveAuthorizer
import com.distrigo.app.data.device.DeviceIdentity
import com.distrigo.app.data.license.AppMetaDataBusiness
import com.distrigo.app.data.local.database.AppDatabase
import com.distrigo.app.di.CloudBackupProviders
import com.distrigo.app.ui.settings.settingsText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

/** What the screen shows, from the state file and the phone. */
data class CloudStatus(
    val loaded: Boolean = false,
    /** Google Play services: without them, no Drive. */
    val available: Boolean = true,
    /** The data's business (`app_meta.business_id`); null before any license. */
    val businessId: String? = null,
    val state: CloudBackupState = CloudBackupState(),
)

/** A backup in the restore list. */
data class CloudBackupChoice(val entry: CloudBackupEntry, val openable: Boolean, val fromThisPhone: Boolean) {
    val at: Instant get() = Instant.ofEpochMilli(entry.meta.createdAtMillis)
}

/** A step that takes the screen over: a dialog or a page of its own. */
sealed class CloudStep {
    data class CreatePassword(val account: String) : CloudStep()
    data class RecoveryCode(val code: String) : CloudStep()

    /** [purpose]: open an existing ring to link, to restore, or because this phone lost its key. */
    data class EnterSecret(val purpose: SecretPurpose) : CloudStep()
    data object ChangePassword : CloudStep()
    data object ConfirmUnlink : CloudStep()
    data object ConfirmChangeAccount : CloudStep()
    data class ChooseBackup(val choices: List<CloudBackupChoice>) : CloudStep()

    /** A restore downloading: [label] is its step. */
    data class Restoring(val label: String) : CloudStep()
}

enum class SecretPurpose { JOIN, RESTORE, RELOCK }

/** « Sauvegarder maintenant » or the nightly job, as WorkManager reports it. */
data class CloudJob(val label: String, val waiting: Boolean)

/** One thing to tell, once, above the cards. */
data class CloudMessage(val text: String, val isError: Boolean)

/** Why Google's consent screen is being shown, so its answer goes on with the right thing. */
private enum class ConsentFor(val flow: Flow) { LINK(Flow.LINK), RESTORE(Flow.RESTORE), RECONNECT(Flow.RECONNECT) }

/** What the user was doing when something failed: the message names it (« n'a pas pu être lié »…). */
private enum class Flow { LINK, RESTORE, RECONNECT, OTHER }

/**
 * Paramètres › Sauvegarde › Sauvegarde Google Drive (docs/cloud_backup_drive_plan.md §6, §3.7): link, the settings,
 * « Sauvegarder maintenant », restore, change of password or account, unlink.
 *
 * A restore ends here with a plain `.distrigo` file, handed to the Sauvegarde screen's own restore (preview, stock
 * rule, safety backup, restart) through [restoreFile]. Nothing here starts a backup while a restore is under way: a new
 * phone linked to restore must not send its still-empty data first.
 */
@HiltViewModel
class CloudBackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: CloudBackupStore,
    private val keys: BackupKeyStore,
    private val link: CloudBackupLink,
    private val restore: CloudRestore,
    private val scheduler: CloudBackupScheduler,
    private val authorizer: DriveAuthorizer,
    private val providers: CloudBackupProviders,
    private val db: AppDatabase,
) : ViewModel() {

    private val _status = MutableStateFlow(CloudStatus())
    val status: StateFlow<CloudStatus> = _status.asStateFlow()

    private val _step = MutableStateFlow<CloudStep?>(null)
    val step: StateFlow<CloudStep?> = _step.asStateFlow()

    /** Working on something the user waits for (a dialog's button spins). */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** The open dialog's error, under its fields. */
    private val _fieldError = MutableStateFlow<String?>(null)
    val fieldError: StateFlow<String?> = _fieldError.asStateFlow()

    private val _message = MutableStateFlow<CloudMessage?>(null)
    val message: StateFlow<CloudMessage?> = _message.asStateFlow()

    private val _job = MutableStateFlow<CloudJob?>(null)
    val job: StateFlow<CloudJob?> = _job.asStateFlow()

    /** Google's consent screen, for the screen to launch. */
    private val _consent = MutableSharedFlow<PendingIntent>(extraBufferCapacity = 1)
    val consent: SharedFlow<PendingIntent> = _consent

    /** A restore is ready: the screen hands this file to the Sauvegarde screen's restore. */
    private val _restoreFile = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val restoreFile: SharedFlow<Uri> = _restoreFile

    /** Linked: the screen asks, once, to be allowed to notify (the cloud alerts need it). */
    private val _askNotifications = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val askNotifications: SharedFlow<Unit> = _askNotifications

    private var consentFor = ConsentFor.LINK
    private var pendingAccount: String? = null
    private var pendingRing: BackupKeyRing? = null
    private var restoreKey: BackupKey? = null
    private var restoreAccount: String? = null
    private val business = AppMetaDataBusiness { db }

    init {
        refresh()
        watchJobs()
    }

    fun refresh() {
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) {
                CloudStatus(loaded = true, available = authorizer.isAvailable(), businessId = business.read(), state = store.read())
            }
            _status.value = status
        }
    }

    fun dismissMessage() {
        _message.value = null
    }

    /** Closes the open step. Never while a restore downloads (its file would be left half-made). */
    fun dismissStep() {
        if (_step.value is CloudStep.Restoring || _busy.value) return
        _step.value = null
        _fieldError.value = null
        pendingRing = null
    }

    // ── Linking ──

    fun startLink() = consent(ConsentFor.LINK, account = null)

    fun startRestore() {
        val state = _status.value.state
        val businessId = state.businessId
        val account = state.account
        if (state.linked && account != null && businessId != null) {
            work(flow = Flow.RESTORE) {
                val key = withContext(Dispatchers.IO) { keys.loadKey(businessId) }
                val ring = withContext(Dispatchers.IO) { keys.loadKeyRing(businessId) } ?: restore.keyRing(account, businessId)
                if (key == null) {
                    // The key is lost on this phone: the password opens it again, then the list.
                    pendingAccount = account
                    pendingRing = ring
                    _step.value = CloudStep.EnterSecret(SecretPurpose.RESTORE)
                } else {
                    showBackups(account, ring, key)
                }
            }
        } else {
            consent(ConsentFor.RESTORE, account = null)
        }
    }

    fun reconnect() = consent(ConsentFor.RECONNECT, account = _status.value.state.account)

    private fun consent(purpose: ConsentFor, account: String?) {
        consentFor = purpose
        if (purpose != ConsentFor.RECONNECT && _status.value.businessId == null) {
            _message.value = CloudMessage(CloudBackupProblem.NO_BUSINESS.message, isError = true)
            return
        }
        Log.i(TAG, "${purpose.name.lowercase()}: asking Google for drive.appdata")
        work(flow = purpose.flow) { onAuthorization(authorizer.authorize(account), step = "authorize") }
    }

    /** Google's consent screen answered. Closed by the user: nothing to say. */
    fun onConsentResult(resultCode: Int, data: Intent?) {
        work(flow = consentFor.flow) { onAuthorization(authorizer.consentResult(resultCode, data), step = "consent screen") }
    }

    private suspend fun onAuthorization(outcome: DriveAuthorizer.Outcome, step: String) {
        Log.i(TAG, "${consentFor.name.lowercase()}: $step answered ${outcome.javaClass.simpleName}")
        when (outcome) {
            is DriveAuthorizer.Outcome.Granted -> afterConsent(outcome.accessToken)
            is DriveAuthorizer.Outcome.NeedsConsent -> _consent.emit(outcome.intent)
            DriveAuthorizer.Outcome.Unavailable -> _message.value = CloudMessage(settingsText(R.string.cloud_unavailable), true)
            DriveAuthorizer.Outcome.Cancelled -> Unit
            is DriveAuthorizer.Outcome.Failed -> {
                Log.w(TAG, "${consentFor.name.lowercase()}: Google refused at $step: ${outcome.message} (code ${outcome.statusCode})")
                val reason = if (outcome.problem == CloudBackupException.Problem.NETWORK) {
                    settingsText(R.string.cloud_reason_network)
                } else {
                    settingsText(R.string.cloud_reason_refused, outcome.statusCode ?: 0)
                }
                _message.value = CloudMessage(failureText(consentFor.flow, reason), true)
            }
        }
    }

    private suspend fun afterConsent(token: String) {
        if (consentFor == ConsentFor.RECONNECT) {
            scheduler.backupNow()
            _message.value = CloudMessage(settingsText(R.string.cloud_reconnected), false)
            return
        }
        Log.i(TAG, "${consentFor.name.lowercase()}: token received; reading the account's email from Drive")
        val account = providers.emailFor(token)
        if (account.isNullOrBlank()) {
            Log.w(TAG, "${consentFor.name.lowercase()}: Drive gave no email")
            _message.value = CloudMessage(failureText(consentFor.flow, settingsText(R.string.cloud_error_no_account)), true)
            return
        }
        val businessId = _status.value.businessId ?: return
        pendingAccount = account
        Log.i(TAG, "${consentFor.name.lowercase()}: email read; looking for the business's key ring on Drive")
        val ring = try {
            link.existingRing(account, businessId)
        } catch (e: BackupKeyRingException) {
            Log.w(TAG, "${consentFor.name.lowercase()}: key ring unreadable", e)
            _message.value = CloudMessage(settingsText(if (e.unsupportedVersion) R.string.cloud_error_newer else R.string.cloud_error_damaged), true)
            return
        }
        Log.i(TAG, "${consentFor.name.lowercase()}: key ring ${if (ring == null) "not found: new key" else "found: asking for the password"}")
        when {
            ring != null -> {
                pendingRing = ring
                _step.value = CloudStep.EnterSecret(if (consentFor == ConsentFor.RESTORE) SecretPurpose.RESTORE else SecretPurpose.JOIN)
            }
            consentFor == ConsentFor.RESTORE -> _message.value = CloudMessage(settingsText(R.string.cloud_no_backups), true)
            else -> _step.value = CloudStep.CreatePassword(account)
        }
    }

    fun submitNewPassword(password: String, again: String) {
        val account = pendingAccount ?: return
        val businessId = _status.value.businessId ?: return
        if (!BackupPassword.isLongEnough(password)) {
            _fieldError.value = settingsText(R.string.cloud_password_short, BackupPassword.MIN_LENGTH)
            return
        }
        if (password != again) {
            _fieldError.value = settingsText(R.string.cloud_password_mismatch)
            return
        }
        _fieldError.value = null
        work(inDialog = true, flow = Flow.LINK) {
            val code = link.createKey(account, businessId, password)
            _step.value = CloudStep.RecoveryCode(code)
        }
    }

    /** « J'ai noté le code »: the link is done, the first backup starts. */
    fun recoveryCodeNoted() {
        _step.value = null
        linked()
    }

    fun submitSecret(secret: String) {
        val step = _step.value as? CloudStep.EnterSecret ?: return
        val state = _status.value.state
        val account = pendingAccount ?: state.account ?: return
        _fieldError.value = null
        work(inDialog = true, flow = when (step.purpose) { SecretPurpose.JOIN -> Flow.LINK; SecretPurpose.RESTORE -> Flow.RESTORE; SecretPurpose.RELOCK -> Flow.OTHER }) {
            val ring = pendingRing ?: run {
                val businessId = state.businessId ?: _status.value.businessId ?: return@work
                withContext(Dispatchers.IO) { keys.loadKeyRing(businessId) } ?: restore.keyRing(account, businessId)
            }
            when (step.purpose) {
                SecretPurpose.JOIN, SecretPurpose.RELOCK -> {
                    if (!link.join(account, ring, secret)) {
                        _fieldError.value = settingsText(R.string.cloud_secret_wrong)
                        return@work
                    }
                    _step.value = null
                    pendingRing = null
                    linked()
                }
                SecretPurpose.RESTORE -> {
                    val key = restore.unlock(ring, secret)
                    if (key == null) {
                        _fieldError.value = settingsText(R.string.cloud_secret_wrong)
                        return@work
                    }
                    // Linked under the same key, but no backup now: the restore comes first (see the class note).
                    link.join(account, ring, secret)
                    pendingRing = null
                    refreshNow()
                    showBackups(account, ring, key)
                }
            }
        }
    }

    /** After a new link: the job matches the state, the first backup goes, notifications are asked for. */
    private fun linked() {
        scheduler.apply()
        scheduler.backupNow()
        _message.value = CloudMessage(settingsText(R.string.cloud_linked_done), false)
        _askNotifications.tryEmit(Unit)
        refresh()
    }

    // ── Settings and actions ──

    fun setAuto(on: Boolean) = updateSettings { it.copy(enabled = on) }
    fun setMobileData(on: Boolean) = updateSettings { it.copy(allowMobileData = on) }
    fun setPhotos(on: Boolean) = updateSettings { it.copy(includePhotos = on) }

    private fun updateSettings(change: (CloudBackupState) -> CloudBackupState) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                store.update(change)
                scheduler.apply()
            }
            refresh()
        }
    }

    fun backupNow() {
        scheduler.backupNow()
        _message.value = CloudMessage(settingsText(R.string.cloud_backup_started), false)
    }

    fun askChangePassword() {
        _fieldError.value = null
        _step.value = CloudStep.ChangePassword
    }

    fun changePassword(current: String, new: String, again: String) {
        val businessId = _status.value.state.businessId ?: return
        if (!BackupPassword.isLongEnough(new)) {
            _fieldError.value = settingsText(R.string.cloud_password_short, BackupPassword.MIN_LENGTH)
            return
        }
        if (new != again) {
            _fieldError.value = settingsText(R.string.cloud_password_mismatch)
            return
        }
        _fieldError.value = null
        work(inDialog = true) {
            val ring = withContext(Dispatchers.IO) { keys.loadKeyRing(businessId) }
            // The current password, so a phone left on the table cannot have its password changed.
            val opens = ring != null && withContext(Dispatchers.Default) { ring.unlockWithPassword(current) } != null
            if (!opens) {
                _fieldError.value = settingsText(R.string.cloud_current_wrong)
                return@work
            }
            link.changePassword(new)
            _step.value = null
            _message.value = CloudMessage(settingsText(R.string.cloud_password_changed), false)
        }
    }

    /** « Entrer le mot de passe »: this phone lost its key; the ring (here or in the account) opens it again. */
    fun askRelock() {
        pendingAccount = _status.value.state.account
        pendingRing = null
        _fieldError.value = null
        _step.value = CloudStep.EnterSecret(SecretPurpose.RELOCK)
    }

    fun askUnlink() {
        _step.value = CloudStep.ConfirmUnlink
    }

    fun askChangeAccount() {
        _step.value = CloudStep.ConfirmChangeAccount
    }

    fun unlink() = forget(then = { _message.value = CloudMessage(settingsText(R.string.cloud_unlinked), false) })

    /** The old account keeps its backups; the new one is linked from scratch and gets a full backup at once. */
    fun changeAccount() = forget(then = { consent(ConsentFor.LINK, account = null) })

    private fun forget(then: () -> Unit) {
        val account = _status.value.state.account
        work(inDialog = true) {
            if (account != null) {
                (authorizer.authorize(account) as? DriveAuthorizer.Outcome.Granted)?.let { providers.revoke(it.accessToken) }
            }
            withContext(Dispatchers.IO) {
                link.forget()
                scheduler.apply()
            }
            _step.value = null
            refreshNow()
            then()
        }
    }

    // ── Restore ──

    private suspend fun showBackups(account: String, ring: BackupKeyRing, key: BackupKey) {
        val thisPhone = withContext(Dispatchers.IO) { DeviceIdentity.id(context) }
        val choices = restore.backups(account, ring).map { (entry, openable) ->
            CloudBackupChoice(entry, openable, entry.meta.deviceId == thisPhone)
        }
        if (choices.isEmpty()) {
            _step.value = null
            _message.value = CloudMessage(settingsText(R.string.cloud_no_backups), true)
            return
        }
        restoreAccount = account
        restoreKey = key
        _step.value = CloudStep.ChooseBackup(choices)
    }

    fun restoreBackup(choice: CloudBackupChoice) {
        val account = restoreAccount ?: return
        val key = restoreKey ?: return
        if (!choice.openable) return
        _step.value = CloudStep.Restoring(settingsText(R.string.cloud_downloading, 0))
        viewModelScope.launch {
            try {
                val file = restore.download(account, choice.entry, key, ::restoreProgress)
                restore.fetchPhotos(account, choice.entry.meta.businessId, key, ::restoreProgress)
                _step.value = null
                _restoreFile.emit(Uri.fromFile(file))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "restore download failed: ${e.javaClass.simpleName}: ${e.message} ${(e as? CloudBackupException)?.detail ?: ""}", e)
                _step.value = null
                _message.value = CloudMessage(errorText(e, Flow.RESTORE), true)
            }
        }
    }

    private fun restoreProgress(progress: CloudRestoreProgress) {
        _step.value = CloudStep.Restoring(
            when (progress) {
                is CloudRestoreProgress.Downloading ->
                    settingsText(R.string.cloud_downloading, if (progress.total > 0) (progress.received * 100 / progress.total).toInt() else 0)
                CloudRestoreProgress.Decrypting -> settingsText(R.string.cloud_decrypting)
                is CloudRestoreProgress.Photos -> settingsText(R.string.cloud_step_photos, progress.done, progress.total)
            },
        )
    }

    // ── Jobs ──

    private fun watchJobs() {
        val workManager = WorkManager.getInstance(context)
        viewModelScope.launch {
            combine(
                workManager.getWorkInfosForUniqueWorkFlow(CloudBackupScheduler.NOW_NAME),
                workManager.getWorkInfosForUniqueWorkFlow(CloudBackupScheduler.WORK_NAME),
            ) { now, daily -> (now + daily).firstOrNull { it.state == WorkInfo.State.RUNNING } ?: now.firstOrNull { it.state == WorkInfo.State.ENQUEUED } }
                .collect { info ->
                    val previous = _job.value
                    _job.value = info?.let(::jobOf)
                    // A run just ended: the state file has its outcome.
                    if (previous != null && _job.value == null) refresh()
                }
        }
    }

    private fun jobOf(info: WorkInfo): CloudJob {
        if (info.state == WorkInfo.State.ENQUEUED) {
            val wifi = !_status.value.state.allowMobileData
            return CloudJob(settingsText(if (wifi) R.string.cloud_step_waiting_wifi else R.string.cloud_step_waiting_network), waiting = true)
        }
        val data = info.progress
        val sent = data.getLong(CloudBackupWorker.KEY_SENT, 0)
        val total = data.getLong(CloudBackupWorker.KEY_TOTAL, 0)
        val label = when (data.getString(CloudBackupWorker.KEY_STEP)) {
            CloudBackupWorker.STEP_ENCRYPTING -> settingsText(R.string.cloud_step_encrypting)
            CloudBackupWorker.STEP_PHOTOS -> settingsText(R.string.cloud_step_photos, sent.toInt(), total.toInt())
            CloudBackupWorker.STEP_UPLOADING -> settingsText(R.string.cloud_step_uploading, if (total > 0) (sent * 100 / total).toInt() else 0)
            else -> settingsText(R.string.cloud_step_preparing)
        }
        return CloudJob(label, waiting = false)
    }

    // ── Helpers ──

    private suspend fun refreshNow() {
        _status.value = withContext(Dispatchers.IO) {
            CloudStatus(loaded = true, available = authorizer.isAvailable(), businessId = business.read(), state = store.read())
        }
    }

    /**
     * Runs [block], spinning; a failure becomes the message (or the dialog's error when [inDialog]), worded for [flow]:
     * a link that fails says it could not link, never that a backup could not be sent.
     */
    private fun work(inDialog: Boolean = false, flow: Flow = Flow.OTHER, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val detail = (e as? CloudBackupException)?.let { " [${it.problem}${it.detail?.let { d -> ", $d" } ?: ""}]" } ?: ""
                Log.w(TAG, "${flow.name.lowercase()} failed: ${e.javaClass.simpleName}: ${e.message}$detail", e)
                val text = errorText(e, flow)
                if (inDialog) _fieldError.value = text else _message.value = CloudMessage(text, true)
            } finally {
                _busy.value = false
            }
        }
    }

    /** « Google Drive n'a pas pu être lié : <reason> », and the like for the other flows. */
    private fun failureText(flow: Flow, reason: String): String = when (flow) {
        Flow.LINK -> settingsText(R.string.cloud_link_failed, reason)
        Flow.RESTORE -> settingsText(R.string.cloud_restore_failed, reason)
        Flow.RECONNECT -> settingsText(R.string.cloud_reconnect_failed, reason)
        Flow.OTHER -> settingsText(R.string.cloud_action_failed, reason)
    }

    private fun errorText(e: Exception, flow: Flow): String = when (e) {
        is CloudBackupException -> when (e.problem) {
            CloudBackupException.Problem.UNAVAILABLE -> settingsText(R.string.cloud_unavailable)
            CloudBackupException.Problem.NETWORK, CloudBackupException.Problem.SERVER, CloudBackupException.Problem.RATE_LIMITED ->
                failureText(flow, settingsText(R.string.cloud_reason_network))
            CloudBackupException.Problem.QUOTA_EXCEEDED -> failureText(flow, settingsText(R.string.cloud_reason_full))
            else -> failureText(flow, settingsText(R.string.cloud_reason_drive, e.detail ?: e.problem.name))
        }
        is CloudRestoreException -> settingsText(
            when (e.problem) {
                CloudRestoreException.Problem.NO_KEY_RING -> R.string.cloud_no_backups
                CloudRestoreException.Problem.NEWER_APP -> R.string.cloud_error_newer
                CloudRestoreException.Problem.OTHER_KEY -> R.string.cloud_error_other_key
                CloudRestoreException.Problem.KEY_RING_DAMAGED, CloudRestoreException.Problem.DAMAGED -> R.string.cloud_error_damaged
            },
        )
        is BackupKeyRingException -> settingsText(if (e.unsupportedVersion) R.string.cloud_error_newer else R.string.cloud_error_damaged)
        else -> CloudBackupProblem.UNEXPECTED.message
    }

    private companion object {
        const val TAG = "CloudBackupViewModel"
    }
}
