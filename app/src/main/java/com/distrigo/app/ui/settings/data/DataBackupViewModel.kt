package com.distrigo.app.ui.settings.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.distrigo.app.data.backup.BackupCreator
import com.distrigo.app.data.backup.BackupJob
import com.distrigo.app.data.backup.BackupJobResult
import com.distrigo.app.data.backup.BackupJobState
import com.distrigo.app.data.backup.BackupInspection
import com.distrigo.app.data.backup.BackupInspector
import com.distrigo.app.data.backup.BackupMessages
import com.distrigo.app.data.backup.BackupPreview
import com.distrigo.app.data.backup.RestoreCoordinator
import com.distrigo.app.data.backup.RestoreInstaller
import com.distrigo.app.data.backup.RestoreOutcome
import com.distrigo.app.data.backup.RestoreResult
import com.distrigo.app.data.backup.auto.AutoBackupRunner
import com.distrigo.app.data.backup.auto.AutoBackupScheduler
import com.distrigo.app.data.backup.auto.AutoBackupState
import com.distrigo.app.data.backup.auto.PickedFolder
import com.distrigo.app.data.local.database.AppDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import com.distrigo.app.data.backup.PreparedRestore
import com.distrigo.app.data.backup.RestorePreparation
import com.distrigo.app.data.backup.RestoreBusiness
import com.distrigo.app.data.backup.RestoreStockRule
import com.distrigo.app.data.license.LicenseManager
import com.distrigo.app.data.license.heldLicense

/** The last backup made of this data, from `app_meta`. */
data class LastBackup(val at: Instant, val size: Long?, val fileName: String)

/** The folder automatic backups go to, as the screen shows it. */
data class AutoBackupFolder(val chosen: Boolean, val name: String?, val available: Boolean)

/** Everything the automatic backup section shows. */
data class AutoBackupStatus(
    val folder: AutoBackupFolder = AutoBackupFolder(chosen = false, name = null, available = false),
    val state: AutoBackupState = AutoBackupState(),
    /** When WorkManager plans the next run, when on and known. */
    val nextRunAt: Instant? = null,
    /** Whether a failure can be notified; the screen says so when it cannot. */
    val notificationsAllowed: Boolean = true,
    /** On, but the daily job has not run for too long: the phone is probably holding it back. */
    val overdue: Boolean = false,
)

/** A safety backup kept from an earlier restore. */
data class SafetyBackup(val file: File, val at: Instant, val size: Long)

/** What the screen is doing. Only [Idle] lets the user start something or leave. */
sealed class DataBackupState {
    data object Idle : DataBackupState()
    data class Working(val label: String) : DataBackupState()
    /** A backup started here finished while the screen was open; [toFolder] for « Sauvegarder maintenant ». */
    data class BackupSaved(val result: BackupJobResult.Saved, val toFolder: Boolean) : DataBackupState()
    data class Previewing(val preview: BackupPreview, val isSafetyBackup: Boolean) : DataBackupState()
    /**
     * The backup is unpacked and checked; restoring it changes the stock rule, or brings another business's data
     * ([otherBusiness]), and the user decides. Nothing has changed yet.
     */
    data class ConfirmingRestore(
        val restore: PreparedRestore,
        val preview: BackupPreview,
        val rule: RestoreStockRule,
        val otherBusiness: RestoreBusiness? = null,
    ) : DataBackupState()
    /** A restore is scheduled: the app must restart now. */
    data object RestartNeeded : DataBackupState()
    data class Failed(val message: String) : DataBackupState()
}

@HiltViewModel
class DataBackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val autoBackup: AutoBackupRunner,
    private val db: AppDatabase,
    private val inspector: BackupInspector,
    private val coordinator: RestoreCoordinator,
    private val installer: RestoreInstaller,
    private val scheduler: AutoBackupScheduler,
    private val license: LicenseManager,
) : ViewModel() {

    val lastBackup: StateFlow<LastBackup?> =
        db.appMetaDao().observe(listOf(BackupCreator.KEY_LAST_AT, BackupCreator.KEY_LAST_SIZE, BackupCreator.KEY_LAST_NAME))
            .map { rows ->
                val values = rows.associate { it.key to it.value }
                val at = values[BackupCreator.KEY_LAST_AT]?.let { runCatching { Instant.parse(it) }.getOrNull() }
                at?.let { LastBackup(it, values[BackupCreator.KEY_LAST_SIZE]?.toLongOrNull(), values[BackupCreator.KEY_LAST_NAME].orEmpty()) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow<DataBackupState>(DataBackupState.Idle)
    val state: StateFlow<DataBackupState> = _state.asStateFlow()

    private val _counts = MutableStateFlow<Map<String, Long>>(emptyMap())
    val counts: StateFlow<Map<String, Long>> = _counts.asStateFlow()

    private val _safetyBackups = MutableStateFlow<List<SafetyBackup>>(emptyList())
    val safetyBackups: StateFlow<List<SafetyBackup>> = _safetyBackups.asStateFlow()

    private val _lastRestore = MutableStateFlow<RestoreResult?>(null)
    /** How the last restore ended, until the user dismisses it. */
    val lastRestore: StateFlow<RestoreResult?> = _lastRestore.asStateFlow()

    private val _autoStatus = MutableStateFlow(AutoBackupStatus())
    val autoStatus: StateFlow<AutoBackupStatus> = _autoStatus.asStateFlow()

    /**
     * The latest backup started from this screen, running in the background (2026-10-21): the screen shows its
     * step, keeps restoring until it ends, and a failure on its card until the next attempt.
     */
    val job: StateFlow<BackupJobState?> = BackupJob.observe(context).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The jobs seen running here: only those end with a dialog; the others ended while the user was away. */
    private val followed = mutableSetOf<java.util.UUID>()

    init {
        viewModelScope.launch {
            job.collect { current ->
                when (current) {
                    is BackupJobState.Running -> followed += current.id
                    is BackupJobState.Done -> if (followed.remove(current.id)) finished(current)
                    null -> Unit
                }
            }
        }
    }

    /** A job seen running here ended: its dialog, unless the screen is busy with something else, and fresh figures. */
    private fun finished(done: BackupJobState.Done) {
        if (_state.value == DataBackupState.Idle) {
            _state.value = when (val result = done.result) {
                is BackupJobResult.Saved -> DataBackupState.BackupSaved(result, done.toFolder)
                is BackupJobResult.Failed -> DataBackupState.Failed(result.message)
                BackupJobResult.Unchanged -> DataBackupState.Idle
            }
        }
        refresh()
    }

    private val backupRunning: Boolean get() = job.value is BackupJobState.Running

    /**
     * Reads the counts, the folder, the safety backups and the last restore again. The screen calls it each
     * time it opens: this view model outlives the screen, and any of these can change while it is closed.
     */
    fun refresh() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _counts.value = inspector.currentCounts()
                _safetyBackups.value = coordinator.safetyBackups().map {
                    SafetyBackup(it, DataBackupFormatting.safetyBackupTime(it), it.length())
                }
                _lastRestore.value = installer.lastResult()
                _autoStatus.value = readAutoStatus()
            }
        }
    }

    /**
     * Starts a backup to [destination], just picked, in the background: the user may leave the screen. The file's
     * grant is kept for the job, which may outlive this screen, and given back once it is written.
     */
    fun createBackup(destination: Uri) {
        if (backupRunning) return
        try {
            context.contentResolver.takePersistableUriPermission(
                destination, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // The provider offers no lasting grant: the job, in this process, still has the picker's.
        }
        BackupJob.start(context, destination)
    }

    /** Blocks on files, the folder's provider and WorkManager: call it off the main thread. */
    private fun readAutoStatus(): AutoBackupStatus {
        val state = autoBackup.store.read()
        val folder = state.folderUri?.let { uri ->
            val picked = PickedFolder(context.contentResolver, Uri.parse(uri))
            val available = picked.isAvailable()
            AutoBackupFolder(chosen = true, name = if (available) picked.displayName else null, available = available)
        } ?: AutoBackupFolder(chosen = false, name = null, available = false)
        return AutoBackupStatus(
            folder = folder,
            state = state,
            nextRunAt = if (state.enabled) runCatching { scheduler.nextRunAt() }.getOrNull() else null,
            notificationsAllowed = notificationsAllowed(),
            overdue = AutoBackupScheduler.isOverdue(state, Instant.now()),
        )
    }

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Turns daily backups on or off. On needs a folder that can be used; the switch is only offered then. */
    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            scheduler.setEnabled(enabled)
            _autoStatus.value = readAutoStatus()
        }
    }

    /**
     * Makes [treeUri], just picked, the folder for automatic backups. Its permission is kept across restarts;
     * the previous folder's is given back, so the app does not keep access to folders it no longer uses.
     */
    fun chooseAutoBackupFolder(treeUri: Uri) = run("Enregistrement du dossier…") {
        val resolver = context.contentResolver
        resolver.takePersistableUriPermission(treeUri, PickedFolder.PERMISSION_FLAGS)
        val previous = autoBackup.store.read().folderUri
        autoBackup.store.update { it.copy(folderUri = treeUri.toString()) }
        if (previous != null && previous != treeUri.toString()) {
            try {
                resolver.releasePersistableUriPermission(Uri.parse(previous), PickedFolder.PERMISSION_FLAGS)
            } catch (e: SecurityException) {
                // Already gone: nothing to give back.
            }
        }
        _autoStatus.value = readAutoStatus()
        DataBackupState.Idle
    }

    /** Runs an automatic backup now, whether or not the data changed since the last one. */
    fun backupNow() {
        if (!backupRunning) BackupJob.start(context, destination = null)
    }

    fun inspect(uri: Uri, isSafetyBackup: Boolean = false) = run("Vérification du fichier…") {
        if (backupRunning) return@run DataBackupState.Failed(WAIT_FOR_BACKUP)
        when (val inspection = inspector.inspect(uri)) {
            is BackupInspection.Restorable -> DataBackupState.Previewing(inspection.preview, isSafetyBackup)
            is BackupInspection.NotRestorable -> DataBackupState.Failed(BackupMessages.of(inspection.problem))
        }
    }

    fun inspectSafetyBackup(backup: SafetyBackup) = inspect(Uri.fromFile(backup.file), isSafetyBackup = true)

    /**
     * Unpacks and checks [preview]'s backup, then restores it — unless restoring it changes the stock rule,
     * or brings another business's data than the license's, in which case the screen asks first ([DataBackupState.ConfirmingRestore]).
     */
    fun restore(preview: BackupPreview) = run("Vérification de la sauvegarde…") {
        val current = db.businessSettingsDao().allowNegativeStock() ?: true
        when (val preparation = coordinator.prepare(preview.uri, preview.manifest, current, ::showStep)) {
            is RestorePreparation.NotRestorable -> DataBackupState.Failed(BackupMessages.of(preparation.problem))
            is RestorePreparation.Ready -> {
                val licenseBusiness = license.state.value.heldLicense?.businessId
                val other = preparation.business?.takeIf { it.isOther(licenseBusiness) }
                if (preparation.stockRule.worthTelling || other != null) {
                    DataBackupState.ConfirmingRestore(preparation.restore, preview, preparation.stockRule, other)
                } else {
                    commit(preparation.restore, preview)
                }
            }
        }
    }

    /** The user read what the restore brings (another business's data, a stock rule), and goes ahead. */
    fun confirmRestore(confirming: DataBackupState.ConfirmingRestore) {
        if (_state.value != confirming) return
        if (backupRunning) {
            _state.value = DataBackupState.Failed(WAIT_FOR_BACKUP)
            viewModelScope.launch(Dispatchers.IO) { coordinator.discard(confirming.restore) }
            return
        }
        _state.value = DataBackupState.Idle
        run("Copie de sécurité de vos données actuelles…") { commit(confirming.restore, confirming.preview) }
    }

    /** The user turned the restore down at the stock rule: the unpacked backup goes, and nothing changed. */
    fun cancelRestore() {
        val confirming = _state.value as? DataBackupState.ConfirmingRestore ?: return
        _state.value = DataBackupState.Idle
        viewModelScope.launch(Dispatchers.IO) { coordinator.discard(confirming.restore) }
        refresh()
    }

    private fun showStep(step: RestoreCoordinator.Step) {
        _state.value = DataBackupState.Working(
            when (step) {
                RestoreCoordinator.Step.CHECKING_BACKUP -> "Vérification de la sauvegarde…"
                RestoreCoordinator.Step.SAVING_CURRENT_DATA -> "Copie de sécurité de vos données actuelles…"
                RestoreCoordinator.Step.SCHEDULING -> "Préparation du redémarrage…"
            }
        )
    }

    private fun commit(restore: PreparedRestore, preview: BackupPreview): DataBackupState {
        val outcome = coordinator.commit(restore, preview.fileName, preview.fileSize, ::showStep)
        return when (outcome) {
            is RestoreOutcome.Scheduled -> DataBackupState.RestartNeeded
            is RestoreOutcome.NotRestorable -> DataBackupState.Failed(BackupMessages.of(outcome.problem))
            is RestoreOutcome.SafetyBackupFailed -> DataBackupState.Failed(BackupMessages.safetyBackupFailed(outcome.reason))
        }
    }

    /** Back to the screen after a result, an error or a preview the user did not confirm. */
    fun dismiss() {
        if (_state.value is DataBackupState.ConfirmingRestore) return cancelRestore()
        if (_state.value !is DataBackupState.Working && _state.value != DataBackupState.RestartNeeded) {
            _state.value = DataBackupState.Idle
            refresh()
        }
    }

    fun dismissLastRestore() {
        _lastRestore.value = null
        viewModelScope.launch(Dispatchers.IO) { installer.clearResult() }
    }

    private companion object {
        const val WAIT_FOR_BACKUP = "Une sauvegarde est en cours : attendez qu'elle se termine pour restaurer."
    }

    /** Runs [block] off the main thread, one operation at a time, showing [label] until it reports a step. */
    private fun run(label: String, block: suspend () -> DataBackupState) {
        if (_state.value is DataBackupState.Working || _state.value == DataBackupState.RestartNeeded) return
        _state.value = DataBackupState.Working(label)
        viewModelScope.launch {
            _state.value = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                android.util.Log.e("DataBackupViewModel", "operation failed", e)
                DataBackupState.Failed("Une erreur inattendue est survenue. Vos données n'ont pas changé.")
            }
        }
    }
}
